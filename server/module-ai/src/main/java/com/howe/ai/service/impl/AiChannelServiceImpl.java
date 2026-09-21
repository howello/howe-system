package com.howe.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.config.AiConfigService;
import com.howe.ai.config.AiSecretCipher;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.AiProvider;
import com.howe.ai.persistence.mapper.AiChannelMapper;
import com.howe.ai.persistence.mapper.AiModelMapper;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import com.howe.ai.provider.AiHealthResult;
import com.howe.ai.provider.AiProviderAdapter;
import com.howe.ai.service.IAiChannelService;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * AI 渠道 服务层实现
 *
 * <p>密钥只在这里进出：写入时加密、读出时打掩码，任何响应都不会带明文。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChannelServiceImpl implements IAiChannelService
{
    /** 掩码标记，编辑时原样回传表示不修改密钥 */
    private static final String MASK_MARK = "****";

    /** 启用 */
    private static final Integer ENABLED = 1;

    /** 停用 */
    private static final Integer DISABLED = 0;

    private final AiChannelMapper aiChannelMapper;

    private final AiModelMapper aiModelMapper;

    private final AiProviderMapper aiProviderMapper;

    private final AiSecretCipher aiSecretCipher;

    private final AiConfigService aiConfigService;

    private final AiConfigCache aiConfigCache;

    @Override
    public List<AiProvider> selectProviderOptions()
    {
        return aiProviderMapper.selectEnabledList();
    }

    @Override
    public List<AiChannel> selectAiChannelList(AiChannel aiChannel)
    {
        List<AiChannel> channels = aiChannelMapper.selectAiChannelList(aiChannel);
        channels.forEach(this::maskApiKey);
        return channels;
    }

    @Override
    public AiChannel selectAiChannelById(Long id)
    {
        AiChannel channel = aiChannelMapper.selectAiChannelById(id);
        if (channel == null)
        {
            throw new ServiceException("渠道不存在");
        }
        maskApiKey(channel);
        return channel;
    }

    @Override
    public int insertAiChannel(AiChannel aiChannel)
    {
        AiProvider provider = aiConfigCache.getProvider(aiChannel.getProviderCode());
        if (provider == null)
        {
            throw new ServiceException("服务商不存在或已停用：" + aiChannel.getProviderCode());
        }
        aiChannel.setApiKey(aiSecretCipher.encrypt(aiChannel.getApiKey()));
        aiChannel.setEnabled(aiChannel.getEnabled() == null ? ENABLED : aiChannel.getEnabled());
        aiChannel.setPriority(aiChannel.getPriority() == null ? 1 : aiChannel.getPriority());
        aiChannel.setHealthStatus("UNKNOWN");
        aiChannel.setCreateBy(SecurityUtils.getUsername());
        int rows = aiChannelMapper.insertAiChannel(aiChannel);
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public int updateAiChannel(AiChannel aiChannel)
    {
        AiChannel existing = aiChannelMapper.selectAiChannelById(aiChannel.getId());
        if (existing == null)
        {
            throw new ServiceException("渠道不存在");
        }
        aiChannel.setUpdateBy(SecurityUtils.getUsername());
        int rows = aiChannelMapper.updateAiChannel(aiChannel);
        // 密钥留空或回传掩码表示不修改；只有确实填了新密钥才覆盖
        if (isNewApiKey(aiChannel.getApiKey()))
        {
            aiChannelMapper.updateApiKey(aiChannel.getId(), aiSecretCipher.encrypt(aiChannel.getApiKey()),
                    aiChannel.getUpdateBy());
        }
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public int deleteAiChannelByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long id : ids)
        {
            int referenced = aiModelMapper.countByChannelId(id);
            if (referenced > 0)
            {
                AiChannel channel = aiChannelMapper.selectAiChannelById(id);
                throw new ServiceException("渠道「" + (channel == null ? id : channel.getName())
                        + "」下还有 " + referenced + " 个模型，请先删除这些模型");
            }
        }
        int rows = aiChannelMapper.deleteAiChannelByIds(ids);
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public int updateStatus(Long id, Integer enabled)
    {
        AiChannel channel = new AiChannel();
        channel.setId(id);
        channel.setEnabled(enabled);
        channel.setUpdateBy(SecurityUtils.getUsername());
        int rows = aiChannelMapper.updateAiChannel(channel);
        if (rows == 0)
        {
            throw new ServiceException("渠道不存在");
        }
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public AiHealthResult testConnection(Long id, Long modelId)
    {
        AiChannel channel = aiChannelMapper.selectAiChannelById(id);
        if (channel == null)
        {
            throw new ServiceException("渠道不存在");
        }
        AiModel model = modelId == null ? findFirstModel(id) : aiModelMapper.selectAiModelById(modelId);
        AiHealthResult result;
        if (model == null)
        {
            result = AiHealthResult.fail("请先为该渠道配置至少一个启用的模型", AiErrorCode.BAD_REQUEST.name());
        }
        else
        {
            try
            {
                AiProvider provider = aiConfigCache.getProvider(channel.getProviderCode());
                if (provider == null)
                {
                    result = AiHealthResult.fail("渠道绑定的服务商不存在或已停用：" + channel.getProviderCode(),
                            AiErrorCode.CHANNEL_DISABLED.name());
                }
                else
                {
                    AiProviderAdapter adapter = aiConfigCache.getAdapter(provider.getProtocol());
                    result = adapter.healthCheck(channel, model);
                }
            }
            catch (AiException e)
            {
                result = AiHealthResult.fail(e.getMessage(), e.getAiErrorCode().name());
            }
            catch (Exception e)
            {
                log.error("渠道连通性检测失败", e);
                result = AiHealthResult.fail("连通性检测失败：" + e.getMessage(),
                        AiErrorCode.PROVIDER_ERROR.name());
            }
        }
        aiChannelMapper.updateHealth(id, result.success() ? "UP" : "DOWN", new Date());
        return result;
    }

    /**
     * 取渠道下第一个启用的模型
     *
     * @param channelId 渠道ID
     * @return 模型，没有时返回 null
     */
    private AiModel findFirstModel(Long channelId)
    {
        AiModel query = new AiModel();
        query.setChannelId(channelId);
        query.setEnabled(ENABLED);
        List<AiModel> models = aiModelMapper.selectAiModelList(query);
        return models.isEmpty() ? null : models.get(0);
    }

    /**
     * 判断入参是否为需要保存的新密钥
     *
     * @param apiKey 入参密钥
     * @return 需要覆盖原密钥时为 true
     */
    private boolean isNewApiKey(String apiKey)
    {
        return StrUtil.isNotBlank(apiKey) && !apiKey.contains(MASK_MARK);
    }

    /**
     * 把密钥替换成掩码
     *
     * @param channel 渠道
     */
    private void maskApiKey(AiChannel channel)
    {
        if (channel != null)
        {
            channel.setApiKey(aiSecretCipher.mask(channel.getApiKey()));
        }
    }
}
