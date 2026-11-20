package com.howe.pregnancy.service;

import com.github.pagehelper.PageInfo;
import com.howe.pregnancy.domain.dto.DietSaveBody;
import com.howe.pregnancy.domain.dto.ReportSaveBody;
import com.howe.pregnancy.domain.vo.DietVO;
import com.howe.pregnancy.domain.vo.ReportVO;

import java.time.LocalDate;

/**
 * 报告与饮食服务
 *
 * @author howe
 */
public interface IPregReportService {

    /**
     * 查询报告列表
     *
     * @param from      起始报告日，可为空
     * @param to        结束报告日，可为空
     * @param checkupId 产检 ID，可为空
     * @return 报告列表
     */
    PageInfo<ReportVO> listReports(LocalDate from, LocalDate to, Long checkupId);

    /**
     * 查询报告详情
     *
     * @param id 报告 ID
     * @return 报告详情
     */
    ReportVO getReport(Long id);

    /**
     * 新增报告
     *
     * @param body 请求
     * @return 新建报告
     */
    ReportVO createReport(ReportSaveBody body);

    /**
     * 编辑报告
     *
     * @param id   报告 ID
     * @param body 请求
     * @return 更新后的报告
     */
    ReportVO updateReport(Long id, com.howe.pregnancy.domain.dto.ReportUpdateBody body);

    /**
     * 逻辑删除报告
     *
     * @param id      报告 ID
     * @param version 版本
     */
    void deleteReport(Long id, Integer version);

    /**
     * 查询饮食记录
     *
     * @param mealType 餐次，可为空
     * @param from     起始日期，可为空
     * @param to       结束日期，可为空
     * @return 饮食记录
     */
    PageInfo<DietVO> listDiets(String mealType, LocalDate from, LocalDate to);

    /**
     * 查询饮食详情
     *
     * @param id 记录 ID
     * @return 饮食详情
     */
    DietVO getDiet(Long id);

    /**
     * 新增饮食记录
     *
     * @param body 请求
     * @return 新建记录
     */
    DietVO createDiet(DietSaveBody body);

    /**
     * 编辑饮食记录
     *
     * @param id   记录 ID
     * @param body 请求
     * @return 更新后的记录
     */
    DietVO updateDiet(Long id, com.howe.pregnancy.domain.dto.DietUpdateBody body);

    /**
     * 逻辑删除饮食记录
     *
     * @param id      记录 ID
     * @param version 版本
     */
    void deleteDiet(Long id, Integer version);
}
