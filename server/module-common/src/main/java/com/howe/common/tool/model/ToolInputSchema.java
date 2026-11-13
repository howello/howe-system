package com.howe.common.tool.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具输入结构。
 *
 * <p>用普通 Map/List 表达 JSON Schema 的六个根字段，不引用 MCP SDK 类型。
 * {@code properties} 内部是原样 JSON 值树，保留 {@code default}、{@code minimum}、
 * {@code maximum}、{@code pattern}、{@code enum}、{@code items} 与嵌套对象等关键字。</p>
 *
 * <p>本类型只描述输入结构，不执行校验；执行时由各工具的校验器显式复验。</p>
 *
 * @param type 根类型，业务工具固定为 {@code object}
 * @param properties 字段定义树
 * @param required 必填字段名，保留顺序
 * @param additionalProperties 是否允许额外字段
 * @param defs 可空的 {@code $defs} 定义
 * @param definitions 可空的 {@code definitions} 定义
 */
public record ToolInputSchema(String type, Map<String, Object> properties, List<String> required,
        Boolean additionalProperties, Map<String, Object> defs, Map<String, Object> definitions) {

    /** 递归防御性复制，保留顺序、数字类型与合法 null。 */
    public ToolInputSchema {
        type = type == null || type.isBlank() ? "object" : type;
        properties = copyMap(properties);
        required = required == null ? List.of() : List.copyOf(required);
        defs = copyMap(defs);
        definitions = copyMap(definitions);
    }

    /**
     * 构造禁止额外字段的空对象输入。
     *
     * @return 空参数输入结构
     */
    public static ToolInputSchema emptyObject() {
        return new ToolInputSchema("object", Map.of(), List.of(), false, null, null);
    }

    /**
     * 递归复制 JSON 值树。
     *
     * <p>不使用 {@code Map.copyOf}：它会拒绝 null 值，而合法 JSON 值树允许 null。
     * 值为 Map 时按插入顺序复制，值为 List 时逐项复制，其余值原样保留。</p>
     *
     * @param source 原始 Map，可为 null
     * @return 复制后的不可变 Map；源为 null 时返回 null
     */
    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    /**
     * 递归复制单个 JSON 值。
     *
     * @param value 原始值
     * @return 复制后的值
     */
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), copyValue(item)));
            return java.util.Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyValue(item)));
            return java.util.Collections.unmodifiableList(copy);
        }
        return value;
    }
}
