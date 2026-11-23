package com.howe.pregnancy.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.common.utils.ConfigUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;

/** 提醒设置、预约排程与后台周期任务共用的规则定义。 */
@Slf4j
@Getter
@RequiredArgsConstructor
public enum PregnancyReminderRule {
    CHECKUP_BEFORE("产检提前提醒", true, "20:00", 1, null),
    CHECKUP_TODAY("产检当日提醒", true, "07:30", 0, null),
    DAILY_RECORD("每日记录提醒", false, "21:00", 0, null),
    BAG_WEEKLY("待产包周提醒", false, "10:00", 0, 0),
    FOLIC_ACID("每日吃叶酸提醒", true, "20:00", null, null),
    DAILY_GROWTH("每日变化推送", true, "09:00", null, null),
    WEEKLY_GROWTH("孕周变化推送", true, "10:00", null, null);

    private final String label;
    private final boolean defaultEnabled;
    private final String defaultTime;
    private final Integer defaultDaysBefore;
    private final Integer defaultWeekday;

    /** 读取当前已保存设置，缺失时使用规则默认值。 */
    public static JSONObject readSettings() {
        String text = ConfigUtils.getString(PregnancyConstants.CONFIG_REMINDER_RULES);
        if (!text.isBlank()) {
            try {
                JSONObject settings = JSON.parseObject(text);
                if (settings != null) return settings;
            } catch (RuntimeException e) {
                log.warn("孕期提醒规则配置不是合法 JSON，按默认值处理");
            }
        }
        return new JSONObject();
    }

    /** 按编码读取规则覆盖值。 */
    private JSONObject findOverride(JSONObject settings) {
        JSONArray rules = settings.getJSONArray("rules");
        if (rules != null) {
            for (int index = rules.size() - 1; index >= 0; index--) {
                JSONObject rule = rules.getJSONObject(index);
                if (rule != null && name().equals(rule.getString("code"))) return rule;
            }
        }
        return new JSONObject();
    }

    /** 合并配置和默认值，旧库缺少新增规则时自动启用默认计划。 */
    public Setting resolve(JSONObject settings) {
        JSONObject rule = findOverride(settings);
        return new Setting(rule.getBooleanValue("enabled", defaultEnabled),
                LocalTime.parse(validTime(rule.getString("time")) ? rule.getString("time") : defaultTime),
                rule.getInteger("daysBefore") == null ? defaultDaysBefore : rule.getInteger("daysBefore"),
                rule.getInteger("weekday") == null ? defaultWeekday : rule.getInteger("weekday"));
    }

    /** 校验设置页面统一的 24 小时时刻格式。 */
    public static boolean validTime(String value) {
        return value != null && value.matches("([01][0-9]|2[0-3]):[0-5][0-9]");
    }

    /** 排程实际生效的提醒设置。 */
    public record Setting(boolean enabled, LocalTime time, Integer daysBefore, Integer weekday) {
    }
}
