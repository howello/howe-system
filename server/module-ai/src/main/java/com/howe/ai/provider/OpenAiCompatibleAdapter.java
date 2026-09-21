package com.howe.ai.provider;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiTextChunk;
import com.howe.ai.api.dto.AiUsage;
import com.howe.ai.capability.AiErrorClassifier;
import com.howe.ai.config.AiProperties;
import com.howe.ai.config.AiSecretCipher;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议适配器
 *
 * <p>覆盖任何兼容 OpenAI 协议的端点：DeepSeek、月之暗面、智谱、火山方舟、自建 vLLM 等。
 * {@code base_url} 必须可由渠道覆盖，否则这个适配器只能连 OpenAI 一家，
 * 这正是「多个 API 可配置」的落点。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class OpenAiCompatibleAdapter extends AbstractAiProviderAdapter
{
    /** 协议标识 */
    public static final String PROTOCOL = "openai";

    /** 未配置接入点时的兜底地址 */
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";

    /** 流式对话路径 */
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    /** 上游 SSE 的结束标记 */
    private static final String STREAM_DONE = "[DONE]";

    /** 原样打进日志的上游事件个数，用于确认厂商的字段名 */
    private static final int RAW_SAMPLE_SIZE = 3;

    public OpenAiCompatibleAdapter(AiSecretCipher cipher, AiProviderMapper providerMapper)
    {
        super(cipher, providerMapper);
    }

    @Override
    public String protocol()
    {
        return PROTOCOL;
    }

    @Override
    public ChatModel createChatModel(AiChannel channel, AiModel model)
    {
        return OpenAiChatModel.builder()
                .openAiApi(chatApi(channel))
                .defaultOptions(OpenAiChatOptions.builder().model(model.getModelName()).build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(noRetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Override
    public ImageModel createImageModel(AiChannel channel, AiModel model)
    {
        OpenAiImageApi api = OpenAiImageApi.builder()
                .baseUrl(resolveBaseUrl(channel))
                .apiKey(apiKey(channel))
                .build();
        return new OpenAiImageModel(api,
                OpenAiImageOptions.builder().model(model.getModelName()).build(),
                noRetryTemplate(), ObservationRegistry.NOOP);
    }

    @Override
    public EmbeddingModel createEmbeddingModel(AiChannel channel, AiModel model)
    {
        return new OpenAiEmbeddingModel(chatApi(channel), MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model.getModelName()).build(),
                noRetryTemplate(), ObservationRegistry.NOOP);
    }

    @Override
    public EmbeddingOptions embeddingOptions(AiModel model, Integer dimensions)
    {
        OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder().model(model.getModelName());
        if (dimensions != null)
        {
            builder.dimensions(dimensions);
        }
        return builder.build();
    }

    @Override
    public ChatOptions chatOptions(AiModel model, ChatSpec spec)
    {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(model.getModelName());
        if (spec.temperature() != null)
        {
            builder.temperature(spec.temperature());
        }
        if (spec.maxTokens() != null)
        {
            builder.maxTokens(spec.maxTokens());
        }
        if (spec.topP() != null)
        {
            builder.topP(spec.topP());
        }
        // 模型上配的透传参数（如 DeepSeek 的 thinking / reasoning_effort）原样并入请求体
        Map<String, Object> extraBody = extraBody(model);
        if (!extraBody.isEmpty())
        {
            builder.extraBody(extraBody);
        }
        return builder.build();
    }

    @Override
    public ImageOptions imageOptions(AiModel model, ImageSpec spec)
    {
        OpenAiImageOptions.Builder builder = OpenAiImageOptions.builder().model(model.getModelName());
        if (spec.count() != null)
        {
            builder.N(spec.count());
        }
        // OpenAI 协议用宽高分开传，这里把统一的 1024*1024 拆开
        builder.width(spec.width(1024));
        builder.height(spec.height(1024));
        return builder.build();
    }

    @Override
    public List<String> listModels(AiChannel channel)
    {
        return fetchModelIds(modelsUrl(resolveBaseUrl(channel)), "查询 OpenAI 兼容端点模型列表", channel);
    }

    @Override
    public Flux<AiTextChunk> streamChat(AiChannel channel, AiModel model, ChatSpec spec, List<Message> messages)
    {
        boolean reasoning = reasoningStreamEnabled(model);
        log.debug("模型 {} 的 reasoning 开关={}，库里的 extra={}", model.getModelName(), reasoning, model.getExtra());
        if (!reasoning)
        {
            // 没开就完全交给默认实现，行为与之前一模一样。自解析这条路径只为了拿到
            // reasoning_content，没开就没必要换实现，也就不引入新的失败面。
            return super.streamChat(channel, model, spec, messages);
        }
        return rawStream(channel, model, spec, messages);
    }

    /**
     * 自己发请求、自己读上游 SSE，把思考增量一并推出来
     *
     * <p><b>HTTP 客户端必须用 {@link HttpURLConnection}，不能用 hutool 的
     * {@code HttpRequest.execute()}。</b>hutool 的 {@code execute()} 会把响应体读干净
     * 才返回——实测一个 5 秒的流式响应，它在第 3891ms 才返回，之后 794 行瞬间读完，
     * 逐字效果完全消失。同样条件下 {@code HttpURLConnection} 第 1 行 692ms 就到、
     * 第 600 行 2995ms，是真正的边收边读。</p>
     *
     * <p>也不用 WebClient：本模块的依赖里既没有 reactor-netty 也没有 jetty，
     * 它会退到别的连接器，而连接器是否逐段吐出响应体不受我们控制。</p>
     *
     * @param channel  渠道
     * @param model    模型
     * @param spec     采样参数
     * @param messages 对话消息
     * @return 分片流，最后一个分片 done=true
     */
    private Flux<AiTextChunk> rawStream(AiChannel channel, AiModel model, ChatSpec spec, List<Message> messages)
    {
        String url = chatCompletionsUrl(resolveBaseUrl(channel));
        String key = apiKey(channel);
        String body = buildStreamBody(model, spec, messages).toJSONString();
        int timeout = (int) AiProperties.load().getTimeoutChatMs();
        String modelName = model.getModelName();
        log.debug("上游流式请求：url={}, body={}", url, brief(body));
        return Flux.<AiTextChunk>create(sink ->
        {
            long start = System.currentTimeMillis();
            StringBuilder buffer = new StringBuilder();
            AiUsage[] usage = new AiUsage[1];
            String[] finishReason = new String[1];
            int[] stats = new int[3];
            HttpURLConnection connection = null;
            try
            {
                connection = openStream(url, key, body, timeout);
                int status = connection.getResponseCode();
                log.debug("上游响应头：status={}, Content-Type={}, Transfer-Encoding={}, Content-Encoding={}, "
                                + "Content-Length={}, 拿到响应头耗时 {} ms",
                        status, connection.getHeaderField("Content-Type"),
                        connection.getHeaderField("Transfer-Encoding"),
                        connection.getHeaderField("Content-Encoding"),
                        connection.getHeaderField("Content-Length"),
                        System.currentTimeMillis() - start);
                if (status != HttpURLConnection.HTTP_OK)
                {
                    String errorBody = readErrorBody(connection);
                    AiErrorCode code = AiErrorClassifier.classifyStatus(
                            HttpStatusCode.valueOf(status), errorBody);
                    throw new AiException(code, "上游返回 " + status + "：" + brief(errorBody));
                }
                readUpstream(connection, sink, buffer, usage, finishReason, stats, start, modelName);
                if (!sink.isCancelled())
                {
                    sink.next(AiTextChunk.done(AiChatResult.builder()
                            .text(buffer.toString())
                            .model(modelName)
                            .usage(usage[0] == null ? AiUsage.builder().build() : usage[0])
                            .finishReason(finishReason[0])
                            .elapsedMs(System.currentTimeMillis() - start)
                            .build()));
                    sink.complete();
                }
            }
            catch (Exception e)
            {
                sink.error(e);
            }
            finally
            {
                if (connection != null)
                {
                    connection.disconnect();
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 建立流式请求连接
     *
     * <p>返回时请求体已发出、响应头已就绪，响应体还没有被读过。</p>
     */
    private HttpURLConnection openStream(String url, String key, String body, int timeout) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + key);
        // 和 Spring AI 一样显式声明要事件流，避免个别厂商按 Accept 决定是否流式
        connection.setRequestProperty("Accept", "text/event-stream");
        // 明确不要压缩：gzip 的压缩器会攒够数据才吐，流式响应一旦被压缩，
        // 客户端就得等整段压缩块才能解出内容，逐字效果直接消失
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(timeout);
        // 读超时按「两次读之间」计算，正好适合 SSE：模型思考期间没有数据也不会被掐断，
        // 但真的卡死超过 timeout 就会退出
        connection.setReadTimeout(timeout);
        try (OutputStream out = connection.getOutputStream())
        {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return connection;
    }

    /**
     * 读取错误响应体，用于错误分类与提示
     */
    private String readErrorBody(HttpURLConnection connection)
    {
        try (InputStream stream = connection.getErrorStream())
        {
            return stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /**
     * 读上游 SSE，逐行解析并推送分片
     */
    private void readUpstream(HttpURLConnection connection, FluxSink<AiTextChunk> sink, StringBuilder buffer,
            AiUsage[] usage, String[] finishReason, int[] stats, long start, String modelName) throws Exception
    {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while (!sink.isCancelled() && (line = reader.readLine()) != null)
            {
                if (stats[0] == 0)
                {
                    // 首个上游分片的时间点：它接近总耗时就说明上游没在逐段推
                    stats[0] = (int) (System.currentTimeMillis() - start);
                }
                String payload = readSseData(line);
                if (payload == null || STREAM_DONE.equals(payload))
                {
                    continue;
                }
                stats[1]++;
                if (stats[1] <= RAW_SAMPLE_SIZE)
                {
                    // 头几个事件原样打出来：厂商把思考放在哪个字段（是 reasoning_content
                    // 还是别的名字），只有看原始报文才能确认
                    log.debug("上游事件[{}]：{}", stats[1], brief(payload));
                }
                emit(sink, payload, buffer, usage, finishReason, stats);
            }
        }
        log.info("上游流式读取结束：model={}, 首行 {} ms, 事件 {} 个, 思考段 {} 个, 正文段 {} 个, 总耗时 {} ms",
                modelName, stats[0], stats[1], stats[2], stats[1] - stats[2], System.currentTimeMillis() - start);
    }

    /**
     * 解析一个上游事件并推出正文或思考增量
     */
    private void emit(FluxSink<AiTextChunk> sink, String payload, StringBuilder buffer,
            AiUsage[] usage, String[] finishReason, int[] stats)
    {
        JSONObject root;
        try
        {
            root = JSON.parseObject(payload);
        }
        catch (Exception e)
        {
            log.debug("跳过无法解析的流式分片：{}", brief(payload));
            return;
        }
        if (root == null)
        {
            return;
        }
        // 开了 stream_options.include_usage 之后，最后一个事件只带 usage、choices 为空
        JSONObject usageObject = root.getJSONObject("usage");
        if (usageObject != null)
        {
            usage[0] = readUsage(usageObject);
        }
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty())
        {
            return;
        }
        JSONObject choice = choices.getJSONObject(0);
        if (choice == null)
        {
            return;
        }
        String reason = choice.getString("finish_reason");
        if (StrUtil.isNotBlank(reason))
        {
            finishReason[0] = reason;
        }
        JSONObject delta = choice.getJSONObject("delta");
        if (delta == null)
        {
            return;
        }
        // 推理模型的思考增量走 reasoning_content，Spring AI 1.1.2 不认这个字段，
        // 所以只有自己解析才能把它带给前端
        String reasoning = delta.getString("reasoning_content");
        if (StrUtil.isNotEmpty(reasoning))
        {
            stats[2]++;
            sink.next(AiTextChunk.reasoning(reasoning));
        }
        String content = delta.getString("content");
        if (StrUtil.isNotEmpty(content))
        {
            buffer.append(content);
            sink.next(AiTextChunk.delta(content));
        }
    }

    /**
     * 取一行 SSE 里的数据部分
     *
     * @param line 一行原始文本
     * @return data 后面的内容，非 data 行返回 null
     */
    private String readSseData(String line)
    {
        return line.startsWith("data:") ? line.substring(5).trim() : null;
    }

    /**
     * 组装流式请求体
     *
     * <p>模型上配的透传参数先放、本模块的字段后放：同名字段以本模块为准，
     * 免得透传参数把 {@code stream} 关掉导致解析不出任何分片。</p>
     */
    private JSONObject buildStreamBody(AiModel model, ChatSpec spec, List<Message> messages)
    {
        JSONObject body = new JSONObject();
        body.putAll(extraBody(model));
        body.put("model", model.getModelName());
        body.put("stream", true);
        // 流式默认不返回用量，显式要一下，否则调用记录里没有 token 数
        body.put("stream_options", Map.of("include_usage", true));
        JSONArray array = new JSONArray();
        for (Message message : messages)
        {
            JSONObject item = new JSONObject();
            item.put("role", roleOf(message));
            item.put("content", message.getText());
            array.add(item);
        }
        body.put("messages", array);
        if (spec.temperature() != null)
        {
            body.put("temperature", spec.temperature());
        }
        if (spec.maxTokens() != null)
        {
            body.put("max_tokens", spec.maxTokens());
        }
        if (spec.topP() != null)
        {
            body.put("top_p", spec.topP());
        }
        return body;
    }

    /**
     * 取消息在 OpenAI 协议里的角色名
     */
    private String roleOf(Message message)
    {
        MessageType type = message.getMessageType();
        if (type == MessageType.SYSTEM)
        {
            return "system";
        }
        if (type == MessageType.ASSISTANT)
        {
            return "assistant";
        }
        return "user";
    }

    /**
     * 读取用量
     */
    private AiUsage readUsage(JSONObject usage)
    {
        return AiUsage.builder()
                .inputTokens(intValue(usage, "prompt_tokens"))
                .outputTokens(intValue(usage, "completion_tokens"))
                .totalTokens(intValue(usage, "total_tokens"))
                .build();
    }

    /**
     * 取整数值，缺失或类型不对时返回 null
     */
    private static Integer intValue(JSONObject object, String key)
    {
        Object value = object.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    /**
     * 拼出对话接口地址
     *
     * <p>接入点已带 {@code /v1} 时不重复拼接。</p>
     */
    private String chatCompletionsUrl(String baseUrl)
    {
        String url = StrUtil.removeSuffix(baseUrl, "/");
        return url.endsWith("/v1") ? url + "/chat/completions" : url + CHAT_COMPLETIONS_PATH;
    }

    @Override
    public AiHealthResult healthCheck(AiChannel channel, AiModel model)
    {
        if (model == null)
        {
            return AiHealthResult.fail("请先为该渠道配置至少一个启用的模型", AiErrorCode.BAD_REQUEST.name());
        }
        try
        {
            createChatModel(channel, model).call("ping");
            return AiHealthResult.ok("连通性正常，模型 " + model.getModelName() + " 可用");
        }
        catch (Exception e)
        {
            AiException translated = translate("OpenAI 兼容端点连通性检测", e);
            return AiHealthResult.fail(translated.getMessage(), translated.getAiErrorCode().name());
        }
    }

    /**
     * 构建 OpenAI 兼容 API 客户端
     *
     * @param channel 渠道
     * @return API 客户端
     */
    private OpenAiApi chatApi(AiChannel channel)
    {
        return OpenAiApi.builder()
                .baseUrl(resolveBaseUrl(channel))
                .apiKey(apiKey(channel))
                .build();
    }

    /**
     * 解析生效接入点，未配置时用 OpenAI 官方地址
     *
     * @param channel 渠道
     * @return 接入点
     */
    private String resolveBaseUrl(AiChannel channel)
    {
        String baseUrl = baseUrl(channel);
        return StrUtil.isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl;
    }

    /**
     * 拼出 models 接口地址
     *
     * <p>接入点有两种常见写法：站点根地址（{@code https://api.openai.com}）与已经带上
     * {@code /v1} 的地址（{@code https://dashscope.aliyuncs.com/compatible-mode/v1}）。
     * 后者再拼一次 {@code /v1} 会变成 404，所以这里按结尾判断。</p>
     *
     * @param baseUrl 接入点
     * @return models 接口地址
     */
    private String modelsUrl(String baseUrl)
    {
        String url = StrUtil.removeSuffix(baseUrl, "/");
        return url.endsWith("/v1") ? url + "/models" : url + "/v1/models";
    }
}
