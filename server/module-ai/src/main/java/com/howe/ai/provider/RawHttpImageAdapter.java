package com.howe.ai.provider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.config.AiProperties;
import com.howe.ai.config.AiSecretCipher;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.stereotype.Component;

/**
 * raw-http 协议适配器
 *
 * <p>面向 Spring AI 未覆盖的异步文生图接口：自己实现「提交 → 轮询 task_id」。
 * 只提供文生图能力，对话与向量交给另外两个协议。</p>
 *
 * @author howe
 */
@Component
public class RawHttpImageAdapter extends AbstractAiProviderAdapter
{
    /** 协议标识 */
    public static final String PROTOCOL = "raw-http";

    /** 连通性检测用的查询路径前缀，探测一个不存在的任务即可判断鉴权与网络 */
    private static final String TASK_PATH_PREFIX = "/api/v1/tasks/";

    public RawHttpImageAdapter(AiSecretCipher cipher, AiProviderMapper providerMapper)
    {
        super(cipher, providerMapper);
    }

    @Override
    public String protocol()
    {
        return PROTOCOL;
    }

    @Override
    public ImageModel createImageModel(AiChannel channel, AiModel model)
    {
        String baseUrl = baseUrl(channel);
        if (StrUtil.isBlank(baseUrl))
        {
            throw new AiException(AiErrorCode.BAD_REQUEST,
                    "渠道「" + channel.getName() + "」未配置接入点，raw-http 协议必须显式指定");
        }
        RawImageOptions defaults = new RawImageOptions(null, model.getModelName(), null, null, null, null, null);
        return new RawHttpImageModel(baseUrl, apiKey(channel), model.getModelName(), defaults,
                AiProperties.load().getTimeoutImageMs());
    }

    @Override
    public ImageOptions imageOptions(AiModel model, ImageSpec spec)
    {
        return new RawImageOptions(spec.count(), model.getModelName(), spec.width(1024), spec.height(1024),
                spec.size(), spec.negativePrompt(), spec.seed());
    }

    @Override
    public AiHealthResult healthCheck(AiChannel channel, AiModel model)
    {
        String baseUrl = baseUrl(channel);
        if (StrUtil.isBlank(baseUrl))
        {
            return AiHealthResult.fail("渠道未配置接入点，raw-http 协议必须显式指定", AiErrorCode.BAD_REQUEST.name());
        }
        try
        {
            // 查询一个不存在的任务：鉴权通过会返回 4xx 的“任务不存在”，鉴权失败返回 401
            try (HttpResponse response = HttpRequest.get(StrUtil.removeSuffix(baseUrl, "/")
                            + TASK_PATH_PREFIX + "connectivity-probe")
                    .header("Authorization", "Bearer " + apiKey(channel))
                    .timeout(15000)
                    .execute())
            {
                int status = response.getStatus();
                if (status == 401 || status == 403)
                {
                    return AiHealthResult.fail("密钥无效或没有访问权限", AiErrorCode.AUTH_FAILED.name());
                }
                if (status >= 500)
                {
                    return AiHealthResult.fail("服务端返回 " + status, AiErrorCode.PROVIDER_ERROR.name());
                }
                return AiHealthResult.ok("连通性正常（鉴权通过，响应码 " + status + "）");
            }
        }
        catch (Exception e)
        {
            AiException translated = translate("raw-http 连通性检测", e);
            return AiHealthResult.fail(translated.getMessage(), translated.getAiErrorCode().name());
        }
    }
}
