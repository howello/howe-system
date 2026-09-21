package com.howe.ai.provider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.capability.AiErrorClassifier;
import com.howe.ai.config.AiSecretCipher;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.AiProvider;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 适配器公共实现
 *
 * <p>集中处理三件每个协议都要做的事：取渠道密钥、解析生效的接入点、
 * 把厂商异常翻译成 {@link AiErrorCode}。</p>
 *
 * @author howe
 */
@Slf4j
public abstract class AbstractAiProviderAdapter implements AiProviderAdapter
{
    /** 查询模型列表的超时，比推理请求短：拉清单是交互动作，不该让后台等太久 */
    private static final int MODEL_LIST_TIMEOUT_MS = 15000;

    /** 错误信息里回显响应体的最大长度 */
    private static final int BRIEF_LENGTH = 200;

    /** 渠道密钥解密器 */
    protected final AiSecretCipher cipher;

    /** 服务商查询，用于取默认接入点 */
    protected final AiProviderMapper providerMapper;

    protected AbstractAiProviderAdapter(AiSecretCipher cipher, AiProviderMapper providerMapper)
    {
        this.cipher = cipher;
        this.providerMapper = providerMapper;
    }

    /**
     * 取渠道的明文密钥
     *
     * @param channel 渠道
     * @return 明文密钥
     */
    protected String apiKey(AiChannel channel)
    {
        String key = cipher.decrypt(channel.getApiKey());
        if (StrUtil.isBlank(key))
        {
            throw new AiException(AiErrorCode.AUTH_FAILED, "渠道「" + channel.getName() + "」未配置密钥");
        }
        return key;
    }

    /**
     * 解析生效的接入点：渠道配置优先，其次服务商默认值
     *
     * @param channel 渠道
     * @return 接入点，可能为 null（由各协议使用自身默认值）
     */
    protected String baseUrl(AiChannel channel)
    {
        if (StrUtil.isNotBlank(channel.getBaseUrl()))
        {
            return channel.getBaseUrl().trim();
        }
        AiProvider provider = providerMapper.selectAiProviderByCode(channel.getProviderCode());
        return provider == null || StrUtil.isBlank(provider.getDefaultBaseUrl())
                ? null : provider.getDefaultBaseUrl().trim();
    }

    /**
     * 构造不重试的模板
     *
     * <p>重试由 {@code AiFallbackPolicy} 统一控制，这里必须关掉 Spring AI 内部重试，
     * 否则一次调用会被放大成多次请求，用量与耗时都失真。</p>
     *
     * @return 只尝试一次的 RetryTemplate
     */
    protected RetryTemplate noRetryTemplate()
    {
        return RetryTemplate.builder().maxAttempts(1).build();
    }

    /**
     * 把厂商异常翻译成 AI 错误码
     *
     * @param action 动作描述，用于日志
     * @param e      原始异常
     * @return AI 异常
     */
    protected AiException translate(String action, Exception e)
    {
        return AiErrorClassifier.translate(action, e);
    }

    /**
     * 判定错误码
     *
     * @param e 原始异常
     * @return 错误码
     */
    protected AiErrorCode classify(Throwable e)
    {
        return AiErrorClassifier.classify(e);
    }

    /**
     * 取模型上配置的透传参数
     *
     * <p>{@code ai_model.extra} 里的 {@code extraBody} 对象会原样并入厂商请求体，
     * 用来透传协议特有的参数——比如 DeepSeek 的 {@code thinking} 与
     * {@code reasoning_effort}。放这里而不是塞进对外的 {@code AiChatRequest}，
     * 是为了不让协议细节污染业务模块看到的契约。</p>
     *
     * @param model 模型
     * @return 透传参数，未配置时返回空 Map
     */
    protected Map<String, Object> extraBody(AiModel model)
    {
        JSONObject body = extraOf(model).getJSONObject("extraBody");
        return body == null ? Map.of() : body;
    }

    /**
     * 判断模型是否开启了「自己解析流式、暴露思考内容」
     *
     * <p>Spring AI 1.1.2 的 OpenAI 模块不认 {@code reasoning_content}，该字段在它内部
     * 就被丢掉了。开启后由适配器直接解析上游 SSE，把思考增量一并推给前端。
     * 因为这条路径与 Spring AI 的实现相互独立，做成按模型开关，出问题可以随时关掉。</p>
     *
     * @param model 模型
     * @return 开启时返回 true
     */
    protected boolean reasoningStreamEnabled(AiModel model)
    {
        return extraOf(model).getBooleanValue("reasoning");
    }

    /**
     * 解析 {@code ai_model.extra}
     *
     * @param model 模型
     * @return 扩展配置对象，未配置或格式非法时返回空对象
     */
    protected JSONObject extraOf(AiModel model)
    {
        String extra = model == null ? null : model.getExtra();
        if (StrUtil.isBlank(extra))
        {
            return new JSONObject();
        }
        try
        {
            JSONObject parsed = JSON.parseObject(extra);
            return parsed == null ? new JSONObject() : parsed;
        }
        catch (Exception e)
        {
            log.warn("模型 {} 的扩展配置不是合法的 JSON，已忽略：{}", model.getModelName(), extra);
            return new JSONObject();
        }
    }

    /**
     * 调用 OpenAI 风格的 models 接口并解析出模型名
     *
     * <p>DashScope 的兼容模式与各家 OpenAI 兼容端点返回的都是
     * {@code {"data":[{"id":"..."},...]}}，所以两个适配器共用这段实现。</p>
     *
     * @param url     完整的 models 接口地址
     * @param action  动作描述，用于异常翻译与日志
     * @param channel 渠道
     * @return 模型名集合，按名称升序
     */
    protected List<String> fetchModelIds(String url, String action, AiChannel channel)
    {
        try (HttpResponse response = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey(channel))
                .timeout(MODEL_LIST_TIMEOUT_MS)
                .execute())
        {
            int status = response.getStatus();
            if (status == 401 || status == 403)
            {
                throw new AiException(AiErrorCode.AUTH_FAILED, "密钥无效或没有查询模型列表的权限");
            }
            if (!response.isOk())
            {
                throw new AiException(AiErrorCode.PROVIDER_ERROR,
                        "查询模型列表失败，HTTP " + status + "：" + brief(response.body()));
            }
            return parseModelIds(response.body());
        }
        catch (AiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw translate(action, e);
        }
    }

    /**
     * 解析 OpenAI 风格的模型列表响应
     *
     * @param body 响应体
     * @return 模型名集合，按名称升序
     */
    protected List<String> parseModelIds(String body)
    {
        JSONArray data;
        try
        {
            JSONObject root = JSON.parseObject(body);
            data = root == null ? null : root.getJSONArray("data");
        }
        catch (Exception e)
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "厂商返回的模型列表不是合法 JSON：" + brief(body));
        }
        if (data == null)
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "厂商返回的模型列表格式无法识别：" + brief(body));
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < data.size(); i++)
        {
            JSONObject item = data.getJSONObject(i);
            String id = item == null ? null : item.getString("id");
            if (StrUtil.isNotBlank(id) && !names.contains(id.trim()))
            {
                names.add(id.trim());
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    /**
     * 截断过长的响应体，避免把整页 HTML 塞进错误信息
     *
     * @param body 响应体
     * @return 截断后的文本
     */
    protected static String brief(String body)
    {
        if (body == null)
        {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() <= BRIEF_LENGTH ? trimmed : trimmed.substring(0, BRIEF_LENGTH) + "...";
    }
}
