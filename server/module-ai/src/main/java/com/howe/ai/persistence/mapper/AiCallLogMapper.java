package com.howe.ai.persistence.mapper;

import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * AI 调用记录 数据层
 *
 * @author howe
 */
public interface AiCallLogMapper
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
    AiCallLog selectAiCallLogById(@Param("id") Long id);

    /**
     * 新增调用记录
     *
     * @param aiCallLog 调用记录
     * @return 结果
     */
    int insertAiCallLog(AiCallLog aiCallLog);

    /**
     * 删除指定时间之前的调用记录，供保留天数清理使用
     *
     * @param before 时间点
     * @return 删除行数
     */
    int deleteBefore(@Param("before") Date before);

    /**
     * 按维度聚合用量统计
     *
     * @param groupBy   聚合维度：scene / model / day
     * @param beginTime 起始时间，可为空
     * @param endTime   结束时间，可为空
     * @param scene     场景过滤，可为空
     * @return 统计结果
     */
    List<AiCallLogStats> selectStats(@Param("groupBy") String groupBy, @Param("beginTime") Date beginTime,
            @Param("endTime") Date endTime, @Param("scene") String scene);
}
