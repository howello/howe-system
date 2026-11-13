package com.howe.meal.tool.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 点餐工具的结构化字段解析辅助。
 *
 * <p>库中 {@code ingredients}、{@code steps}、{@code tips} 三列都是 JSON 文本。
 * 工具只解析白名单结构：用料仅 {@code name}/{@code amount}，做法与小贴士仅字符串数组；
 * 其余键一律丢弃，不把原始 JSON 透传给客户端。</p>
 */
public final class MealStructuredFields {

    /** 辅助类不允许实例化。 */
    private MealStructuredFields() {
    }

    /**
     * 解析用料数组。
     *
     * @param raw 原始 JSON 文本
     * @return 白名单结构；为空或非法时返回空数组
     */
    public static List<Map<String, Object>> ingredients(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(raw);
            if (array == null) {
                return List.of();
            }
            List<Map<String, Object>> values = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                JSONObject item = array.getJSONObject(index);
                if (item == null) {
                    continue;
                }
                String name = item.getString("name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                values.add(Map.of("name", name, "amount", item.getString("amount") == null
                        ? "" : item.getString("amount")));
            }
            return values;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /**
     * 解析字符串数组。
     *
     * @param raw 原始 JSON 文本
     * @return 非空字符串数组；非法时返回空数组
     */
    public static List<String> textArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(raw);
            if (array == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                String value = array.getString(index);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
            return values;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /**
     * 把逗号分隔文本转为数组。
     *
     * @param raw 逗号分隔文本
     * @return 去空后的数组
     */
    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
