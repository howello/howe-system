package com.howe.ai.service;

import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.vo.AiCallLogStats;

import java.util.Date;
import java.util.List;

/**
 * AI 调用记录 服务层
 *
 * @author howe
 */
public interface IAiCallLogService
{
    /**
     * 查询调用记录列表
     *
     * @param aiCallLog 查询条件
     * @return 记录集合
     */
    List<AiCallLog> selectAiCallLogList(AiCallLog aiCallLog);

    /**
     * 按主键查询调用记录
     *
     * @param id 主键ID
     * @return 调用记录
     */
    AiCallLog selectAiCallLogById(Long id);

    /**
     * 按维度聚合用量统计
     *
     * @param groupBy   聚合维度：scene / model / day
     * @param beginTime 起始时间，可为空
     * @param endTime   结束时间，可为空
     * @param scene     场景过滤，可为空
     * @return 统计结果
     */
    List<AiCallLogStats> selectStats(String groupBy, Date beginTime, Date endTime, String scene);
}
