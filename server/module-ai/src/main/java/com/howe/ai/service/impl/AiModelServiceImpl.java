package com.howe.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.howe.ai.capability.AiCapability;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.config.AiConfigService;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.AiProvider;
import com.howe.ai.persistence.domain.dto.AiModelImportRequest;
import com.howe.ai.persistence.domain.vo.AiRemoteModel;
import com.howe.ai.persistence.mapper.AiChannelMapper;
import com.howe.ai.persistence.mapper.AiModelMapper;
import com.howe.ai.persistence.mapper.AiSceneRouteMapper;
import com.howe.ai.provider.AiProviderAdapter;
import com.howe.ai.service.IAiModelService;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 模型 服务层实现
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements IAiModelService
{
    /** 启用 */
    private static final Integer ENABLED = 1;

    private final AiModelMapper aiModelMapper;

    private final AiChannelMapper aiChannelMapper;

    private final AiSceneRouteMapper aiSceneRouteMapper;

    private final AiConfigService aiConfigService;

    private final AiConfigCache aiConfigCache;

    @Override
    public List<AiModel> selectAiModelList(AiModel aiModel)
    {
        return aiModelMapper.selectAiModelList(aiModel);
    }

    @Override
    public AiModel selectAiModelById(Long id)
    {
        AiModel model = aiModelMapper.selectAiModelById(id);
        if (model == null)
        {
            throw new ServiceException("模型不存在");
        }
        return model;
    }

    @Override
    public int insertAiModel(AiModel aiModel)
    {
        requireChannel(aiModel.getChannelId());
        requireValidExtra(aiModel.getExtra());
        aiModel.setEnabled(aiModel.getEnabled() == null ? ENABLED : aiModel.getEnabled());
        aiModel.setCreateBy(SecurityUtils.getUsername());
        int rows = aiModelMapper.insertAiModel(aiModel);
        log.info("新增模型：id={}, modelName={}, extra={}", aiModel.getId(), aiModel.getModelName(),
                aiModel.getExtra());
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public int updateAiModel(AiModel aiModel)
    {
        requireChannel(aiModel.getChannelId());
        requireValidExtra(aiModel.getExtra());
        aiModel.setUpdateBy(SecurityUtils.getUsername());
        int rows = aiModelMapper.updateAiModel(aiModel);
        if (rows == 0)
        {
            throw new ServiceException("模型不存在");
        }
        log.info("修改模型：id={}, modelName={}, extra={}, 影响 {} 行", aiModel.getId(), aiModel.getModelName(),
                aiModel.getExtra(), rows);
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public int deleteAiModelByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long id : ids)
        {
            int referenced = aiSceneRouteMapper.countByModelId(id);
            if (referenced > 0)
            {
                AiModel model = aiModelMapper.selectAiModelById(id);
                throw new ServiceException("模型「" + (model == null ? id : model.getModelName())
                        + "」被 " + referenced + " 条场景路由引用，请先调整这些路由");
            }
        }
        int rows = aiModelMapper.deleteAiModelByIds(ids);
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public int updateStatus(Long id, Integer enabled)
    {
        AiModel model = new AiModel();
        model.setId(id);
        model.setEnabled(enabled);
        model.setUpdateBy(SecurityUtils.getUsername());
        int rows = aiModelMapper.updateAiModel(model);
        if (rows == 0)
        {
            throw new ServiceException("模型不存在");
        }
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public List<AiRemoteModel> listRemoteModels(Long channelId)
    {
        AiChannel channel = requireChannel(channelId);
        AiProvider provider = aiConfigCache.getProvider(channel.getProviderCode());
        if (provider == null)
        {
            throw new ServiceException("渠道绑定的服务商不存在或已停用：" + channel.getProviderCode());
        }
        AiProviderAdapter adapter = aiConfigCache.getAdapter(provider.getProtocol());
        if (adapter == null)
        {
            throw new ServiceException("没有可用的协议适配器：" + provider.getProtocol());
        }
        List<String> remoteNames;
        try
        {
            remoteNames = adapter.listModels(channel);
        }
        catch (UnsupportedOperationException e)
        {
            throw new ServiceException("服务商「" + provider.getName() + "」不提供模型列表接口，请手动添加模型");
        }
        Set<String> existing = existingModelNames(channelId);
        return remoteNames.stream()
                .map(name -> new AiRemoteModel(name, existing.contains(name), guessCapability(name)))
                .collect(Collectors.toList());
    }

    @Override
    public int importModels(AiModelImportRequest request)
    {
        AiChannel channel = requireChannel(request.getChannelId());
        Set<String> existing = existingModelNames(channel.getId());
        String operator = SecurityUtils.getUsername();
        int rows = 0;
        for (AiModelImportRequest.Item item : request.getModels())
        {
            String modelName = item.getModelName().trim();
            // 同一渠道下同名模型只保留一条；不同渠道可以有同名模型，用于降级
            if (modelName.isEmpty() || !existing.add(modelName))
            {
                continue;
            }
            AiModel model = new AiModel();
            model.setChannelId(channel.getId());
            model.setModelName(modelName);
            model.setCapability(requireCapability(item.getCapability()));
            model.setDisplayName(modelName);
            model.setEnabled(ENABLED);
            model.setCreateBy(operator);
            rows += aiModelMapper.insertAiModel(model);
        }
        if (rows > 0)
        {
            aiConfigService.refresh();
        }
        return rows;
    }

    /**
     * 取渠道，不存在时抛业务异常
     *
     * @param channelId 渠道ID
     * @return 渠道
     */
    private AiChannel requireChannel(Long channelId)
    {
        if (channelId == null)
        {
            throw new ServiceException("归属渠道不能为空");
        }
        AiChannel channel = aiChannelMapper.selectAiChannelById(channelId);
        if (channel == null)
        {
            throw new ServiceException("归属渠道不存在：" + channelId);
        }
        return channel;
    }

    /**
     * 校验扩展配置是合法的 JSON 对象
     *
     * <p>不校验的话，写错的 JSON 会被适配器当成「没配」静默忽略，表现成
     * 「配置明明写了却不生效」——排查时只能看到 extra 里的原字符串，很难定位。</p>
     *
     * @param extra 扩展配置
     */
    private void requireValidExtra(String extra)
    {
        if (StrUtil.isBlank(extra))
        {
            return;
        }
        try
        {
            if (JSON.parseObject(extra) == null)
            {
                throw new ServiceException("扩展配置必须是 JSON 对象，如 {\"reasoning\":true}");
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("扩展配置不是合法的 JSON 对象：" + e.getMessage());
        }
    }

    /**
     * 取渠道下已有的模型名
     *
     * @param channelId 渠道ID
     * @return 模型名集合
     */
    private Set<String> existingModelNames(Long channelId)
    {
        AiModel query = new AiModel();
        query.setChannelId(channelId);
        return aiModelMapper.selectAiModelList(query).stream()
                .map(AiModel::getModelName)
                .collect(Collectors.toSet());
    }

    /**
     * 校验能力类型取值
     *
     * @param capability 能力类型
     * @return 规范化后的能力类型
     */
    private String requireCapability(String capability)
    {
        if (StrUtil.isBlank(capability))
        {
            throw new ServiceException("能力类型不能为空");
        }
        try
        {
            return AiCapability.valueOf(capability.trim().toUpperCase()).name();
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("不支持的能力类型：" + capability);
        }
    }

    /**
     * 按模型名推测能力类型
     *
     * <p>厂商的 models 接口不返回能力信息，这里按常见命名习惯给个默认值，
     * 导入时用户可以逐行改。猜错只影响默认值，不影响导入结果。</p>
     *
     * @param modelName 模型名
     * @return 推测的能力类型
     */
    private String guessCapability(String modelName)
    {
        String name = modelName.toLowerCase();
        if (name.contains("embed"))
        {
            return AiCapability.EMBEDDING.name();
        }
        if (name.contains("wanx") || name.contains("dall-e") || name.contains("t2i")
                || name.contains("stable-diffusion") || name.contains("flux"))
        {
            return AiCapability.IMAGE.name();
        }
        if (name.contains("-vl") || name.contains("vl-") || name.contains("vision"))
        {
            return AiCapability.VISION.name();
        }
        return AiCapability.CHAT.name();
    }
}
