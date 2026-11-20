package com.howe.pregnancy.util;

import com.howe.pregnancy.constant.PregnancyConstants;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 孕期业务的日期、时刻与展示转换
 *
 * <p>接口层的日期是 {@code YYYY-MM-DD}，时刻是带偏移的 ISO 8601；数据库存 UTC 时刻与
 * 派生的自然日。所有转换集中在这里，避免各 Service 各写一套时区处理。</p>
 *
 * <p>「业务今天」始终按 {@code Asia/Shanghai} 计算，不依赖服务器默认时区或客户端传入。</p>
 *
 * @author howe
 */
public final class PregnancyTimeUtils {

    /** 业务时区 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(PregnancyConstants.TIMEZONE);

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 工具类不允许实例化
     */
    private PregnancyTimeUtils() {
    }

    /**
     * 取业务时区下的今天
     *
     * @return 业务今天
     */
    public static LocalDate businessToday() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /**
     * 取业务时区
     *
     * @return 时区
     */
    public static ZoneId businessZone() {
        return BUSINESS_ZONE;
    }

    /**
     * 解析日期字符串
     *
     * @param text  日期文本
     * @param field 字段名，用于错误提示
     * @return 日期
     */
    public static LocalDate parseDate(String text, String field) {
        if (text == null || text.isBlank()) {
            throw PregnancyException.invalid(field + "不能为空");
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw PregnancyException.invalid(field + "格式不正确，应为 YYYY-MM-DD");
        }
    }

    /**
     * 解析可选日期
     *
     * @param text  日期文本，可为空
     * @param field 字段名
     * @return 日期，空文本返回 null
     */
    public static LocalDate parseOptionalDate(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return parseDate(text, field);
    }

    /**
     * 解析带偏移的时刻；缺少偏移时按业务时区解释
     *
     * @param text  时刻文本
     * @param field 字段名
     * @return 时刻
     */
    public static OffsetDateTime parseOccurredAt(String text, String field) {
        if (text == null || text.isBlank()) {
            throw PregnancyException.invalid(field + "不能为空");
        }
        String value = text.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 兼容不带偏移的本地时刻：按业务时区解释
        }
        if (value.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(:\\d{2})?$")) {
            value = value.replace(' ', 'T');
        }
        try {
            LocalDateTime local = LocalDateTime.parse(value);
            return local.atZone(BUSINESS_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            throw PregnancyException.invalid(field + "格式不正确，应为带偏移的 ISO 8601 时刻");
        }
    }

    /**
     * 解析可选时刻（HH:mm 或 HH:mm:ss）
     *
     * @param text  时刻文本，可为空
     * @param field 字段名
     * @return 时刻，空文本返回 null
     */
    public static LocalTime parseOptionalTime(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 兼容 HH:mm
        }
        try {
            return LocalTime.parse(value + ":00");
        } catch (DateTimeParseException e) {
            throw PregnancyException.invalid(field + "格式不正确，应为 HH:mm 或 HH:mm:ss");
        }
    }

    /**
     * 判断某日期是否晚于业务今天
     *
     * @param date 日期
     * @return 晚于返回 true
     */
    public static boolean isAfterBusinessToday(LocalDate date) {
        return date != null && date.isAfter(businessToday());
    }

    /**
     * 把带偏移时刻转成数据库比较用的业务自然日
     *
     * @param occurredAt 发生时刻
     * @return 业务自然日
     */
    public static LocalDate businessDateOf(OffsetDateTime occurredAt) {
        return occurredAt.atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
    }

    /**
     * 把自然日转成 JDBC 日期
     *
     * @param date 自然日
     * @return java.sql.Date，空值返回 null
     */
    public static java.sql.Date toSqlDate(LocalDate date) {
        return date == null ? null : java.sql.Date.valueOf(date);
    }

    /**
     * 把带偏移时刻转成 JDBC 时刻
     *
     * @param occurredAt 发生时刻
     * @return java.sql.Timestamp，空值返回 null
     */
    public static java.sql.Timestamp toSqlTimestamp(OffsetDateTime occurredAt) {
        return occurredAt == null ? null
                : java.sql.Timestamp.valueOf(occurredAt.atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime());
    }

    /**
     * 把 {@link Date} 转成业务时区的自然日
     *
     * @param date 数据库日期或时刻
     * @return 自然日，空值返回 null
     */
    public static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (date instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return date.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
    }

    /**
     * 把 {@link Date} 转成业务时区的带偏移时刻
     *
     * @param date 数据库时刻
     * @return 带偏移时刻，空值返回 null
     */
    public static OffsetDateTime toOffsetDateTime(Date date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().atZone(BUSINESS_ZONE).toOffsetDateTime();
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        }
        return date.toInstant().atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    /**
     * 格式化时刻为接口使用的带偏移文本
     *
     * @param instant 时刻
     * @return ISO 8601 文本，空值返回 null
     */
    public static String formatOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(BUSINESS_ZONE).toOffsetDateTime().toString();
    }

    /**
     * 格式化自然日
     *
     * @param date 自然日
     * @return YYYY-MM-DD，空值返回 null
     */
    public static String formatDate(LocalDate date) {
        return date == null ? null : DATE_FORMATTER.format(date);
    }

    /**
     * 当前时刻（业务时区）
     *
     * @return 当前时刻
     */
    public static OffsetDateTime now() {
        return OffsetDateTime.now(BUSINESS_ZONE);
    }
}
