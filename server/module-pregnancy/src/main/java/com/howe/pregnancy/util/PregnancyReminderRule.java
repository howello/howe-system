package com.howe.pregnancy.util;

import com.howe.common.utils.spring.SpringUtils;
import com.howe.pregnancy.domain.PregReminderRuleEntity;
import com.howe.pregnancy.mapper.PregReminderRuleMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提醒规则定义与读取
 *
 * <p>规则的默认值与可读名称由枚举固化；当前生效值存放在 {@code preg_reminder_rule}
 * 表，一行一条规则。表中缺少某条规则（旧库新增规则）时自动回落到枚举默认值。</p>
 *
 * @author howe
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public enum PregnancyReminderRule {

    /** 产检提前提醒 */
    CHECKUP_BEFORE("产检提前提醒", true, "20:00", 1, null),
    /** 产检当日提醒 */
    CHECKUP_TODAY("产检当日提醒", true, "07:30", 0, null),
    /** 每日记录提醒 */
    DAILY_RECORD("每日记录提醒", false, "21:00", 0, null),
    /** 待产包周提醒 */
    BAG_WEEKLY("待产包周提醒", false, "10:00", 0, 0),
    /** 每日吃叶酸提醒 */
    FOLIC_ACID("每日吃叶酸提醒", true, "20:00", null, null),
    /** 每日变化推送 */
    DAILY_GROWTH("每日变化推送", true, "09:00", null, null),
    /** 孕周变化推送 */
    WEEKLY_GROWTH("孕周变化推送", true, "10:00", null, null);

    private final String label;
    private final boolean defaultEnabled;
    private final String defaultTime;
    private final Integer defaultDaysBefore;
    private final Integer defaultWeekday;

    /**
     * 读取当前生效的规则（表内值覆盖枚举默认值）
     *
     * <p>每次读取都直接查表：规则是低频操作，且有 {@code next_run_at} 索引保护，
     * 不额外引入缓存，避免多实例之间出现规则不一致。</p>
     *
     * @return 规则编码到生效设置的映射，读取失败时返回空映射（全部走默认值）
     */
    public static Map<String, Setting> readSettings() {
        Map<String, Setting> result = new HashMap<>();
        try {
            List<PregReminderRuleEntity> rows =
                    SpringUtils.getBean(PregReminderRuleMapper.class).selectAll();
            for (PregReminderRuleEntity row : rows) {
                result.put(row.getRuleCode(), toSetting(row));
            }
        } catch (RuntimeException e) {
            log.warn("读取孕期提醒规则失败，按默认值处理", e);
        }
        return result;
    }

    /**
     * 将表行转换为生效设置，非法时刻回落到默认值
     *
     * @param row 规则行
     * @return 生效设置
     */
    private static Setting toSetting(PregReminderRuleEntity row) {
        String code = row.getRuleCode();
        PregnancyReminderRule definition = null;
        try {
            definition = valueOf(code);
        } catch (IllegalArgumentException e) {
            log.warn("孕期提醒规则存在未知编码：{}", code);
        }
        String time = validTime(row.getRemindTime())
                ? row.getRemindTime()
                : (definition == null ? null : definition.getDefaultTime());
        return new Setting(Boolean.TRUE.equals(row.getEnabled()), LocalTime.parse(time),
                row.getDaysBefore(), row.getWeekday());
    }

    /**
     * 合并表内覆盖值与默认值
     *
     * @param settings 表内规则映射
     * @return 生效设置
     */
    public Setting resolve(Map<String, Setting> settings) {
        Setting stored = settings.get(name());
        return stored == null
                ? new Setting(defaultEnabled, LocalTime.parse(defaultTime), defaultDaysBefore, defaultWeekday)
                : stored;
    }

    /**
     * 校验设置页面统一的 24 小时时刻格式
     *
     * @param value 时刻文本
     * @return 合法返回 true
     */
    public static boolean validTime(String value) {
        return value != null && value.matches("([01][0-9]|2[0-3]):[0-5][0-9]");
    }

    /**
     * 排程实际生效的提醒设置
     *
     * @param enabled    是否启用
     * @param time       提醒时刻
     * @param daysBefore 提前天数
     * @param weekday    星期（0 周日）
     */
    public record Setting(boolean enabled, LocalTime time, Integer daysBefore, Integer weekday) {
    }
}
