package com.howe.ai.service.impl;

import com.howe.ai.config.AiConfigService;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.AiSceneRoute;
import com.howe.ai.persistence.mapper.AiModelMapper;
import com.howe.ai.persistence.mapper.AiSceneRouteMapper;
import com.howe.ai.service.IAiSceneRouteService;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 场景路由 服务层实现
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSceneRouteServiceImpl implements IAiSceneRouteService
{
    /** 启用 */
    private static final Integer ENABLED = 1;

    private final AiSceneRouteMapper aiSceneRouteMapper;

    private final AiModelMapper aiModelMapper;

    private final AiConfigService aiConfigService;

    @Override
    public List<AiSceneRoute> selectAiSceneRouteList(AiSceneRoute aiSceneRoute)
    {
        return aiSceneRouteMapper.selectAiSceneRouteList(aiSceneRoute);
    }

    @Override
    public int saveAiSceneRoute(AiSceneRoute aiSceneRoute)
    {
        requireModel(aiSceneRoute.getPrimaryModelId(), "主模型");
        aiSceneRoute.setEnabled(aiSceneRoute.getEnabled() == null ? ENABLED : aiSceneRoute.getEnabled());
        aiSceneRoute.setUpdateBy(SecurityUtils.getUsername());

        AiSceneRoute existing = aiSceneRoute.getId() == null
                ? aiSceneRouteMapper.selectByScene(aiSceneRoute.getScene(), aiSceneRoute.getCapability())
                : aiSceneRouteMapper.selectAiSceneRouteById(aiSceneRoute.getId());
        int rows;
        if (existing == null)
        {
            aiSceneRoute.setCreateBy(SecurityUtils.getUsername());
            rows = aiSceneRouteMapper.insertAiSceneRoute(aiSceneRoute);
        }
        else
        {
            aiSceneRoute.setId(existing.getId());
            rows = aiSceneRouteMapper.updateAiSceneRoute(aiSceneRoute);
        }
        aiConfigService.refresh();
        return rows;
    }

    @Override
    public int deleteAiSceneRouteByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请选择要删除的数据");
        }
        int rows = aiSceneRouteMapper.deleteAiSceneRouteByIds(ids);
        aiConfigService.refresh();
        return rows;
    }

    /**
     * 校验模型存在
     *
     * @param modelId 模型ID
     * @param label   字段名，用于提示
     */
    private void requireModel(Long modelId, String label)
    {
        if (modelId == null)
        {
            throw new ServiceException(label + "不能为空");
        }
        AiModel model = aiModelMapper.selectAiModelById(modelId);
        if (model == null)
        {
            throw new ServiceException(label + "不存在：" + modelId);
        }
    }
}
