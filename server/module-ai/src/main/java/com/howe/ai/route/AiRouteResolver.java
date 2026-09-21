package com.howe.ai.route;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.capability.AiCapability;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.config.AiProperties;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.AiSceneRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景路由解析
 *
 * <p>解析顺序固定为四档：</p>
 * <ol>
 *   <li>请求显式指定渠道或模型 → 直接用（后台「测试」按钮走这条）</li>
 *   <li>请求场景命中 {@code ai_scene_route} → 用主模型，并带上降级链</li>
 *   <li>全局默认 {@code ai.default.&lt;capability&gt;.model}</li>
 *   <li>都没有 → 抛 {@code NO_AVAILABLE_MODEL}</li>
 * </ol>
 *
 * <p>降级链只在第 2 档命中后才生效。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRouteResolver
{
    private final AiConfigCache aiConfigCache;

    /**
     * 解析本次调用的候选目标
     *
     * @param scene           场景标识
     * @param capability      能力类型
     * @param explicitChannel 请求显式指定的渠道 ID，可为空
     * @param explicitModel   请求显式指定的模型名，可为空
     * @return 路由计划
     * @throws AiException 没有可用模型或指定目标不可用
     */
    public AiRoutePlan resolve(String scene, AiCapability capability, String explicitChannel, String explicitModel)
    {
        AiProperties properties = aiConfigCache.getProperties();
        if (!properties.isEnabled())
        {
            throw new AiException(AiErrorCode.AI_DISABLED);
        }
        String capabilityName = capability.name();

        AiRoutePlan explicit = resolveExplicit(scene, capabilityName, explicitChannel, explicitModel);
        if (explicit != null)
        {
            return explicit;
        }

        AiRoutePlan fromRoute = resolveSceneRoute(scene, capabilityName, properties.isFallbackEnabled());
        if (fromRoute != null)
        {
            return fromRoute;
        }

        AiRoutePlan fromDefault = resolveGlobalDefault(scene, capability, properties);
        if (fromDefault != null)
        {
            return fromDefault;
        }

        throw new AiException(AiErrorCode.NO_AVAILABLE_MODEL,
                StrUtil.isBlank(scene)
                        ? "未配置全局默认模型，且请求没有指定场景"
                        : "场景「" + scene + "」没有配置路由，也没有配置全局默认模型");
    }

    /**
     * 第一档：请求显式指定渠道或模型
     *
     * @param scene           场景标识
     * @param capabilityName  能力类型名
     * @param explicitChannel 显式渠道
     * @param explicitModel   显式模型名
     * @return 路由计划；未显式指定时返回 null
     */
    private AiRoutePlan resolveExplicit(String scene, String capabilityName, String explicitChannel,
            String explicitModel)
    {
        Long channelId = parseId(explicitChannel);
        if (channelId == null && StrUtil.isBlank(explicitModel))
        {
            return null;
        }

        AiModel model = StrUtil.isBlank(explicitModel) ? null : aiConfigCache.findModel(channelId, explicitModel.trim());
        if (model == null && channelId != null)
        {
            // 只给了渠道：在该渠道下挑一个能力匹配的模型
            model = aiConfigCache.findFirstModel(channelId, capabilityName);
        }
        if (model == null)
        {
            throw new AiException(AiErrorCode.NO_AVAILABLE_MODEL,
                    "指定的渠道或模型不可用：channel=" + explicitChannel + ", model=" + explicitModel);
        }
        AiChannel channel = aiConfigCache.getChannel(model.getChannelId());
        if (channel == null)
        {
            throw new AiException(AiErrorCode.CHANNEL_DISABLED,
                    "模型「" + model.getModelName() + "」所属渠道已停用或不存在");
        }
        return new AiRoutePlan(scene, capabilityName, List.of(new AiRouteTarget(channel, model)), new JSONObject());
    }

    /**
     * 第二档：场景路由，主模型 + 降级链
     *
     * @param scene           场景标识
     * @param capabilityName  能力类型名
     * @param fallbackEnabled 是否启用降级链
     * @return 路由计划；场景未配路由或没有任何可用模型时返回 null
     */
    private AiRoutePlan resolveSceneRoute(String scene, String capabilityName, boolean fallbackEnabled)
    {
        if (StrUtil.isBlank(scene))
        {
            return null;
        }
        AiSceneRoute route = aiConfigCache.getRoute(scene, capabilityName);
        if (route == null)
        {
            return null;
        }

        List<AiRouteTarget> targets = new ArrayList<>();
        appendTarget(targets, route.getPrimaryModelId(), "主模型");
        if (fallbackEnabled)
        {
            for (Long modelId : parseIds(route.getFallbackModelIds()))
            {
                appendTarget(targets, modelId, "降级模型");
            }
        }
        if (targets.isEmpty())
        {
            log.warn("场景 {} 的模型都不可用，回落到全局默认模型", scene);
            return null;
        }
        return new AiRoutePlan(scene, capabilityName, targets, parseParams(route.getRouteParams()));
    }

    /**
     * 第三档：全局默认模型
     *
     * @param scene      场景标识
     * @param capability 能力类型
     * @param properties 参数快照
     * @return 路由计划；未配置全局默认时返回 null
     */
    private AiRoutePlan resolveGlobalDefault(String scene, AiCapability capability, AiProperties properties)
    {
        Long modelId = switch (capability)
        {
            case CHAT -> properties.getDefaultChatModelId();
            case VISION -> properties.getDefaultVisionModelId();
            case IMAGE -> properties.getDefaultImageModelId();
            case EMBEDDING -> properties.getDefaultEmbeddingModelId();
        };
        if (modelId == null)
        {
            return null;
        }
        List<AiRouteTarget> targets = new ArrayList<>(1);
        appendTarget(targets, modelId, "全局默认模型");
        if (targets.isEmpty())
        {
            return null;
        }
        return new AiRoutePlan(scene, capability.name(), targets, new JSONObject());
    }

    /**
     * 追加一个候选目标，模型或渠道不可用时跳过
     *
     * @param targets 候选列表
     * @param modelId 模型 ID
     * @param role    角色描述，仅用于日志
     */
    private void appendTarget(List<AiRouteTarget> targets, Long modelId, String role)
    {
        if (modelId == null)
        {
            return;
        }
        AiModel model = aiConfigCache.getModel(modelId);
        if (model == null)
        {
            log.warn("{} {} 不存在或已停用，跳过", role, modelId);
            return;
        }
        AiChannel channel = aiConfigCache.getChannel(model.getChannelId());
        if (channel == null)
        {
            log.warn("{} {} 所属渠道已停用，跳过", role, modelId);
            return;
        }
        targets.add(new AiRouteTarget(channel, model));
    }

    /**
     * 解析场景参数覆盖
     *
     * @param params JSON 字符串
     * @return 参数对象，解析失败时返回空对象
     */
    private JSONObject parseParams(String params)
    {
        if (StrUtil.isBlank(params))
        {
            return new JSONObject();
        }
        try
        {
            JSONObject parsed = JSON.parseObject(params);
            return parsed == null ? new JSONObject() : parsed;
        }
        catch (Exception e)
        {
            log.warn("场景参数不是合法的 JSON，已忽略：{}", params);
            return new JSONObject();
        }
    }

    /**
     * 解析降级链的模型 ID 数组
     *
     * @param fallbackModelIds JSON 数组字符串
     * @return 模型 ID 列表
     */
    private List<Long> parseIds(String fallbackModelIds)
    {
        if (StrUtil.isBlank(fallbackModelIds))
        {
            return List.of();
        }
        try
        {
            JSONArray array = JSON.parseArray(fallbackModelIds);
            List<Long> ids = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++)
            {
                Long id = array.getLong(i);
                if (id != null)
                {
                    ids.add(id);
                }
            }
            return ids;
        }
        catch (Exception e)
        {
            log.warn("降级链不是合法的 JSON 数组，已忽略：{}", fallbackModelIds);
            return List.of();
        }
    }

    /**
     * 解析数字 ID
     *
     * @param value 字符串
     * @return ID，非法时返回 null
     */
    private Long parseId(String value)
    {
        if (StrUtil.isBlank(value))
        {
            return null;
        }
        try
        {
            return Long.valueOf(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
