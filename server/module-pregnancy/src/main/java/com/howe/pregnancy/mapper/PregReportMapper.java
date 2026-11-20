package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregDietRecord;
import com.howe.pregnancy.domain.PregFile;
import com.howe.pregnancy.domain.PregReport;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报告与饮食数据层
 *
 * @author howe
 */
public interface PregReportMapper {

    /**
     * 按条件查询报告
     *
     * @param from       起始报告日，可为空
     * @param to         结束报告日，可为空
     * @param checkupId  产检 ID，可为空
     * @return 报告列表
     */
    List<PregReport> selectList(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                @Param("checkupId") Long checkupId);

    /**
     * 按主键查询
     *
     * @param id 报告 ID
     * @return 报告，不存在返回 null
     */
    PregReport selectById(@Param("id") Long id);

    /**
     * 按请求 ID 查询（含软删）
     *
     * @param actorUserId 操作人
     * @param requestId   请求 ID
     * @return 报告，不存在返回 null
     */
    PregReport selectByRequest(@Param("actorUserId") Long actorUserId,
                               @Param("requestId") String requestId);

    /**
     * 新增报告
     *
     * @param report 报告
     * @return 影响行数
     */
    int insert(PregReport report);

    /**
     * 按版本更新
     *
     * @param report 报告
     * @return 影响行数
     */
    int update(PregReport report);

    /**
     * 逻辑删除
     *
     * @param id      报告 ID
     * @param version 版本
     * @return 影响行数
     */
    int logicalDelete(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 解除测量与报告的关联
     *
     * @param reportId 报告 ID
     * @return 影响行数
     */
    int detachMeasurements(@Param("reportId") Long reportId);

    /**
     * 按报告查询绑定文件
     *
     * @param reportId 报告 ID
     * @return 文件列表
     */
    List<PregFile> selectFilesByReport(@Param("reportId") Long reportId);

    /**
     * 按报告统计
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 每行含 recordDate 与 total
     */
    List<Map<String, Object>> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 查询某天的报告
     *
     * @param date 日期
     * @return 报告列表
     */
    List<PregReport> selectByDay(@Param("date") LocalDate date);
}
