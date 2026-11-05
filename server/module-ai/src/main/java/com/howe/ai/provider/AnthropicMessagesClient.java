package com.howe.ai.provider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Anthropic Messages API 客户端
 *
 * <p>用 hutool 直连官方 REST API，不引入任何厂商 SDK。之所以不复用 Spring AI 的
 * OpenAI 兼容实现：Anthropic 的 {@code /v1/messages} 与 OpenAI 的
 * {@code /v1/chat/completions} 在消息结构、图片块、流式事件上都不同，
 * 硬套兼容层会在视觉与流式上丢信息。</p>
 *
 * <p><b>采样参数约束</b>：当前 API 版本 {@code 2026-10-21} 已移除
 * {@code temperature} / {@code top_p} / {@code top_k}，请求体里出现任一字段都会
 * 返回 400。因此本客户端<b>刻意不发送</b>这三个字段，即使调用方传了也忽略。</p>
 *
 * @author howe
 */
public class AnthropicMessagesClient {

    /** Anthropic API 版本头，2026-10-21 起不再接受采样参数 */
    public static final String ANTHROPIC_VERSION = "2026-10-21";

    /** 默认接入点 */
    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

    /** 默认输出上限，模型未配置 max_tokens 时使用 */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    /** 错误信息里回显响应体的最大长度 */
    private static final int BRIEF_LENGTH = 300;

    /** 接入点 */
    private final String baseUrl;

    /** 明文密钥 */
    private final String apiKey;

    /**
     * 构造客户端
     *
     * @param baseUrl 接入点，可空（用官方默认值）
     * @param apiKey  明文密钥
     */
    public AnthropicMessagesClient(String baseUrl, String apiKey) {
        this.baseUrl = StrUtil.isBlank(baseUrl) ? DEFAULT_BASE_URL : StrUtil.removeSuffix(baseUrl.trim(), "/");
        this.apiKey = apiKey;
    }

    /**
     * 同步发起一次 Messages 请求
     *
     * @param body    已组装好的请求体（不含采样参数）
     * @param timeout 超时毫秒数
     * @return 解析后的结果
     */
    public AnthropicMessage call(JSONObject body, long timeout) {
        try (HttpResponse response = HttpRequest.post(messagesUrl())
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .body(body.toJSONString())
                .timeout((int) Math.min(timeout, Integer.MAX_VALUE))
                .execute()) {
            String text = response.body();
            if (!response.isOk()) {
                throw toException(response.getStatus(), text);
            }
            return parseMessage(text);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "调用 Anthropic 失败：" + e.getMessage());
        }
    }

    /**
     * 流式发起 Messages 请求
     *
     * <p>SSE 解析放在调用方线程上逐个事件回调；任何 {@code error} 事件即使
     * HTTP 为 200 也会抛异常，避免被当成成功。</p>
     *
     * @param body      请求体
     * @param timeout   超时毫秒数
     * @param onText    正文增量回调
     * @param onUsage   用量回调，可能为空
     * @return 结束原因
     */
    public String stream(JSONObject body, long timeout, Consumer<String> onText, Consumer<AnthropicUsage> onUsage) {
        body.put("stream", true);
        try (HttpResponse response = HttpRequest.post(messagesUrl())
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .body(body.toJSONString())
                .timeout((int) Math.min(timeout, Integer.MAX_VALUE))
                .execute()) {
            if (!response.isOk()) {
                throw toException(response.getStatus(), response.body());
            }
            return consumeSse(response.bodyStream(), onText, onUsage);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "Anthropic 流式调用失败：" + e.getMessage());
        }
    }

    /**
     * 查询厂商侧模型列表
     *
     * @param timeout 超时毫秒数
     * @return 模型名集合，按名称升序
     */
    public List<String> listModels(long timeout) {
        List<String> models = new ArrayList<>();
        String afterId = null;
        try {
            // 分页拉取，避免只拿到第一页
            for (int page = 0; page < 20; page++) {
                String url = baseUrl + "/v1/models?limit=100" + (afterId == null ? "" : "&after_id=" + afterId);
                try (HttpResponse response = HttpRequest.get(url)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .timeout((int) Math.min(timeout, Integer.MAX_VALUE))
                        .execute()) {
                    if (!response.isOk()) {
                        throw toException(response.getStatus(), response.body());
                    }
                    JSONObject json = JSON.parseObject(response.body());
                    JSONArray data = json.getJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.size(); i++) {
                            String id = data.getJSONObject(i).getString("id");
                            if (StrUtil.isNotBlank(id)) {
                                models.add(id);
                            }
                        }
                    }
                    if (!json.getBooleanValue("has_more")) {
                        break;
                    }
                    afterId = json.getString("last_id");
                    if (StrUtil.isBlank(afterId)) {
                        break;
                    }
                }
            }
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "获取 Anthropic 模型列表失败：" + e.getMessage());
        }
        models.sort(String::compareTo);
        return models;
    }

    /**
     * 组装 Messages 请求体
     *
     * @param model       模型名
     * @param system      系统提示词，可空
     * @param messages    消息列表
     * @param maxTokens   输出上限，可空用默认值
     * @return 请求体
     */
    public static JSONObject buildBody(String model, String system, List<JSONObject> messages, Integer maxTokens) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", maxTokens == null || maxTokens <= 0 ? DEFAULT_MAX_TOKENS : maxTokens);
        if (StrUtil.isNotBlank(system)) {
            body.put("system", system);
        }
        body.put("messages", messages);
        // 刻意不写 temperature / top_p / top_k：2026-10-21 起发送即 400
        return body;
    }

    /**
     * 把一段文本组装成 user 消息
     *
     * @param text 文本
     * @return 消息对象
     */
    public static JSONObject userText(String text) {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        JSONArray content = new JSONArray();
        content.add(textBlock(text));
        message.put("content", content);
        return message;
    }

    /**
     * 把文本与图片组装成一条 user 消息
     *
     * @param text   文本
     * @param images 图片列表，元素为 {@code url} 或 {@code base64} + {@code mediaType}
     * @return 消息对象
     */
    public static JSONObject userWithImages(String text, List<Map<String, String>> images) {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        JSONArray content = new JSONArray();
        if (images != null) {
            for (Map<String, String> image : images) {
                content.add(imageBlock(image));
            }
        }
        content.add(textBlock(text));
        message.put("content", content);
        return message;
    }

    /**
     * 把 assistant 文本组装成消息
     *
     * @param text 文本
     * @return 消息对象
     */
    public static JSONObject assistantText(String text) {
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        JSONArray content = new JSONArray();
        content.add(textBlock(text));
        message.put("content", content);
        return message;
    }

    /**
     * 构造文本块
     *
     * @param text 文本
     * @return 内容块
     */
    private static JSONObject textBlock(String text) {
        JSONObject block = new JSONObject();
        block.put("type", "text");
        block.put("text", text == null ? "" : text);
        return block;
    }

    /**
     * 构造图片块
     *
     * @param image 图片来源
     * @return 内容块
     */
    private static JSONObject imageBlock(Map<String, String> image) {
        JSONObject block = new JSONObject();
        block.put("type", "image");
        JSONObject source = new JSONObject();
        if (StrUtil.isNotBlank(image.get("base64"))) {
            source.put("type", "base64");
            source.put("media_type", StrUtil.blankToDefault(image.get("mediaType"), "image/png"));
            source.put("data", image.get("base64"));
        } else {
            source.put("type", "url");
            source.put("url", image.get("url"));
        }
        block.put("source", source);
        return block;
    }

    /**
     * 解析非流式响应
     *
     * @param text 响应体
     * @return 解析结果
     */
    private AnthropicMessage parseMessage(String text) {
        JSONObject json = JSON.parseObject(text);
        if (json == null) {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "Anthropic 返回了空响应");
        }
        StringBuilder content = new StringBuilder();
        JSONArray blocks = json.getJSONArray("content");
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                if ("text".equals(block.getString("type"))) {
                    content.append(StrUtil.nullToEmpty(block.getString("text")));
                }
            }
        }
        String stopReason = json.getString("stop_reason");
        if ("refusal".equals(stopReason)) {
            throw new AiException(AiErrorCode.CONTENT_BLOCKED, "Anthropic 拒绝了本次请求");
        }
        return new AnthropicMessage(content.toString(), usageOf(json.getJSONObject("usage")), stopReason);
    }

    /**
     * 解析 SSE 事件流
     *
     * @param stream  响应流
     * @param onText  正文增量回调
     * @param onUsage 用量回调
     * @return 结束原因
     */
    private String consumeSse(InputStream stream, Consumer<String> onText, Consumer<AnthropicUsage> onUsage) {
        StringBuilder event = new StringBuilder();
        StringBuilder data = new StringBuilder();
        String stopReason = null;
        boolean stopped = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // 空行表示一个事件结束
                    String result = handleEvent(event.toString(), data.toString(), onText, onUsage);
                    if (result != null) {
                        if ("__STOP__".equals(result)) {
                            stopped = true;
                        } else {
                            stopReason = result;
                        }
                    }
                    event.setLength(0);
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith("event:")) {
                    event.append(line.substring(6).trim());
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
                // 以 ':' 开头的是注释（心跳），忽略
            }
            // 流结束前可能没有尾随空行
            if (!data.isEmpty()) {
                String result = handleEvent(event.toString(), data.toString(), onText, onUsage);
                if ("__STOP__".equals(result)) {
                    stopped = true;
                } else if (result != null) {
                    stopReason = result;
                }
            }
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "解析 Anthropic 流式响应失败：" + e.getMessage());
        }
        if (!stopped) {
            // 没有 message_stop 就结束，说明连接提前断了
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "Anthropic 流式响应在结束事件前中断");
        }
        return stopReason;
    }

    /**
     * 处理单个 SSE 事件
     *
     * @param eventName 事件名
     * @param payload   数据体
     * @param onText    正文回调
     * @param onUsage   用量回调
     * @return message_stop 返回哨兵，message_delta 返回结束原因，其它返回 null
     */
    private String handleEvent(String eventName, String payload, Consumer<String> onText,
            Consumer<AnthropicUsage> onUsage) {
        if (StrUtil.isBlank(payload)) {
            return null;
        }
        JSONObject json = JSON.parseObject(payload);
        if (json == null) {
            return null;
        }
        String type = StrUtil.blankToDefault(json.getString("type"), eventName);
        switch (type) {
            case "content_block_delta" -> {
                JSONObject delta = json.getJSONObject("delta");
                if (delta != null && "text_delta".equals(delta.getString("type"))) {
                    String text = delta.getString("text");
                    if (StrUtil.isNotEmpty(text)) {
                        onText.accept(text);
                    }
                }
                return null;
            }
            case "message_delta" -> {
                if (onUsage != null) {
                    AnthropicUsage usage = usageOf(json.getJSONObject("usage"));
                    if (usage != null) {
                        onUsage.accept(usage);
                    }
                }
                JSONObject delta = json.getJSONObject("delta");
                return delta == null ? null : delta.getString("stop_reason");
            }
            case "message_stop" -> {
                return "__STOP__";
            }
            case "error" -> {
                JSONObject error = json.getJSONObject("error");
                String message = error == null ? payload
                        : StrUtil.blankToDefault(error.getString("message"), payload);
                AiErrorCode code = classifyErrorType(error == null ? null : error.getString("type"));
                throw new AiException(code, "Anthropic 流式错误：" + message);
            }
            default -> {
                // message_start 里也带 usage，这里顺带回调一次
                if ("message_start".equals(type) && onUsage != null) {
                    JSONObject message = json.getJSONObject("message");
                    if (message != null) {
                        AnthropicUsage usage = usageOf(message.getJSONObject("usage"));
                        if (usage != null) {
                            onUsage.accept(usage);
                        }
                    }
                }
                return null;
            }
        }
    }

    /**
     * 解析用量
     *
     * @param usage 用量对象
     * @return 用量，缺失时返回 null
     */
    private AnthropicUsage usageOf(JSONObject usage) {
        if (usage == null) {
            return null;
        }
        return new AnthropicUsage(usage.getInteger("input_tokens"), usage.getInteger("output_tokens"));
    }

    /**
     * 拼 Messages 接口地址
     *
     * <p>渠道可能配了以 {@code /v1} 结尾的地址，这里避免拼成 {@code /v1/v1/messages}。</p>
     *
     * @return 完整地址
     */
    private String messagesUrl() {
        String base = StrUtil.removeSuffix(baseUrl, "/");
        if (base.endsWith("/v1")) {
            return base + "/messages";
        }
        return base + "/v1/messages";
    }

    /**
     * 把 HTTP 错误翻译成 AI 异常
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @return AI 异常
     */
    private AiException toException(int status, String body) {
        String brief = StrUtil.maxLength(StrUtil.nullToEmpty(body), BRIEF_LENGTH);
        AiErrorCode code;
        if (status == HttpStatus.HTTP_UNAUTHORIZED || status == HttpStatus.HTTP_FORBIDDEN) {
            code = AiErrorCode.AUTH_FAILED;
        } else if (status == HttpStatus.HTTP_TOO_MANY_REQUESTS) {
            code = AiErrorCode.RATE_LIMITED;
        } else if (status == 529) {
            code = AiErrorCode.PROVIDER_ERROR;
        } else if (status >= 500) {
            code = AiErrorCode.PROVIDER_ERROR;
        } else if (status == HttpStatus.HTTP_BAD_REQUEST) {
            code = AiErrorCode.BAD_REQUEST;
        } else {
            code = AiErrorCode.PROVIDER_ERROR;
        }
        return new AiException(code, "Anthropic 返回 " + status + "：" + brief);
    }

    /**
     * 按错误类型细分错误码
     *
     * @param type 错误类型
     * @return 错误码
     */
    private AiErrorCode classifyErrorType(String type) {
        if (type == null) {
            return AiErrorCode.PROVIDER_ERROR;
        }
        return switch (type) {
            case "authentication_error", "permission_error" -> AiErrorCode.AUTH_FAILED;
            case "rate_limit_error" -> AiErrorCode.RATE_LIMITED;
            case "invalid_request_error" -> AiErrorCode.BAD_REQUEST;
            case "overloaded_error", "api_error" -> AiErrorCode.PROVIDER_ERROR;
            default -> AiErrorCode.PROVIDER_ERROR;
        };
    }

    /**
     * Anthropic 返回的消息
     *
     * @param text       正文
     * @param usage      用量
     * @param stopReason 结束原因
     */
    public record AnthropicMessage(String text, AnthropicUsage usage, String stopReason) {
    }

    /**
     * Anthropic 用量
     *
     * @param inputTokens  输入 token
     * @param outputTokens 输出 token
     */
    public record AnthropicUsage(Integer inputTokens, Integer outputTokens) {

        /**
         * 合并两段用量，取非空值
         *
         * @param other 另一段用量
         * @return 合并结果
         */
        public AnthropicUsage merge(AnthropicUsage other) {
            if (other == null) {
                return this;
            }
            return new AnthropicUsage(
                    other.inputTokens != null ? other.inputTokens : inputTokens,
                    other.outputTokens != null ? other.outputTokens : outputTokens);
        }
    }
}
