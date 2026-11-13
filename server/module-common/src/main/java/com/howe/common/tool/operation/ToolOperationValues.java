package com.howe.common.tool.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作载荷的 JSON 基础类型复制辅助。
 *
 * <p>只允许 String、Number、Boolean、null 及它们的 Map/List 嵌套；其它类型直接拒绝，
 * 防止实体、异常、Supplier 或可变账号对象混入待加密载荷。</p>
 */
final class ToolOperationValues {

    /** 辅助类不允许实例化。 */
    private ToolOperationValues() {
    }

    /**
     * 深度复制 Map。
     *
     * @param source 原始 Map，可为 null
     * @return 不可变副本；源为 null 时返回空 Map
     */
    static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 深度复制单个值。
     *
     * @param value 原始值
     * @return 不可变副本
     */
    static Object copyValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("操作载荷的键必须是字符串");
                }
                copy.put(text, copyValue(item));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyValue(item)));
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("操作载荷只允许 JSON 基础类型");
    }
}
