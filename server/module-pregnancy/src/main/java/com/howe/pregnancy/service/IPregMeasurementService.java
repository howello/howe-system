package com.howe.pregnancy.service;

import com.github.pagehelper.PageInfo;
import com.howe.pregnancy.domain.dto.MeasurementSaveBody;
import com.howe.pregnancy.domain.vo.MeasurementVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 固定指标测量服务
 *
 * @author howe
 */
public interface IPregMeasurementService {

    /**
     * 查询测量记录
     *
     * @param metricCode 指标编码，可为空
     * @param from       起始业务日，可为空
     * @param to         结束业务日，可为空
     * @param scene      场景，可为空
     * @param unit       单位，可为空
     * @param reportId   报告 ID，可为空
     * @return 记录列表
     */
    PageInfo<MeasurementVO> list(String metricCode, LocalDate from, LocalDate to,
                             String scene, String unit, Long reportId);

    /**
     * 查询测量记录详情
     *
     * @param id 记录 ID
     * @return 记录详情
     */
    MeasurementVO detail(Long id);

    /**
     * 新增测量
     *
     * @param body 请求
     * @return 新建记录
     */
    MeasurementVO create(MeasurementSaveBody body);

    /**
     * 编辑测量
     *
     * @param id   记录 ID
     * @param body 请求
     * @return 更新后的记录
     */
    MeasurementVO update(Long id, com.howe.pregnancy.domain.dto.MeasurementUpdateBody body);

    /**
     * 逻辑删除测量
     *
     * @param id      记录 ID
     * @param version 版本
     */
    void delete(Long id, Integer version);

    /**
     * 查询指标统计
     *
     * @param metricCode  指标编码
     * @param from        起始业务日，可为空
     * @param to          结束业务日，可为空
     * @param scene       场景，可为空
     * @param unit        单位，可为空
     * @param aggregation 聚合口径
     * @return 统计结果
     */
    Map<String, Object> statistics(String metricCode, LocalDate from, LocalDate to,
                                   String scene, String unit, String aggregation);

    /**
     * 按报告查询关联测量
     *
     * @param reportId 报告 ID
     * @return 记录列表
     */
    List<MeasurementVO> listByReport(Long reportId);

    /**
     * 查询某天的饮水合计
     *
     * @param date 业务日
     * @return 合计值
     */
    BigDecimal waterTotal(LocalDate date);
}
