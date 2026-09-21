package com.howe.ai.provider;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import cn.hutool.core.util.StrUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.config.AiSecretCipher;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DashScope 协议适配器
 *
 * <p>覆盖通义千问（chat / vision）、通义万相（image）与 text-embedding。</p>
 *
 * @author howe
 */
@Component
public class DashScopeAdapter extends AbstractAiProviderAdapter
{
    /** 协议标识 */
    public static final String PROTOCOL = "dashscope";

    /** 未配置接入点时的兜底地址 */
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

    /** 兼容模式下的模型列表路径 */
    private static final String COMPATIBLE_MODELS_PATH = "/compatible-mode/v1/models";

    public DashScopeAdapter(AiSecretCipher cipher, AiProviderMapper providerMapper)
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
        return DashScopeChatModel.builder()
                .dashScopeApi(chatApi(channel))
                .defaultOptions(DashScopeChatOptions.builder().model(model.getModelName()).build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(noRetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Override
    public ImageModel createImageModel(AiChannel channel, AiModel model)
    {
        return DashScopeImageModel.builder()
                .dashScopeApi(imageApi(channel))
                .defaultOptions(DashScopeImageOptions.builder().model(model.getModelName()).build())
                .retryTemplate(noRetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(AiChannel channel, AiModel model)
    {
        return new DashScopeEmbeddingModel(chatApi(channel), MetadataMode.EMBED,
                DashScopeEmbeddingOptions.builder().model(model.getModelName()).build(),
                noRetryTemplate(), ObservationRegistry.NOOP);
    }

    @Override
    public EmbeddingOptions embeddingOptions(AiModel model, Integer dimensions)
    {
        DashScopeEmbeddingOptions.Builder builder =
                DashScopeEmbeddingOptions.builder().model(model.getModelName());
        if (dimensions != null)
        {
            builder.dimensions(dimensions);
        }
        return builder.build();
    }

    @Override
    public ChatOptions chatOptions(AiModel model, ChatSpec spec)
    {
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder =
                DashScopeChatOptions.builder().model(model.getModelName());
        if (spec.temperature() != null)
        {
            builder.temperature(spec.temperature());
        }
        if (spec.maxTokens() != null)
        {
            builder.maxToken(spec.maxTokens());
        }
        if (spec.topP() != null)
        {
            builder.topP(spec.topP());
        }
        return builder.build();
    }

    @Override
    public ImageOptions imageOptions(AiModel model, ImageSpec spec)
    {
        DashScopeImageOptions.Builder builder = DashScopeImageOptions.builder().model(model.getModelName());
        if (spec.count() != null)
        {
            builder.n(spec.count());
        }
        if (StrUtil.isNotBlank(spec.size()))
        {
            // 通义万相直接收 size 字符串（1024*1024），与本模块对外的尺寸写法一致，原样透传。
            // 这里刻意用 withSize：SDK 的 size 与 width/height 是各自独立的字段，
            // 只有 size 会作为请求参数发给厂商，换成宽高会让尺寸设置失效。
            builder.withSize(spec.size().trim());
        }
        if (StrUtil.isNotBlank(spec.negativePrompt()))
        {
            builder.negativePrompt(spec.negativePrompt());
        }
        if (spec.seed() != null)
        {
            builder.seed(spec.seed().intValue());
        }
        return builder.build();
    }

    @Override
    public List<String> listModels(AiChannel channel)
    {
        return fetchModelIds(compatibleModelsUrl(channel), "查询 DashScope 模型列表", channel);
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
            // 发一个最小请求：只要鉴权与网络通，就说明渠道可用
            createChatModel(channel, model).call("ping");
            return AiHealthResult.ok("连通性正常，模型 " + model.getModelName() + " 可用");
        }
        catch (Exception e)
        {
            AiException translated = translate("DashScope 连通性检测", e);
            return AiHealthResult.fail(translated.getMessage(), translated.getAiErrorCode().name());
        }
    }

    /**
     * 构建 DashScope 通用 API 客户端
     *
     * @param channel 渠道
     * @return API 客户端
     */
    private DashScopeApi chatApi(AiChannel channel)
    {
        DashScopeApi.Builder builder = DashScopeApi.builder().apiKey(apiKey(channel));
        String baseUrl = baseUrl(channel);
        if (baseUrl != null)
        {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    /**
     * 构建 DashScope 文生图 API 客户端
     *
     * @param channel 渠道
     * @return 文生图 API 客户端
     */
    private DashScopeImageApi imageApi(AiChannel channel)
    {
        DashScopeImageApi.Builder builder = DashScopeImageApi.builder().apiKey(apiKey(channel));
        String baseUrl = baseUrl(channel);
        if (baseUrl != null)
        {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    /**
     * 拼出模型列表接口地址
     *
     * <p>DashScope 原生的 {@code /api/v1} 下没有 models 接口，模型清单只在 OpenAI 兼容模式下
     * 提供，路径固定挂在站点根上。渠道接入点若已经是兼容模式地址就直接用，否则取站点根再拼。</p>
     *
     * @param channel 渠道
     * @return models 接口地址
     */
    private String compatibleModelsUrl(AiChannel channel)
    {
        String baseUrl = baseUrl(channel);
        String url = StrUtil.isBlank(baseUrl) ? DEFAULT_BASE_URL : StrUtil.removeSuffix(baseUrl.trim(), "/");
        return url.contains("/compatible-mode") ? url + "/models" : url + COMPATIBLE_MODELS_PATH;
    }
}
