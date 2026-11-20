package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregMeasurement;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 固定指标测量数据层
 *
 * @author howe
 */
public interface PregMeasurementMapper {

    /**
     * 按条件查询测量记录
     *
     * @param metricCode 指标编码，可为空
     * @param from       起始业务日，可为空
     * @param to         结束业务日，可为空
     * @param scene      场景，可为空
     * @param unit       单位，可为空
     * @param reportId   报告 ID，可为空
     * @return 记录列表
     */
    List<PregMeasurement> selectList(@Param("metricCode") String metricCode,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("scene") String scene,
                                     @Param("unit") String unit,
                                     @Param("reportId") Long reportId);

    /**
     * 按主键查询未删除记录
     *
     * @param id 记录 ID
     * @return 记录，不存在返回 null
     */
    PregMeasurement selectById(@Param("id") Long id);

    /**
     * 按记录人 + 请求 ID 查询（含已软删，用于幂等判断）
     *
     * @param actorUserId 记录人
     * @param requestId   请求 ID
     * @return 记录，不存在返回 null
     */
    PregMeasurement selectByRequest(@Param("actorUserId") Long actorUserId,
                                    @Param("requestId") String requestId);

    /**
     * 新增记录
     *
     * @param measurement 记录
     * @return 影响行数
     */
    int insert(PregMeasurement measurement);

    /**
     * 按版本更新记录
     *
     * @param measurement 记录
     * @return 影响行数，0 表示版本冲突
     */
    int update(PregMeasurement measurement);

    /**
     * 逻辑删除
     *
     * @param id        记录 ID
     * @param version   版本
     * @param updateBy  操作人
     * @return 影响行数
     */
    int logicalDelete(@Param("id") Long id, @Param("version") Integer version,
                      @Param("updateBy") String updateBy);

    /**
     * 报告删除时解除测量关联
     *
     * @param reportId 报告 ID
     * @return 影响行数
     */
    int detachReport(@Param("reportId") Long reportId);

    /**
     * 查询指定日期范围内的指标标记数量
     *
     * @param from 起始业务日
     * @param to   结束业务日
     * @return 每行含 recordDate 与 total
     */
    List<Map<String, Object>> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 查询某天的饮水合计
     *
     * @param recordDate 业务日
     * @return 合计值，无记录返回 null
     */
    java.math.BigDecimal sumWaterByDay(@Param("recordDate") LocalDate recordDate);

    /**
     * 按指标与筛选条件查询统计原始数据
     *
     * @param metricCode 指标编码
     * @param from       起始业务日
     * @param to         结束业务日
     * @param scene      场景
     * @param unit       单位
     * @return 原始记录（按发生时刻升序）
     */
    List<PregMeasurement> selectForStatistics(@Param("metricCode") String metricCode,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to,
                                              @Param("scene") String scene,
                                              @Param("unit") String unit);

    /**
     * 按报告查询关联测量
     *
     * @param reportId 报告 ID
     * @return 记录列表
     */
    List<PregMeasurement> selectByReport(@Param("reportId") Long reportId);

    /**
     * 判断业务当天是否存在任意有效实际记录
     *
     * @param recordDate 业务日
     * @return 存在返回 true
     */
    boolean existsActualRecordOn(@Param("recordDate") LocalDate recordDate);
}
