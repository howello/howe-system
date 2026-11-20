package com.howe.pregnancy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancySeedProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 从随模块发布的素材查询孕期参考资料。 */
@Service
@RequiredArgsConstructor
public class PregContentService {

    private static final Set<String> RATINGS = Set.of("RECOMMENDED", "ALLOWED", "LIMIT", "CAUTION", "AVOID");

    private final PregnancyAccessGuard accessGuard;
    private final PregnancySeedProvider contentProvider;

    /** 按实际孕周读取当周全部段落和配图。 */
    public JsonNode week(int week) {
        accessGuard.requireReadable();
        JsonNode root = contentProvider.growth();
        JsonNode result = root == null ? null : root.path("weeks").path(Integer.toString(week));
        if (result == null || result.isMissingNode()) {
            throw PregnancyException.notFound();
        }
        return result;
    }

    /** 按食物名称、别名和标签检索，只返回当前页的列表字段。 */
    public FoodPage foods(String keyword, String rating, int page, int size) {
        accessGuard.requireReadable();
        if (page < 1 || size < 1 || size > 60 || (keyword != null && keyword.length() > 80)
                || (rating != null && !RATINGS.contains(rating))) {
            throw PregnancyException.invalid("食物筛选条件不正确");
        }
        String query = keyword == null ? "" : normalize(keyword);
        long offset = (long) (page - 1) * size;
        List<FoodCard> rows = new ArrayList<>();
        int total = 0;
        for (JsonNode food : foodContent()) {
            if ((rating != null && !rating.equals(food.path("rating").asText())) || !matches(food, query)) {
                continue;
            }
            if (total >= offset && rows.size() < size) {
                rows.add(new FoodCard(food.path("id").asText(), food.path("name").asText(),
                        food.path("rating").asText(), food.path("tags"), food.path("thumbnailUrl").asText()));
            }
            total++;
        }
        return new FoodPage(rows, total);
    }

    /** 获取单个食物资料，列表来源缺少的字段随原数据省略。 */
    public JsonNode food(String id) {
        accessGuard.requireReadable();
        for (JsonNode food : foodContent()) {
            if (id.equals(food.path("id").asText())) {
                return food;
            }
        }
        throw PregnancyException.notFound();
    }

    /** 读取食物表，无法加载时返回可重试的服务异常。 */
    private JsonNode foodContent() {
        JsonNode content = contentProvider.foods();
        if (content == null || !content.isArray()) {
            throw new PregnancyException("PREG_CONTENT_UNAVAILABLE", "暂无法读取食物资料", 503);
        }
        return content;
    }

    /** 判断该食物是否匹配当前关键词。 */
    private static boolean matches(JsonNode food, String query) {
        if (query.isEmpty() || normalize(food.path("name").asText()).contains(query)
                || normalize(food.path("aliases").asText()).contains(query)) {
            return true;
        }
        for (JsonNode tag : food.path("tags")) {
            if (normalize(tag.asText()).contains(query)) {
                return true;
            }
        }
        return false;
    }

    /** 统一搜索文本的全角字符、空格与大小写。 */
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }

    /** 食物列表分页结果。 */
    @Schema(description = "食物列表分页")
    public record FoodPage(List<FoodCard> rows, int total) {}

    /** 食物列表摘要。 */
    @Schema(description = "食物列表摘要")
    public record FoodCard(String id, String name, String rating, JsonNode tags, String thumbnailUrl) {}
}
