package com.howe.book.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** 微信读书搜索、详情和导入共享的书目字段转换。 */
final class BookWeReadMetadata {
    static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /** 仅使用静态转换方法。 */
    private BookWeReadMetadata() {}

    /** 将日期字符串或 Unix 时间戳转为出版年份。 */
    static String year(JsonNode info) {
        String value = info.path("publishTime").asText().strip();
        if (value.matches("[12][0-9]{3}(?:[-/].*)?")) {
            return value.substring(0, 4);
        }
        long timestamp = info.path("publishTime").asLong(0);
        if (timestamp > 0 && timestamp < 100_000_000_000_000L) {
            Instant instant = timestamp >= 100_000_000_000L ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
            int year = instant.atZone(TIME_ZONE).getYear();
            return year >= 1000 && year <= 9999 ? Integer.toString(year) : "";
        }
        return "";
    }

    /** 将微信读书的百分制评分转换成书架使用的十分制。 */
    static String rating(JsonNode value) {
        double score = value.asDouble(-1);
        if (score >= 0 && score <= 100) {
            return String.format(Locale.ROOT, "%.1f", score / 10);
        }
        return score > 100 && score <= 1000 ? String.format(Locale.ROOT, "%.1f", score / 100) : "";
    }

    /** 详情中的非空字段优先，保留书架或笔记本提供的补充字段。 */
    static ObjectNode merge(ObjectNode result, JsonNode... sources) {
        for (JsonNode source : sources) {
            source.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (!value.isNull() && (!value.isTextual() || !value.asText().isBlank())) {
                    result.set(entry.getKey(), value);
                }
            });
        }
        return result;
    }

    /** 拆分微信读书书籍分类路径。 */
    static List<String> categories(JsonNode info) {
        return java.util.Arrays.stream(info.path("category").asText().split("[-－>＞]+"))
            .map(String::strip).filter(value -> !value.isEmpty()).toList();
    }
}
