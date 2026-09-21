package com.howe.ai.config;

import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.AiProvider;
import com.howe.ai.persistence.domain.AiSceneRoute;
import com.howe.ai.persistence.mapper.AiChannelMapper;
import com.howe.ai.persistence.mapper.AiModelMapper;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import com.howe.ai.persistence.mapper.AiSceneRouteMapper;
import com.howe.ai.provider.AiProviderAdapter;
import com.howe.ai.provider.ChatSpec;
import com.howe.ai.provider.ImageSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 配置缓存
 *
 * <p>缓存渠道、模型、场景路由的只读快照，以及按 {@code channelId + modelName} 构建好的
 * Spring AI 模型实例。后台改完配置调用 {@link #refresh()} 即可生效，不需要重启。</p>
 *
 * <p>快照整体替换、模型实例按代清空，读侧永远看到同一代的一致视图。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConfigCache
{
    private final AiProviderMapper aiProviderMapper;

    private final AiChannelMapper aiChannelMapper;

    private final AiModelMapper aiModelMapper;

    private final AiSceneRouteMapper aiSceneRouteMapper;

    /** 协议适配器，按 protocol 索引 */
    private final List<AiProviderAdapter> adapters;

    /** 已构建的模型实例，key 为「类型:渠道ID:模型名」 */
    private final Map<String, Object> modelInstances = new ConcurrentHashMap<>();

    /** 当前快照 */
    private volatile Snapshot snapshot;

    /**
     * 重新加载配置快照，并清空已构建的模型实例
     */
    public void refresh()
    {
        Map<String, AiProvider> providers = aiProviderMapper.selectEnabledList().stream()
                .collect(Collectors.toMap(AiProvider::getCode, Function.identity(), (a, b) -> a));

        List<AiChannel> channels = aiChannelMapper.selectEnabledList();
        Map<Long, AiChannel> channelMap = channels.stream()
                .collect(Collectors.toMap(AiChannel::getId, Function.identity(), (a, b) -> a));

        List<AiModel> models = aiModelMapper.selectEnabledList();
        Map<Long, AiModel> modelMap = models.stream()
                .collect(Collectors.toMap(AiModel::getId, Function.identity(), (a, b) -> a));

        Map<String, AiSceneRoute> routes = new HashMap<>();
        for (AiSceneRoute route : aiSceneRouteMapper.selectEnabledList())
        {
            routes.put(sceneKey(route.getScene(), route.getCapability()), route);
        }

        AiProperties properties = AiProperties.load();
        this.snapshot = new Snapshot(providers, channelMap, modelMap, routes);
        this.modelInstances.clear();
        log.info("AI 配置缓存已刷新：服务商 {} 个，渠道 {} 个，模型 {} 个，场景路由 {} 条，总开关 {}",
                providers.size(), channelMap.size(), modelMap.size(), routes.size(),
                properties.isEnabled() ? "开启" : "关闭");
        // 逐个打出模型与它的 extra：库里的值就是这里的值，
        // 「配置写了却不生效」时靠这行判断是没存进去还是解析没生效
        for (AiModel model : models)
        {
            log.debug("缓存模型：id={}, modelName={}, capability={}, enabled={}, extra={}",
                    model.getId(), model.getModelName(), model.getCapability(), model.getEnabled(),
                    model.getExtra());
        }
    }

    /**
     * 获取当前参数快照
     *
     * <p>每次都从参数配置表重新读取：{@code ai.enabled}、全局默认模型 ID 这类值在
     * 「参数设置」里改完就应立即生效，不需要等数据表快照刷新。底层走 Redis 缓存，代价很低。</p>
     *
     * @return 参数快照
     */
    public AiProperties getProperties()
    {
        return AiProperties.load();
    }

    /**
     * 按标识获取服务商
     *
     * @param code 服务商标识
     * @return 服务商，不存在时返回 null
     */
    public AiProvider getProvider(String code)
    {
        return current().providers.get(code);
    }

    /**
     * 按主键获取渠道
     *
     * @param id 渠道ID
     * @return 渠道，不存在或已停用时返回 null
     */
    public AiChannel getChannel(Long id)
    {
        return id == null ? null : current().channels.get(id);
    }

    /**
     * 按主键获取模型
     *
     * @param id 模型ID
     * @return 模型，不存在或已停用时返回 null
     */
    public AiModel getModel(Long id)
    {
        return id == null ? null : current().models.get(id);
    }

    /**
     * 按场景与能力获取路由
     *
     * @param scene      场景标识
     * @param capability 能力类型
     * @return 场景路由，不存在时返回 null
     */
    public AiSceneRoute getRoute(String scene, String capability)
    {
        return current().routes.get(sceneKey(scene, capability));
    }

    /**
     * 按渠道与模型名查找启用的模型
     *
     * <p>请求里显式指定 {@code model} 时用名字定位，因为业务模块只认识厂商的模型名，
     * 不该依赖本模块的数据库主键。</p>
     *
     * @param channelId 渠道ID，为空时在全部渠道中查找
     * @param modelName 模型名
     * @return 模型，找不到时返回 null
     */
    public AiModel findModel(Long channelId, String modelName)
    {
        if (modelName == null)
        {
            return null;
        }
        for (AiModel model : current().models.values())
        {
            if (modelName.equals(model.getModelName())
                    && (channelId == null || channelId.equals(model.getChannelId())))
            {
                return model;
            }
        }
        return null;
    }

    /**
     * 在指定渠道下按能力取第一个启用的模型
     *
     * <p>后台「测试连通性」只指定渠道时用它挑一个模型发探测请求。</p>
     *
     * @param channelId  渠道ID
     * @param capability 能力类型
     * @return 模型，找不到时返回 null
     */
    public AiModel findFirstModel(Long channelId, String capability)
    {
        for (AiModel model : current().models.values())
        {
            if (channelId.equals(model.getChannelId())
                    && (capability == null || capability.equalsIgnoreCase(model.getCapability())))
            {
                return model;
            }
        }
        return null;
    }

    /**
     * 获取协议适配器
     *
     * @param protocol 协议标识
     * @return 适配器
     * @throws AiException 协议没有对应实现
     */
    public AiProviderAdapter getAdapter(String protocol)
    {
        for (AiProviderAdapter adapter : adapters)
        {
            if (adapter.protocol().equalsIgnoreCase(protocol))
            {
                return adapter;
            }
        }
        throw new AiException(AiErrorCode.PROVIDER_ERROR, "没有可用的协议适配器：" + protocol);
    }

    /**
     * 取或构建对话模型实例
     *
     * @param channel 渠道
     * @param model   模型
     * @return 对话模型
     */
    public ChatModel chatModel(AiChannel channel, AiModel model)
    {
        return instance("chat", channel, model, ChatModel.class,
                adapter -> adapter.createChatModel(channel, model));
    }

    /**
     * 取或构建文生图模型实例
     *
     * @param channel 渠道
     * @param model   模型
     * @return 文生图模型
     */
    public ImageModel imageModel(AiChannel channel, AiModel model)
    {
        return instance("image", channel, model, ImageModel.class,
                adapter -> adapter.createImageModel(channel, model));
    }

    /**
     * 取或构建向量模型实例
     *
     * @param channel 渠道
     * @param model   模型
     * @return 向量模型
     */
    public EmbeddingModel embeddingModel(AiChannel channel, AiModel model)
    {
        return instance("embedding", channel, model, EmbeddingModel.class,
                adapter -> adapter.createEmbeddingModel(channel, model));
    }

    /**
     * 取或构建模型实例的通用逻辑
     *
     * @param type      实例类型标识
     * @param channel   渠道
     * @param model     模型
     * @param modelType 期望的实例类型
     * @param builder   构建函数
     * @param <T>       实例类型
     * @return 模型实例
     */
    @SuppressWarnings("unchecked")
    private <T> T instance(String type, AiChannel channel, AiModel model, Class<T> modelType,
            Function<AiProviderAdapter, T> builder)
    {
        String key = type + ":" + channel.getId() + ":" + model.getModelName();
        Object cached = modelInstances.get(key);
        if (cached != null)
        {
            return (T) cached;
        }
        AiProvider provider = requireProvider(channel);
        T created = builder.apply(getAdapter(provider.getProtocol()));
        if (created == null || !modelType.isInstance(created))
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR,
                    "协议 " + provider.getProtocol() + " 不支持该能力：" + type);
        }
        modelInstances.put(key, created);
        return created;
    }

    /**
     * 取该渠道与模型对应的对话选项
     *
     * @param channel 渠道
     * @param model   模型
     * @param spec    采样参数
     * @return 协议对应的对话选项，协议不支持对话时返回 null
     */
    public ChatOptions chatOptions(AiChannel channel, AiModel model, ChatSpec spec)
    {
        AiProvider provider = requireProvider(channel);
        return getAdapter(provider.getProtocol()).chatOptions(model, spec);
    }

    /**
     * 取该渠道与模型对应的文生图选项
     *
     * @param channel 渠道
     * @param model   模型
     * @param spec    文生图参数
     * @return 协议对应的图片选项
     */
    public ImageOptions imageOptions(AiChannel channel, AiModel model, ImageSpec spec)
    {
        AiProvider provider = requireProvider(channel);
        return getAdapter(provider.getProtocol()).imageOptions(model, spec);
    }

    /**
     * 取该渠道与模型对应的向量选项
     *
     * @param channel    渠道
     * @param model      模型
     * @param dimensions 目标维度，可为空
     * @return 协议对应的向量选项，协议不支持时返回 null
     */
    public EmbeddingOptions embeddingOptions(AiChannel channel, AiModel model, Integer dimensions)
    {
        AiProvider provider = requireProvider(channel);
        return getAdapter(provider.getProtocol()).embeddingOptions(model, dimensions);
    }

    /**
     * 取渠道绑定的服务商
     *
     * @param channel 渠道
     * @return 服务商
     * @throws AiException 服务商不存在或已停用
     */
    public AiProvider requireProvider(AiChannel channel)
    {
        AiProvider provider = getProvider(channel.getProviderCode());
        if (provider == null)
        {
            throw new AiException(AiErrorCode.CHANNEL_DISABLED,
                    "渠道「" + channel.getName() + "」绑定的服务商已停用或不存在：" + channel.getProviderCode());
        }
        return provider;
    }

    /**
     * 获取当前快照，未初始化时先加载一次
     *
     * @return 当前快照
     */
    private Snapshot current()
    {
        Snapshot current = this.snapshot;
        if (current == null)
        {
            synchronized (this)
            {
                if (this.snapshot == null)
                {
                    refresh();
                }
                current = this.snapshot;
            }
        }
        return current;
    }

    /**
     * 场景与能力的组合键
     *
     * @param scene      场景标识
     * @param capability 能力类型
     * @return 组合键
     */
    private static String sceneKey(String scene, String capability)
    {
        return scene + "|" + capability;
    }

    /**
     * 配置快照
     *
     * @param providers 启用的服务商，按标识索引
     * @param channels  启用的渠道，按主键索引
     * @param models    启用的模型，按主键索引
     * @param routes    启用的场景路由，按「场景|能力」索引
     */
    private record Snapshot(Map<String, AiProvider> providers, Map<Long, AiChannel> channels,
            Map<Long, AiModel> models, Map<String, AiSceneRoute> routes)
    {
        private Snapshot
        {
            providers = Collections.unmodifiableMap(providers);
            channels = Collections.unmodifiableMap(channels);
            models = Collections.unmodifiableMap(models);
            routes = Collections.unmodifiableMap(routes);
        }
    }
}
