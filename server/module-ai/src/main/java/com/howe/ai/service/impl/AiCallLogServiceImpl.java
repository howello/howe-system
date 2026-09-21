package com.howe.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import com.howe.ai.persistence.mapper.AiCallLogMapper;
import com.howe.ai.service.IAiCallLogService;
import com.howe.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * AI 调用记录 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class AiCallLogServiceImpl implements IAiCallLogService
{
    /** 允许的聚合维度 */
    private static final Set<String> GROUP_BY = Set.of("scene", "model", "day");

    private final AiCallLogMapper aiCallLogMapper;

    @Override
    public List<AiCallLog> selectAiCallLogList(AiCallLog aiCallLog)
    {
        return aiCallLogMapper.selectAiCallLogList(aiCallLog);
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

    @Override
    public List<AiCallLogStats> selectStats(String groupBy, Date beginTime, Date endTime, String scene)
    {
        String dimension = StrUtil.isBlank(groupBy) ? "scene" : groupBy.trim().toLowerCase();
        if (!GROUP_BY.contains(dimension))
        {
            throw new ServiceException("不支持的聚合维度：" + groupBy + "，可选 scene / model / day");
        }
        return aiCallLogMapper.selectStats(dimension, beginTime, endTime, scene);
    }
}
