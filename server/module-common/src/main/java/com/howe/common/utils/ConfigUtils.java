package com.howe.common.utils;

import com.howe.common.config.ConfigProvider;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.spring.SpringUtils;

/**
 * 参数配置工具类
 *
 * <p>
 * 统一从「系统管理 &gt; 参数设置」读取配置，避免把可变配置写死在 yml 里。
 * 底层 {@code selectConfigByKey} 走 Redis 缓存，可以放心高频调用；
 * 在参数设置页改完值即时生效，不需要重启服务。
 * </p>
 *
 * @author howe
 */
public class ConfigUtils
{
    /**
     * 读取字符串配置
     *
     * @param configKey 参数键名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public static String getString(String configKey, String defaultValue)
    {
        String value = getRaw(configKey);
        return StringUtils.isEmpty(value) ? defaultValue : value.trim();
    }

    /**
     * 读取字符串配置，缺省为空串
     *
     * @param configKey 参数键名
     * @return 参数值
     */
    public static String getString(String configKey)
    {
        return getString(configKey, StringUtils.EMPTY);
    }

    /**
     * 读取必填配置，缺失时抛出可读的业务异常
     *
     * @param configKey 参数键名
     * @param description 参数含义，用于错误提示
     * @return 参数值
     */
    public static String getRequired(String configKey, String description)
    {
        String value = getString(configKey);
        if (StringUtils.isEmpty(value))
        {
            throw new ServiceException("参数未配置：" + description + "（系统管理 > 参数设置，键名 " + configKey + "）");
        }
        return value;
    }

    /**
     * 读取布尔配置
     *
     * @param configKey 参数键名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public static boolean getBoolean(String configKey, boolean defaultValue)
    {
        String value = getString(configKey);
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "Y".equalsIgnoreCase(value);
    }

    /**
     * 读取整数配置
     *
     * @param configKey 参数键名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public static int getInt(String configKey, int defaultValue)
    {
        String value = getString(configKey);
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    /**
     * 读取浮点配置
     *
     * @param configKey 参数键名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public static double getDouble(String configKey, double defaultValue)
    {
        String value = getString(configKey);
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        try
        {
            return Double.parseDouble(value);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    /**
     * 取原始值
     *
     * <p>
     * 容器尚未就绪时（例如启动早期）静默返回空串，交由调用方走缺省值，
     * 避免因为拿不到配置直接把启动过程打断。
     * </p>
     */
    private static String getRaw(String configKey)
    {
        try
        {
            return SpringUtils.getBean(ConfigProvider.class).getConfigValue(configKey);
        }
        catch (Exception e)
        {
            return StringUtils.EMPTY;
        }
    }
}
