package com.howe.ai.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * 调用记录的日期区间解析
 *
 * <p>列表与统计必须共用同一套口径，否则会出现「列表里有、统计里没有」。
 * 规则：区间<b>左闭右开</b>，结束日期推到次日零点。</p>
 *
 * <p>之所以不直接用 {@code DateUtils.parseDate}：它把 {@code 2026-11-04}
 * 解析成当日 00:00:00，配合 {@code <=} 会漏掉结束日当天的几乎所有记录。</p>
 *
 * @author howe
 */
final class DayRange {

    private DayRange() {
    }

    /**
     * 取某天的零点
     *
     * @param value 日期字符串（{@code yyyy-MM-dd}）或 {@link Date}
     * @return 当日零点的日期；解析失败返回原值
     */
    static Date startOfDay(Object value) {
        LocalDate date = toLocalDate(value);
        if (date == null) {
            return value instanceof Date d ? d : null;
        }
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 取某天的次日零点
     *
     * <p>区间用 {@code <} 比较，因此「结束日当天」能被完整包含。</p>
     *
     * @param value 日期字符串（{@code yyyy-MM-dd}）或 {@link Date}
     * @return 次日零点的日期；解析失败返回原值
     */
    static Date startOfNextDay(Object value) {
        LocalDate date = toLocalDate(value);
        if (date == null) {
            return value instanceof Date d ? d : null;
        }
        return Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 把入参解析成日期
     *
     * <p>只按「日」解析：带时分秒的入参也取其日期部分，
     * 避免同一组参数在列表与统计里得到不同边界。</p>
     *
     * @param value 日期字符串或 Date
     * @return 日期，无法解析时返回 null
     */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = value == null ? null : String.valueOf(value).trim();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return DateUtil.parse(text).toLocalDateTime().toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }
}
