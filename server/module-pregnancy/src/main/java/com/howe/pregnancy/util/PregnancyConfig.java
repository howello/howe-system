package com.howe.pregnancy.util;

import com.howe.common.utils.ConfigUtils;
import com.howe.pregnancy.constant.PregnancyConstants;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 孕期配置读取
 *
 * <p>末次月经在「系统管理 &gt; 参数设置」维护，孕期任务使用内置默认值。
 * 这里统一封装键名与缺省值，业务代码不直接拼字符串。</p>
 *
 * @author howe
 */
public final class PregnancyConfig {

    /**
     * 工具类不允许实例化
     */
    private PregnancyConfig() {
    }

    /**
     * 读取末次月经日期
     *
     * <p>值必须是 ISO 日期；空值或格式错误都按「未配置」处理，首页显示待设置，
     * 各项录入与查询仍可用。</p>
     *
     * @return 末次月经日期，未配置返回 null
     */
    public static LocalDate lastMenstrualPeriod() {
        String text = ConfigUtils.getString(PregnancyConstants.CONFIG_LAST_MENSTRUAL_PERIOD);
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 根据末次月经推算预产期。
     *
     * @return 末次月经后 40 周，未配置返回 null
     */
    public static LocalDate dueDate() {
        LocalDate start = lastMenstrualPeriod();
        return start == null ? null : start.plusDays(PregnancyConstants.GESTATION_DAYS_AT_DUE);
    }

    /**
     * 读取单文件字节上限
     *
     * @return 上限字节数，默认 50 MiB
     */
    public static long fileMaxBytes() {
        return 50 * 1024 * 1024;
    }

    /**
     * 读取每条记录的图片上限
     *
     * @return 上限张数，默认 20
     */
    public static int fileMaxPerOwner() {
        return 20;
    }

    /**
     * 读取未绑定与解绑文件的保留小时
     *
     * @return 小时数，默认 24
     */
    public static int fileRetainHours() {
        return 24;
    }

    /**
     * 读取导出构建窗口分钟数
     *
     * @return 分钟数，默认 120
     */
    public static int exportBuildWindowMinutes() {
        return 120;
    }

    /**
     * 读取导出 ZIP 下载期小时
     *
     * @return 小时数，默认 24
     */
    public static int exportReadyHours() {
        return 24;
    }

    /**
     * 读取任务自动尝试上限
     *
     * @return 上限，默认 3
     */
    public static int taskMaxAttempts() {
        return 3;
    }

    /**
     * 读取任务租约分钟数
     *
     * @return 分钟数，默认 5
     */
    public static int taskLeaseMinutes() {
        return 5;
    }

    /**
     * 读取提醒晚到窗口分钟数
     *
     * @return 分钟数，默认 120
     */
    public static int reminderLateMinutes() {
        return 120;
    }

    /**
     * 读取孕期企微机器人凭证
     *
     * @return 明文 key，未配置返回空串
     */
    public static String wecomKey() {
        return ConfigUtils.getString(PregnancyConstants.CONFIG_WECOM_KEY);
    }

    /**
     * 读取孕期通知通道总开关
     *
     * @return 启用返回 true
     */
    public static boolean wecomEnabled() {
        return ConfigUtils.getBoolean(PregnancyConstants.CONFIG_WECOM_ENABLED, false);
    }
}
