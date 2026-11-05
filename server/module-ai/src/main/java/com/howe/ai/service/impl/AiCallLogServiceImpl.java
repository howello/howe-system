package com.howe.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiImageAsset;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.AiProvider;
import com.howe.ai.persistence.domain.AiSceneRoute;
import com.howe.ai.persistence.domain.vo.AiCallLogDetailVO;
import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import com.howe.ai.persistence.mapper.AiCallLogMapper;
import com.howe.ai.persistence.mapper.AiChannelMapper;
import com.howe.ai.persistence.mapper.AiImageAssetMapper;
import com.howe.ai.persistence.mapper.AiModelMapper;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import com.howe.ai.persistence.mapper.AiSceneRouteMapper;
import com.howe.ai.service.IAiCallLogService;
import com.howe.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 调用记录 服务层实现
 *
 * <p>详情与统计都需要把裸 ID 换成可读名称。名称解析刻意<b>不走</b>
 * {@code AiConfigCache}（它只装启用中的配置）：渠道或模型停用、删除后，
 * 历史记录仍应显示当时的名称，或明确的「已删除」占位。</p>
 *
 * <p>统计采用「先按稳定键聚合，再批量查名称」两步，不直接联表——
 * 图片资产或多能力场景会让联表后的计数倍增。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogServiceImpl implements IAiCallLogService
{
    /** 允许的聚合维度 */
    private static final Set<String> GROUP_BY = Set.of("scene", "model", "day");

    private final AiCallLogMapper aiCallLogMapper;

    private final AiChannelMapper aiChannelMapper;

    private final AiModelMapper aiModelMapper;

    private final AiProviderMapper aiProviderMapper;

    private final AiSceneRouteMapper aiSceneRouteMapper;

    private final AiImageAssetMapper aiImageAssetMapper;

    @Override
    public List<AiCallLog> selectAiCallLogList(AiCallLog aiCallLog)
    {
        return aiCallLogMapper.selectAiCallLogList(aiCallLog);
    }

    /**
     * 查询调用记录详情
     *
     * <p>补齐场景名、服务商名、渠道名、模型名与生成图。名称缺失时给出
     * 「已删除渠道 #ID」「已删除模型 #ID」这类占位，而不是留空。</p>
     *
     * @param id 记录 ID
     * @return 详情
     */
    @Override
    public AiCallLogDetailVO selectDetail(Long id)
    {
        AiCallLog callLog = aiCallLogMapper.selectAiCallLogById(id);
        if (callLog == null)
        {
            throw new ServiceException("调用记录不存在");
        }
        AiCallLogDetailVO detail = new AiCallLogDetailVO();
        BeanUtils.copyProperties(callLog, detail);
        detail.setSceneName(resolveSceneName(callLog.getScene()));
        detail.setProviderName(resolveProviderName(callLog.getProviderCode()));
        detail.setChannelName(resolveChannelName(callLog.getChannelId()));
        detail.setModelName(resolveModelName(callLog.getModelId()));
        List<AiImageAsset> images = aiImageAssetMapper.selectByCallLogId(id);
        detail.setImages(images == null ? List.of() : images);
        return detail;
    }

    @Override
    public AiCallLog selectAiCallLogById(Long id)
    {
        AiCallLog callLog = aiCallLogMapper.selectAiCallLogById(id);
        if (callLog == null)
        {
            throw new ServiceException("调用记录不存在");
        }
        return callLog;
    }

    /**
     * 用量统计
     *
     * <p>聚合后按维度补齐展示名：场景用 {@code scene_name}，模型用模型名，
     * 按天直接用日期。</p>
     *
     * @param groupBy   聚合维度
     * @param beginTime 起始时间（含）
     * @param endTime   结束时间（不含）
     * @param scene     场景过滤
     * @return 统计结果
     */
    @Override
    public List<AiCallLogStats> selectStats(String groupBy, Date beginTime, Date endTime, String scene)
    {
        String dimension = StrUtil.isBlank(groupBy) ? "scene" : groupBy.trim().toLowerCase();
        if (!GROUP_BY.contains(dimension))
        {
            throw new ServiceException("不支持的聚合维度：" + groupBy + "，可选 scene / model / day");
        }
        List<AiCallLogStats> stats = aiCallLogMapper.selectStats(dimension, beginTime, endTime, scene);
        if (stats == null || stats.isEmpty())
        {
            return stats == null ? List.of() : stats;
        }
        fillGroupLabels(stats, dimension);
        return stats;
    }

    /**
     * 补齐统计结果的展示名
     *
     * @param stats     统计结果
     * @param dimension 聚合维度
     */
    private void fillGroupLabels(List<AiCallLogStats> stats, String dimension)
    {
        Map<String, String> sceneNames = "scene".equals(dimension) ? loadSceneNames() : Map.of();
        // 模型名按 ID 逐个查：记录量有限，且要能查到停用/已删除的模型
        Map<String, String> modelNames = new HashMap<>();
        if ("model".equals(dimension))
        {
            for (AiCallLogStats item : stats)
            {
                modelNames.put(item.getGroupKey(), resolveModelName(parseId(item.getGroupKey())));
            }
        }
        for (AiCallLogStats item : stats)
        {
            String key = item.getGroupKey();
            if ("scene".equals(dimension))
            {
                item.setGroupLabel(sceneNames.getOrDefault(key, key));
            }
            else if ("model".equals(dimension))
            {
                item.setGroupLabel(modelNames.getOrDefault(key, key));
            }
            else
            {
                item.setGroupLabel(key);
            }
        }
    }

    /**
     * 装载场景标识到名称的映射
     *
     * @return 映射
     */
    private Map<String, String> loadSceneNames()
    {
        Map<String, String> result = new HashMap<>();
        // 直接查表，不用启用中的缓存，才能覆盖已停用场景
        List<AiSceneRoute> routes = aiSceneRouteMapper.selectEnabledList();
        if (routes != null)
        {
            for (AiSceneRoute route : routes)
            {
                if (StrUtil.isNotBlank(route.getSceneName()))
                {
                    result.put(route.getScene(), route.getSceneName());
                }
            }
        }
        return result;
    }

    /**
     * 解析场景名称
     *
     * @param scene 场景标识
     * @return 名称，未配置时回退为标识
     */
    private String resolveSceneName(String scene)
    {
        if (StrUtil.isBlank(scene))
        {
            return null;
        }
        List<AiSceneRoute> routes = aiSceneRouteMapper.selectBySceneOnly(scene);
        if (routes != null && !routes.isEmpty() && StrUtil.isNotBlank(routes.get(0).getSceneName()))
        {
            return routes.get(0).getSceneName();
        }
        return scene;
    }

    /**
     * 解析服务商名称
     *
     * @param providerCode 服务商标识
     * @return 展示名，找不到时回退为标识
     */
    private String resolveProviderName(String providerCode)
    {
        if (StrUtil.isBlank(providerCode))
        {
            return null;
        }
        AiProvider provider = aiProviderMapper.selectAiProviderByCode(providerCode);
        return provider == null ? providerCode : provider.getName();
    }

    /**
     * 解析渠道名称
     *
     * @param channelId 渠道 ID
     * @return 渠道名；不存在时返回占位
     */
    private String resolveChannelName(Long channelId)
    {
        if (channelId == null)
        {
            return null;
        }
        AiChannel channel = aiChannelMapper.selectAiChannelById(channelId);
        return channel == null ? "已删除渠道 #" + channelId : channel.getName();
    }

    /**
     * 解析模型名称
     *
     * @param modelId 模型 ID
     * @return 模型名；不存在时返回占位
     */
    private String resolveModelName(Long modelId)
    {
        if (modelId == null)
        {
            return "未记录模型";
        }
        AiModel model = aiModelMapper.selectAiModelById(modelId);
        return model == null ? "已删除模型 #" + modelId : model.getModelName();
    }

    /**
     * 把字符串键解析为 Long
     *
     * @param value 字符串
     * @return 数字，解析失败返回 null
     */
    private Long parseId(String value)
    {
        try
        {
            return StrUtil.isBlank(value) ? null : Long.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
