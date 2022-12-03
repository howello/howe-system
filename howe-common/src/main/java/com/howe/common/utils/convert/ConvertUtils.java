package com.howe.common.utils.convert;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/28 17:46 星期一
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public class ConvertUtils {

    /**
     * Obj转换为string，
     * 转换失败把或者null时，返回null
     *
     * @param obj
     * @return
     */
    public static String toStr(Object obj) {
        return ConvertUtils.toStr(obj, null);
    }

    /**
     * Obj转换为string，
     * 转换失败或者null时，返回默认值
     *
     * @param obj
     * @param defaultValue
     * @return
     */
    public static String toStr(Object obj, String defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof String) {
            return String.valueOf(obj);
        }
        return obj.toString();
    }

    /**
     * Object 转 Integer
     *
     * @param obj
     * @return
     */
    public static Integer toInt(Object obj) {
        return ConvertUtils.toInt(obj, null);
    }

    /**
     * Object 转 Integer  带默认值
     *
     * @param obj
     * @param defaultValue
     * @return
     */
    public static Integer toInt(Object obj, Integer defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Integer) {
            return (Integer) obj;
        }

        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }

        String s = ConvertUtils.toStr(obj, null);
        if (StringUtils.isBlank(s)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    /**
     * 转换为boolean<br>
     * String支持的值为：true、false、yes、ok、no，1,0 如果给定的值为空，或者转换失败，返回默认值<br>
     * 转换失败不会报错
     *
     * @param value        被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    public static Boolean toBool(Object value, Boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String valueStr = toStr(value, null);
        if (StringUtils.isEmpty(valueStr)) {
            return defaultValue;
        }
        valueStr = valueStr.trim().toLowerCase();
        switch (valueStr) {
            case "true":
                return true;
            case "false":
                return false;
            case "yes":
                return true;
            case "ok":
                return true;
            case "no":
                return false;
            case "1":
                return true;
            case "0":
                return false;
            default:
                return defaultValue;
        }
    }

    /**
     * 转换为boolean<br>
     * 如果给定的值为空，或者转换失败，返回默认值<code>null</code><br>
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    public static Boolean toBool(Object value) {
        return toBool(value, null);
    }
}
