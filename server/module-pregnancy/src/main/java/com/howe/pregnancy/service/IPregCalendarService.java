package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.vo.CalendarDayVO;
import com.howe.pregnancy.domain.vo.CalendarMonthVO;
import com.howe.pregnancy.domain.vo.GrowthVO;

import java.time.LocalDate;

/**
 * 日历与每日变化服务
 *
 * @author howe
 */
public interface IPregCalendarService {

    /**
     * 查询月摘要
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 月摘要
     */
    CalendarMonthVO month(LocalDate from, LocalDate to);

    /**
     * 查询当天详情
     *
     * @param date 日期
     * @return 当天详情
     */
    CalendarDayVO day(LocalDate date);

    /**
     * 查询每日变化时间轴
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含），跨度最多 31 天
     * @return 时间轴
     */
    GrowthVO growth(LocalDate from, LocalDate to);
}
