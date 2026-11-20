package com.howe.pregnancy.util;

import com.howe.pregnancy.constant.PregnancyConstants;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 孕周与倒计时计算
 *
 * <p>全项目统一的孕周口径：预产期当天为 40 周 0 天，即
 * {@code gestationalDay = 280 + daysBetween(dueDate, date)}。负值表示孕前。</p>
 *
 * <p>孕周只在展示时计算，不写入业务记录；调整预产期后历史日期按新值重算，
 * 但原始发生日期与时刻不变。</p>
 *
 * @author howe
 */
public final class GestationUtils {

    /**
     * 工具类不允许实例化
     */
    private GestationUtils() {
    }

    /**
     * 计算孕日
     *
     * @param dueDate 预产期，可为空
     * @param date    查询日期，可为空
     * @return 孕日；任一为空返回 null
     */
    public static Integer gestationalDay(LocalDate dueDate, LocalDate date) {
        if (dueDate == null || date == null) {
            return null;
        }
        return (int) (PregnancyConstants.GESTATION_DAYS_AT_DUE + ChronoUnit.DAYS.between(dueDate, date));
    }

    /**
     * 计算孕周数
     *
     * @param dueDate 预产期
     * @param date    查询日期
     * @return 孕周；孕前或缺少预产期返回 null
     */
    public static Integer week(LocalDate dueDate, LocalDate date) {
        Integer day = gestationalDay(dueDate, date);
        return day == null || day < 0 ? null : day / 7;
    }

    /**
     * 计算孕周内天数
     *
     * @param dueDate 预产期
     * @param date    查询日期
     * @return 天数；孕前或缺少预产期返回 null
     */
    public static Integer dayOfWeek(LocalDate dueDate, LocalDate date) {
        Integer day = gestationalDay(dueDate, date);
        return day == null || day < 0 ? null : day % 7;
    }

    /**
     * 计算孕日对应的预产期日期
     *
     * @param dueDate        预产期
     * @param gestationalDay 孕日
     * @return 对应日期
     */
    public static LocalDate dateOfGestationalDay(LocalDate dueDate, int gestationalDay) {
        return dueDate.plusDays(gestationalDay - PregnancyConstants.GESTATION_DAYS_AT_DUE);
    }

    /**
     * 格式化孕周为「N 周 M 天」
     *
     * @param dueDate 预产期
     * @param date    查询日期
     * @return 展示文本；孕前返回「孕前」，缺少预产期返回 null
     */
    public static String formatGestation(LocalDate dueDate, LocalDate date) {
        if (dueDate == null) {
            return null;
        }
        Integer week = week(dueDate, date);
        if (week == null) {
            return "孕前";
        }
        return week + " 周 " + dayOfWeek(dueDate, date) + " 天";
    }

    /**
     * 计算距预产期天数
     *
     * @param today   业务今天
     * @param dueDate 预产期
     * @return 天数；过期为负，任一为空返回 null
     */
    public static Long daysUntilDue(LocalDate today, LocalDate dueDate) {
        if (today == null || dueDate == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(today, dueDate);
    }

    /**
     * 计算距 37 周天数
     *
     * @param today   业务今天
     * @param dueDate 预产期
     * @return 天数；已达返回 0 或负数，任一为空返回 null
     */
    public static Long daysUntil37Weeks(LocalDate today, LocalDate dueDate) {
        if (today == null || dueDate == null) {
            return null;
        }
        LocalDate dateOf37 = dueDate.minusDays(PregnancyConstants.GESTATION_DAYS_AT_DUE
                - PregnancyConstants.DAYS_OF_FULL_TERM);
        return ChronoUnit.DAYS.between(today, dateOf37);
    }

    /**
     * 判断是否为有效的每日内容孕日
     *
     * @param gestationalDay 孕日
     * @return 落在素材范围内返回 true
     */
    public static boolean hasDailyContent(Integer gestationalDay) {
        return gestationalDay != null
                && gestationalDay >= PregnancyConstants.GROWTH_DAY_MIN
                && gestationalDay <= PregnancyConstants.GROWTH_DAY_MAX;
    }

    /**
     * 判断是否为有效的周概况孕周
     *
     * @param week 孕周
     * @return 落在素材范围内返回 true
     */
    public static boolean hasWeeklyContent(Integer week) {
        return week != null
                && week >= PregnancyConstants.GROWTH_WEEK_MIN
                && week <= PregnancyConstants.GROWTH_WEEK_MAX;
    }
}
