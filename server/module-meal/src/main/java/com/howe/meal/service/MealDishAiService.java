package com.howe.meal.service;

import com.alibaba.fastjson2.JSON;
import com.howe.ai.api.AiClient;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiImage;
import com.howe.ai.api.dto.AiImageRequest;
import com.howe.ai.api.dto.AiImageResult;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.dto.DishAiGenerateRequest;
import com.howe.meal.domain.dto.DishAiGenerateResult;
import com.howe.meal.mapper.MealCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品一键 AI 补齐服务
 *
 * <p>固定流水线编排：先由后端判断缺失字段 → 只把缺失字段交给对话模型（结构化输出）
 * → 分类名只从「当前可用分类」中匹配 → 缺封面时再调文生图。不引入 Agent 编排框架，
 * 模型只负责填空，缺什么的判断与合并都在这里完成。</p>
 *
 * <p>只补缺失、绝不覆盖用户已填字段；生成结果不落库，由前端回填后走原有提交。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealDishAiService {

    /** 对话补齐场景（路由 key） */
    private static final String SCENE_GENERATE = "meal.dish.generate";

    /** 文生图场景（路由 key） */
    private static final String SCENE_COVER = "meal.dish.cover";

    /** 封面风格后缀模板 key，对应 sys_config 的 ai.image.styleSuffix.recipe.photo */
    private static final String STYLE_KEY = "recipe.photo";

    /** 生成内容一律使用简体中文 */
    private static final String SYSTEM_PROMPT = "你是一位资深中餐菜谱编辑，请根据菜名补全菜品信息。"
            + "所有内容使用简体中文：简介精炼（不超过 60 字）；标签、耗时、难度贴合家常做法；"
            + "份量给出人份（如 2 人份）；热量给出每份千卡估值（如 520 千卡）；"
            + "用料给出常见份量的名称与用量；做法步骤清晰可执行；小贴士简洁实用。";

    private final AiClient aiClient;

    private final MealCategoryMapper mealCategoryMapper;

    /**
     * 按菜名补齐当前为空的菜品字段
     *
     * @param request 生成请求（菜名 + 已填字段快照）
     * @return 只含被补齐字段的结果
     */
    public DishAiGenerateResult generate(DishAiGenerateRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ServiceException("请先填写菜名");
        }
        DishAiGenerateRequest.Current current = request.getCurrent();

        // 候选分类：公共分类（dept_id=0）+ 目标家庭的私有分类
        Map<String, Long> categoryIdByName = loadCandidateCategories(resolveDeptId(request.getDeptId()));

        DishAiGenerateResult result = new DishAiGenerateResult();
        result.setFields(new LinkedHashMap<>());
        result.setMatchedCategoryIds(new ArrayList<>());
        result.setUnmatchedCategoryNames(new ArrayList<>());

        boolean needCategories = isBlankIds(current == null ? null : current.getCategoryIds());
        boolean needDescription = isBlank(current == null ? null : current.getDescription());
        boolean needTags = isBlank(current == null ? null : current.getTags());
        boolean needDuration = isBlank(current == null ? null : current.getDuration());
        boolean needLevel = isBlank(current == null ? null : current.getLevel());
        boolean needServe = isBlank(current == null ? null : current.getServe());
        boolean needKcal = isBlank(current == null ? null : current.getKcal());
        boolean needIngredients = isBlank(current == null ? null : current.getIngredients());
        boolean needSteps = isBlank(current == null ? null : current.getSteps());
        boolean needTips = isBlank(current == null ? null : current.getTips());

        // 分类只能从现有分类里选；没有候选分类时不请求分类字段
        boolean requestCategories = needCategories && !categoryIdByName.isEmpty();

        boolean needText = requestCategories || needDescription || needTags || needDuration
                || needLevel || needServe || needKcal || needIngredients || needSteps || needTips;
        if (needText) {
            fillTextFields(result, name, categoryIdByName, requestCategories, needDescription, needTags,
                    needDuration, needLevel, needServe, needKcal, needIngredients, needSteps, needTips);
        }

        if (isBlank(current == null ? null : current.getCover())) {
            fillCover(result, name);
        }
        return result;
    }

    /**
     * 结构化对话一次性补齐缺失的文本字段
     */
    private void fillTextFields(DishAiGenerateResult result, String name, Map<String, Long> categoryIdByName,
            boolean needCategories, boolean needDescription, boolean needTags, boolean needDuration,
            boolean needLevel, boolean needServe, boolean needKcal, boolean needIngredients, boolean needSteps,
            boolean needTips) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (needCategories) {
            properties.put("categories", arrayOfStringSchema());
            required.add("categories");
        }
        if (needDescription) {
            properties.put("description", stringSchema());
            required.add("description");
        }
        if (needTags) {
            properties.put("tags", arrayOfStringSchema());
            required.add("tags");
        }
        if (needDuration) {
            properties.put("duration", stringSchema());
            required.add("duration");
        }
        if (needLevel) {
            properties.put("level", stringSchema());
            required.add("level");
        }
        if (needServe) {
            properties.put("serve", stringSchema());
            required.add("serve");
        }
        if (needKcal) {
            properties.put("kcal", stringSchema());
            required.add("kcal");
        }
        if (needIngredients) {
            properties.put("ingredients", arrayOfIngredientSchema());
            required.add("ingredients");
        }
        if (needSteps) {
            properties.put("steps", arrayOfStringSchema());
            required.add("steps");
        }
        if (needTips) {
            properties.put("tips", arrayOfStringSchema());
            required.add("tips");
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);

        AiChatRequest chatRequest = AiChatRequest.builder()
                .scene(SCENE_GENERATE)
                .system(SYSTEM_PROMPT)
                .prompt(buildPrompt(name, categoryIdByName.keySet(), needCategories))
                .outputSchema(JSON.toJSONString(schema))
                .build();
        AiChatResult chatResult = aiClient.chat(chatRequest);
        Map<String, Object> data = toMap(chatResult.getStructured());

        if (needCategories) {
            for (String categoryName : toStringList(data.get("categories"))) {
                Long categoryId = categoryIdByName.get(categoryName.trim());
                if (categoryId != null) {
                    result.getMatchedCategoryIds().add(categoryId);
                } else {
                    result.getUnmatchedCategoryNames().add(categoryName);
                }
            }
        }
        if (needDescription) {
            putIfPresent(result.getFields(), "description", toText(data.get("description")));
        }
        if (needTags) {
            putIfPresent(result.getFields(), "tags", String.join(",", toStringList(data.get("tags"))));
        }
        if (needDuration) {
            putIfPresent(result.getFields(), "duration", toText(data.get("duration")));
        }
        if (needLevel) {
            putIfPresent(result.getFields(), "level", toText(data.get("level")));
        }
        if (needServe) {
            putIfPresent(result.getFields(), "serve", toText(data.get("serve")));
        }
        if (needKcal) {
            putIfPresent(result.getFields(), "kcal", toText(data.get("kcal")));
        }
        if (needIngredients) {
            putIfPresent(result.getFields(), "ingredients", toJson(data.get("ingredients")));
        }
        if (needSteps) {
            putIfPresent(result.getFields(), "steps", toJson(data.get("steps")));
        }
        if (needTips) {
            putIfPresent(result.getFields(), "tips", toJson(data.get("tips")));
        }
    }

    /**
     * 缺封面时调用文生图；失败只记录可读原因，不影响文本补齐
     */
    private void fillCover(DishAiGenerateResult result, String name) {
        try {
            AiImageResult imageResult = aiClient.image(AiImageRequest.builder()
                    .scene(SCENE_COVER)
                    .prompt(name)
                    .style(STYLE_KEY)
                    .build());
            String url = firstImageUrl(imageResult);
            if (url == null) {
                result.setImageError("图片生成未返回结果");
            } else {
                result.setCover(url);
            }
        } catch (AiException e) {
            result.setImageError(e.getMessage());
        }
    }

    /**
     * 查询候选分类并建立「分类名 → 分类ID」映射（重名取第一个）
     */
    private Map<String, Long> loadCandidateCategories(Long deptId) {
        MealCategory query = new MealCategory();
        query.setDeptId(deptId);
        List<MealCategory> categories = mealCategoryMapper.selectMealCategoryList(query);
        Map<String, Long> categoryIdByName = new LinkedHashMap<>();
        for (MealCategory category : categories) {
            if (category.getName() != null) {
                categoryIdByName.putIfAbsent(category.getName().trim(), category.getCategoryId());
            }
        }
        return categoryIdByName;
    }

    /**
     * 归属家庭：管理员可显式指定（如公共菜谱传 0），其余一律取当前登录家庭
     */
    private Long resolveDeptId(Long requested) {
        if (requested != null && SecurityUtils.isAdmin()) {
            return requested;
        }
        return SecurityUtils.getDeptId();
    }

    private String buildPrompt(String name, Collection<String> categoryNames, boolean withCategories) {
        StringBuilder builder = new StringBuilder();
        builder.append("菜名：").append(name).append("\n");
        if (withCategories) {
            builder.append("可选分类（categories 只能从下列名称中选择，不得自造、不得新增）：")
                    .append(String.join("、", categoryNames)).append("\n");
        }
        builder.append("请补全所需字段。");
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isBlankIds(List<Long> ids) {
        return ids == null || ids.isEmpty();
    }

    private Map<String, Object> stringSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        return schema;
    }

    private Map<String, Object> arrayOfStringSchema() {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "string");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", items);
        return schema;
    }

    private Map<String, Object> arrayOfIngredientSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", stringSchema());
        properties.put("amount", stringSchema());
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "object");
        items.put("properties", properties);
        items.put("required", List.of("name", "amount"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", items);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object structured) {
        if (structured instanceof Map) {
            return (Map<String, Object>) structured;
        }
        return new LinkedHashMap<>();
    }

    private List<String> toStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                addText(list, item);
            }
        } else {
            addText(list, value);
        }
        return list;
    }

    private void addText(List<String> list, Object item) {
        if (item == null) {
            return;
        }
        String text = String.valueOf(item).trim();
        if (!text.isEmpty()) {
            list.add(text);
        }
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        return value == null ? null : JSON.toJSONString(value);
    }

    private void putIfPresent(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isEmpty()) {
            fields.put(key, value);
        }
    }

    private String firstImageUrl(AiImageResult result) {
        if (result == null || result.getImages() == null) {
            return null;
        }
        for (AiImage image : result.getImages()) {
            if (image != null && image.getUrl() != null && !image.getUrl().isEmpty()) {
                return image.getUrl();
            }
        }
        return null;
    }
}
