package com.howe.common.tool.support;

import com.howe.common.tool.model.ToolPage;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 工具入参校验辅助。
 *
 * <p>平台只发布输入结构，不会自动执行完整 JSON Schema 校验；每个工具必须通过
 * 校验器显式拒绝未知字段、越界值和错误类型。本类型不引用 MCP SDK 类型。</p>
 */
public final class ToolArguments {

    /** 工具入参辅助不允许实例化。 */
    private ToolArguments() {
    }

    /**
     * 拒绝未知字段。
     *
     * @param arguments 实际入参
     * @param allowed 允许的字段名
     */
    public static void rejectUnknown(Map<String, Object> arguments, Set<String> allowed) {
        Objects.requireNonNull(arguments);
        for (String key : arguments.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("不支持的参数：" + key);
            }
        }
    }

    /**
     * 读取可选字符串并校验长度。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @param maxLength 最大长度
     * @return 去除首尾空白后的值；未提供时返回 null
     */
    public static String optionalText(Map<String, Object> arguments, String name, int maxLength) {
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("参数类型不正确：" + name);
        }
        String trimmed = text.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("参数长度超过上限：" + name);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 读取可选取正整数，缺省时返回默认值。
     *
     * <p>数值按 {@code doubleValue() == intValue()} 判定：{@code 1.0} 视为整数 1，
     * 带非零小数部分的值（如 {@code 1.5}）拒绝。</p>
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @param defaultValue 默认值
     * @param minimum 允许的最小值
     * @param maximum 允许的最大值
     * @return 校验后的整数
     */
    public static int optionalInt(Map<String, Object> arguments, String name, int defaultValue, int minimum,
            int maximum) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("参数类型不正确：" + name);
        }
        int result = number.intValue();
        if (result < minimum || result > maximum) {
            throw new IllegalArgumentException("参数超出允许范围：" + name);
        }
        return result;
    }

    /**
     * 读取可选取布尔值，缺省时返回默认值。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 校验后的布尔值
     */
    public static boolean optionalBoolean(Map<String, Object> arguments, String name, boolean defaultValue) {
        Object value = arguments.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException("参数类型不正确：" + name);
        }
        return result;
    }

    /**
     * 读取必填正整数字符串 ID 并转换为 Long。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @return 正数 Long
     */
    public static Long requiredId(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("参数格式不正确：" + name);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("参数超出范围：" + name);
        }
    }

    /**
     * 校验入参必须为空。
     *
     * @param arguments 实际入参，可为 null
     */
    public static void requireEmpty(Map<String, Object> arguments) {
        if (arguments != null && !arguments.isEmpty()) {
            throw new IllegalArgumentException("该工具不接受参数");
        }
    }

    /**
     * 读取必填非空字符串并校验长度。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @param maxLength 最大长度
     * @return 去除首尾空白后的值
     */
    public static String requiredText(Map<String, Object> arguments, String name, int maxLength) {
        String text = optionalText(arguments, name, maxLength);
        if (text == null) {
            throw new IllegalArgumentException("缺少必填参数：" + name);
        }
        return text;
    }

    /**
     * 读取可选枚举字符串。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @param allowed 允许取值
     * @return 校验后的值；未提供时返回 null
     */
    public static String optionalEnum(Map<String, Object> arguments, String name, Set<String> allowed) {
        String value = optionalText(arguments, name, 64);
        if (value == null) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("参数取值不合法：" + name);
        }
        return value;
    }

    /**
     * 读取必填枚举字符串。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @param allowed 允许取值
     * @return 校验后的值
     */
    public static String requiredEnum(Map<String, Object> arguments, String name, Set<String> allowed) {
        String value = optionalEnum(arguments, name, allowed);
        if (value == null) {
            throw new IllegalArgumentException("缺少必填参数：" + name);
        }
        return value;
    }

    /**
     * 读取可选字符串数组，要求元素非空且不重复。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @param maxItems 最大元素数
     * @param maxItemLength 单元素最大长度
     * @return 去重前的校验结果；未提供时返回 null
     */
    public static java.util.List<String> optionalTextList(Map<String, Object> arguments, String name, int maxItems,
            int maxItemLength) {
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof java.util.List<?> list)) {
            throw new IllegalArgumentException("参数类型不正确：" + name);
        }
        if (list.size() > maxItems) {
            throw new IllegalArgumentException("参数元素过多：" + name);
        }
        java.util.List<String> result = new java.util.ArrayList<>(list.size());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank() || text.length() > maxItemLength
                    || text.contains(",")) {
                throw new IllegalArgumentException("参数元素不合法：" + name);
            }
            String trimmed = text.trim();
            if (!seen.add(trimmed)) {
                throw new IllegalArgumentException("参数元素重复：" + name);
            }
            result.add(trimmed);
        }
        return result;
    }

    /**
     * 读取必填正整数版本字符串。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @return 版本值
     */
    public static long requiredVersion(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || !text.matches("(0|[1-9][0-9]{0,18})")) {
            throw new IllegalArgumentException("参数格式不正确：" + name);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("参数超出范围：" + name);
        }
    }

    /**
     * 读取必填操作标识，32 位小写十六进制。
     *
     * @param arguments 实际入参
     * @param name 字段名
     * @return 操作标识
     */
    public static String requiredOperationId(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || !text.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("参数格式不正确：" + name);
        }
        return text;
    }

    /**
     * 构造分页信息，页码与页大小已由调用方完成范围校验。
     *
     * @param page 页码
     * @param pageSize 页大小
     * @param returned 本页实际条数
     * @param total 符合条件的总数
     * @return 分页信息
     */
    public static ToolPage pageOf(int page, int pageSize, int returned, long total) {
        return new ToolPage(page, pageSize, returned, total);
    }
}
