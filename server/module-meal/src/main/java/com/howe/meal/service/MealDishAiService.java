package com.howe.meal.service;

import com.alibaba.fastjson2.JSON;
import com.howe.ai.api.AiClient;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiImage;
import com.howe.ai.api.dto.AiImageRequest;
import com.howe.ai.api.dto.AiImageResult;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.dto.DishAiAuditResult;
import com.howe.meal.domain.dto.DishAiCoverResult;
import com.howe.meal.domain.dto.DishAiGenerateRequest;
import com.howe.meal.domain.dto.DishAiTextResult;
import com.howe.meal.domain.dto.DishAiUpdateRequest;
import com.howe.meal.domain.dto.DishAiUpdateResult;
import com.howe.meal.mapper.MealCategoryMapper;
import com.howe.meal.service.IMealDishService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品一键 AI 补齐服务
 *
 * <p>拆成「文本」与「封面」两条独立流水线，前端可并行调用：文本补齐先由后端判断缺失字段
 * → 只把缺失字段交给对话模型（结构化输出）→ 分类名只从「当前可用分类」中匹配；
 * 封面则在缺失时调文生图。两条流水线互不依赖，不引入 Agent 编排框架，模型只负责填空。</p>
 *
 * <p>只补缺失、绝不覆盖用户已填字段；生成结果不落库，由前端回填后走原有提交。
 * 另提供 {@link #auditAndComplete} 供新菜提案的 AI 自动审核在无登录态线程中复用同一套补齐逻辑
 * （显式传入家庭 ID，不读 {@code SecurityUtils}）。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealDishAiService {

    /** 对话补齐场景（路由 key） */
    private static final String SCENE_GENERATE = "meal.dish.generate";

    /** 新菜提案 AI 自动审核场景（路由 key），与手动补齐分开配置模型 */
    private static final String SCENE_AUDIT = "meal.dish.audit";

    /** 文生图场景（路由 key） */
    private static final String SCENE_COVER = "meal.dish.cover";

    /** 菜品 AI 更新场景（路由 key）：从完整做法提取字段并覆盖入库 */
    private static final String SCENE_UPDATE = "meal.dish.update";

    /** 封面风格后缀模板 key，对应 sys_config 的 ai.image.styleSuffix.recipe.photo */
    private static final String STYLE_KEY = "recipe.photo";

    /**
     * 文本补齐 system 提示词的缺省值
     *
     * <p>实际取值优先读 sys_config（{@link ConfigConstants#AI_PROMPT_MEAL_DISH_SYSTEM}），
     * 未配置时回退到这里，保证开箱可用。</p>
     */
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一位资深中餐菜谱编辑，请根据菜名补全菜品信息。"
            + "所有内容使用简体中文：简介精炼（不超过 60 字）；标签、耗时、难度贴合家常做法；"
            + "份量给出人份（如 2 人份）；热量给出每份千卡估值（如 520 千卡）；"
            + "用料给出常见份量的名称与用量；做法步骤清晰可执行；小贴士简洁实用。";

    /**
     * AI 自动审核在 system 提示词之后追加的判定说明缺省值
     *
     * <p>实际取值优先读 sys_config（{@link ConfigConstants#AI_PROMPT_MEAL_DISH_AUDIT_SUFFIX}）。</p>
     */
    private static final String DEFAULT_AUDIT_SUFFIX =
            "另外，请先判断该菜名是否为一道可制作的家常菜品：若是，approved 填 \"true\" 并按要求补齐字段；"
            + "若不是可制作的菜品、或内容明显不当，approved 填 \"false\"，rejectReason 用一句中文说明原因，其余字段留空。";

    /**
     * 菜品 AI 更新的 system 提示词缺省值
     *
     * <p>实际取值优先读 sys_config（{@link ConfigConstants#AI_PROMPT_MEAL_DISH_UPDATE_SYSTEM}）。</p>
     */
    private static final String DEFAULT_UPDATE_SYSTEM_PROMPT =
            "你是一位资深中餐菜谱编辑。用户会给出一道菜的完整做法，请从中提取结构化菜品信息。"
            + "所有内容使用简体中文：简介精炼（不超过 60 字）；标签给出 3 到 6 个短词；"
            + "耗时、难度、份量、热量按文本合理推断；用料提取名称与用量；"
            + "做法按步骤拆分并保留原文关键信息；小贴士提取注意事项。"
            + "文本中确实未提到的字段返回空字符串或空数组，不要编造。";

    private final AiClient aiClient;

    private final MealCategoryMapper mealCategoryMapper;

    private final IMealDishService mealDishService;

    /**
     * 按菜名补齐当前为空的文本字段（不含封面）
     *
     * @param request 生成请求（菜名 + 已填字段快照）
     * @return 只含被补齐文本字段与分类匹配结果
     */
    public DishAiTextResult generateText(DishAiGenerateRequest request) {
        return generateTextForDept(resolveName(request), request.getCurrent(), resolveDeptId(request.getDeptId()));
    }

    /**
     * 从用户输入的完整做法中提取菜品文本字段并直接覆盖入库
     *
     * <p>只覆盖模型实际返回的非空字段；缺失或空字段保留数据库原值。
     * 菜名、封面、分类、归属与来源不变。</p>
     *
     * @param request AI 更新请求（菜品 ID + 完整做法文本）
     * @return 本次写入的字段与更新后的菜品
     */
    public DishAiUpdateResult updateFromText(DishAiUpdateRequest request) {
        if (request == null || request.getDishId() == null) {
            throw new ServiceException("请选择要更新的菜品");
        }
        String text = request.getText() == null ? "" : request.getText().trim();
        if (text.isEmpty()) {
            throw new ServiceException("请先输入这道菜的完整做法");
        }

        MealDish exist = mealDishService.selectMealDishById(request.getDishId());
        MealScopeGuard.assertDishWritable(exist.getDeptId(), "菜品");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        appendUpdateSchema(properties);

        AiChatResult chatResult = aiClient.chat(AiChatRequest.builder()
                .scene(SCENE_UPDATE)
                .system(updateSystemPrompt())
                .prompt(buildUpdatePrompt(exist.getName(), text))
                .outputSchema(JSON.toJSONString(objectSchema(properties, required)))
                .build());
        Object structured = chatResult.getStructured();
        if (!(structured instanceof Map)) {
            throw new ServiceException("AI 未返回可解析的菜品信息，请稍后重试");
        }
        Map<String, Object> data = toMap(structured);

        DishAiUpdateResult result = new DishAiUpdateResult();
        result.setFields(new LinkedHashMap<>());

        MealDish update = new MealDish();
        update.setDishId(exist.getDishId());
        fillUpdateField(update, result.getFields(), "description", toText(data.get("description")));
        fillUpdateField(update, result.getFields(), "tags", String.join(",", toStringList(data.get("tags"))));
        fillUpdateField(update, result.getFields(), "duration", toText(data.get("duration")));
        fillUpdateField(update, result.getFields(), "level", toText(data.get("level")));
        fillUpdateField(update, result.getFields(), "serve", toText(data.get("serve")));
        fillUpdateField(update, result.getFields(), "kcal", toText(data.get("kcal")));
        fillUpdateField(update, result.getFields(), "ingredients", nonEmptyJson(data.get("ingredients")));
        fillUpdateField(update, result.getFields(), "steps", nonEmptyJson(data.get("steps")));
        fillUpdateField(update, result.getFields(), "tips", nonEmptyJson(data.get("tips")));

        if (!result.getFields().isEmpty()) {
            mealDishService.updateMealDish(update);
        }
        result.setDish(mealDishService.selectMealDishById(exist.getDishId()));
        return result;
    }

    /**
     * 按菜名与家庭 ID 补齐当前为空的文本字段（不依赖登录态，供异步线程复用）
     *
     * @param name    菜名
     * @param current 当前已填字段快照
     * @param deptId  归属家庭 ID，决定可用分类候选
     * @return 只含被补齐文本字段与分类匹配结果
     */
    public DishAiTextResult generateTextForDept(String name, DishAiGenerateRequest.Current current, Long deptId) {
        Map<String, Long> categoryIdByName = loadCandidateCategories(deptId);
        DishAiTextResult result = emptyTextResult();
        Missing missing = detectMissing(current);
        // 分类只能从现有分类里选；没有候选分类时不请求分类字段
        boolean requestCategories = missing.categories && !categoryIdByName.isEmpty();

        boolean needText = requestCategories || missing.description || missing.tags || missing.duration
                || missing.level || missing.serve || missing.kcal || missing.ingredients || missing.steps
                || missing.tips;
        if (needText) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            appendFieldSchema(missing, requestCategories, properties, required);

            AiChatRequest chatRequest = AiChatRequest.builder()
                    .scene(SCENE_GENERATE)
                    .system(systemPrompt())
                    .prompt(buildPrompt(name, categoryIdByName.keySet(), requestCategories))
                    .outputSchema(JSON.toJSONString(objectSchema(properties, required)))
                    .build();
            AiChatResult chatResult = aiClient.chat(chatRequest);
            extractFields(result, toMap(chatResult.getStructured()), missing, requestCategories, categoryIdByName);
        }
        return result;
    }

    /**
     * AI 自动审核：一次结构化调用同时完成「判定是否可制作」与「补齐缺失字段」
     *
     * @param name    菜名
     * @param current 当前已填字段快照
     * @param deptId  归属家庭 ID，决定可用分类候选
     * @return 判定结果；通过时携带补齐的字段与分类匹配结果
     */
    public DishAiAuditResult auditAndComplete(String name, DishAiGenerateRequest.Current current, Long deptId) {
        Map<String, Long> categoryIdByName = loadCandidateCategories(deptId);
        Missing missing = detectMissing(current);
        boolean requestCategories = missing.categories && !categoryIdByName.isEmpty();

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        // 判定用字符串而非布尔：部分 OpenAI 兼容网关对 schema 中的 boolean/number 属性会返回空内容
        properties.put("approved", stringSchema("是否通过：通过填 \"true\"，驳回填 \"false\""));
        required.add("approved");
        properties.put("rejectReason", stringSchema());
        required.add("rejectReason");
        appendFieldSchema(missing, requestCategories, properties, required);

        AiChatRequest chatRequest = AiChatRequest.builder()
                .scene(SCENE_AUDIT)
                .system(auditPrompt())
                .prompt(buildAuditPrompt(name, categoryIdByName.keySet(), requestCategories))
                .outputSchema(JSON.toJSONString(objectSchema(properties, required)))
                .build();
        AiChatResult chatResult = aiClient.chat(chatRequest);
        Object structured = chatResult.getStructured();
        if (!(structured instanceof Map)) {
            // 网关未按 schema 返回（如返回空内容）时按通过处理，只落已返回的字段，避免误拦截上架
            log.warn("新菜提案 AI 自动审核未返回可解析的结构化结果，按通过处理并只落已返回字段：raw={}",
                    abbreviate(chatResult.getText()));
        }
        Map<String, Object> data = toMap(structured);

        DishAiAuditResult audit = new DishAiAuditResult();
        audit.setApproved(toBoolean(data.get("approved")));
        audit.setRejectReason(toText(data.get("rejectReason")));
        DishAiTextResult text = emptyTextResult();
        if (audit.isApproved()) {
            extractFields(text, data, missing, requestCategories, categoryIdByName);
        }
        audit.setText(text);
        return audit;
    }

    /**
     * 按菜名生成封面（已填封面时不生成）
     *
     * @param request 生成请求（菜名 + 已填字段快照）
     * @return 封面永久地址或失败原因
     */
    public DishAiCoverResult generateCover(DishAiGenerateRequest request) {
        String name = resolveName(request);
        DishAiGenerateRequest.Current current = request.getCurrent();

        DishAiCoverResult result = new DishAiCoverResult();
        if (isBlank(current == null ? null : current.getCover())) {
            fillCover(result, name);
        }
        return result;
    }

    /**
     * 校验并取出菜名
     *
     * @param request 生成请求
     * @return 去掉首尾空白的菜名
     */
    private String resolveName(DishAiGenerateRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ServiceException("请先填写菜名");
        }
        return name;
    }

    /**
     * 按缺失字段组装 JSON Schema 的 properties 与 required
     *
     * @param missing           缺失字段判定
     * @param requestCategories 本次是否请求分类
     * @param properties        Schema properties 输出
     * @param required          Schema required 输出
     */
    private void appendFieldSchema(Missing missing, boolean requestCategories, Map<String, Object> properties,
            List<String> required) {
        if (requestCategories) {
            properties.put("categories", arrayOfStringSchema());
            required.add("categories");
        }
        if (missing.description) {
            properties.put("description", stringSchema());
            required.add("description");
        }
        if (missing.tags) {
            properties.put("tags", arrayOfStringSchema());
            required.add("tags");
        }
        if (missing.duration) {
            properties.put("duration", stringSchema());
            required.add("duration");
        }
        if (missing.level) {
            properties.put("level", stringSchema());
            required.add("level");
        }
        if (missing.serve) {
            properties.put("serve", stringSchema());
            required.add("serve");
        }
        if (missing.kcal) {
            properties.put("kcal", stringSchema());
            required.add("kcal");
        }
        if (missing.ingredients) {
            properties.put("ingredients", arrayOfIngredientSchema());
            required.add("ingredients");
        }
        if (missing.steps) {
            properties.put("steps", arrayOfStringSchema());
            required.add("steps");
        }
        if (missing.tips) {
            properties.put("tips", arrayOfStringSchema());
            required.add("tips");
        }
    }

    /**
     * 把模型返回的数据按缺失字段抽取到结果（分类只匹配现有分类，未命中的单列）
     *
     * @param result            结果输出
     * @param data              模型返回的结构化数据
     * @param missing           缺失字段判定
     * @param requestCategories 本次是否请求分类
     * @param categoryIdByName  分类名 → 分类 ID 映射
     */
    private void extractFields(DishAiTextResult result, Map<String, Object> data, Missing missing,
            boolean requestCategories, Map<String, Long> categoryIdByName) {
        if (requestCategories) {
            for (String categoryName : toStringList(data.get("categories"))) {
                Long categoryId = categoryIdByName.get(categoryName.trim());
                if (categoryId != null) {
                    result.getMatchedCategoryIds().add(categoryId);
                } else {
                    result.getUnmatchedCategoryNames().add(categoryName);
                }
            }
        }
        if (missing.description) {
            putIfPresent(result.getFields(), "description", toText(data.get("description")));
        }
        if (missing.tags) {
            putIfPresent(result.getFields(), "tags", String.join(",", toStringList(data.get("tags"))));
        }
        if (missing.duration) {
            putIfPresent(result.getFields(), "duration", toText(data.get("duration")));
        }
        if (missing.level) {
            putIfPresent(result.getFields(), "level", toText(data.get("level")));
        }
        if (missing.serve) {
            putIfPresent(result.getFields(), "serve", toText(data.get("serve")));
        }
        if (missing.kcal) {
            putIfPresent(result.getFields(), "kcal", toText(data.get("kcal")));
        }
        if (missing.ingredients) {
            putIfPresent(result.getFields(), "ingredients", toJson(data.get("ingredients")));
        }
        if (missing.steps) {
            putIfPresent(result.getFields(), "steps", toJson(data.get("steps")));
        }
        if (missing.tips) {
            putIfPresent(result.getFields(), "tips", toJson(data.get("tips")));
        }
    }

    /**
     * 缺封面时调用文生图；失败只记录可读原因，不影响文本补齐
     */
    private void fillCover(DishAiCoverResult result, String name) {
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
     * 组装 AI 更新的 JSON Schema：请求全部文本字段，允许模型对未提及字段返回空
     *
     * @param properties Schema properties 输出
     */
    private void appendUpdateSchema(Map<String, Object> properties) {
        properties.put("description", stringSchema());
        properties.put("tags", arrayOfStringSchema());
        properties.put("duration", stringSchema());
        properties.put("level", stringSchema());
        properties.put("serve", stringSchema());
        properties.put("kcal", stringSchema());
        properties.put("ingredients", arrayOfIngredientSchema());
        properties.put("steps", arrayOfStringSchema());
        properties.put("tips", arrayOfStringSchema());
    }

    /**
     * 读取 AI 更新的 system 提示词（可在「参数设置」修改，未配置时用缺省值）
     */
    private String updateSystemPrompt() {
        return ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_DISH_UPDATE_SYSTEM,
                DEFAULT_UPDATE_SYSTEM_PROMPT);
    }

    /**
     * 组装 AI 更新的用户提示词
     *
     * @param name 菜名，帮助模型定位菜品
     * @param text 用户输入的完整做法
     * @return 提示词
     */
    private String buildUpdatePrompt(String name, String text) {
        return "菜名：" + name + "\n"
                + "完整做法：\n" + text + "\n"
                + "请从中提取菜品信息；文本未提到的字段返回空字符串或空数组。";
    }

    /**
     * 把非空的提取结果写入更新对象与本次写入字段集合
     *
     * <p>空字符串、空数组不写入，从而保留数据库原值。</p>
     *
     * @param update 更新对象
     * @param fields 本次实际写入字段集合
     * @param key    字段名
     * @param value  提取结果
     */
    private void fillUpdateField(MealDish update, Map<String, Object> fields, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        switch (key) {
            case "description" -> update.setDescription(value);
            case "tags" -> update.setTags(value);
            case "duration" -> update.setDuration(value);
            case "level" -> update.setLevel(value);
            case "serve" -> update.setServe(value);
            case "kcal" -> update.setKcal(value);
            case "ingredients" -> update.setIngredients(value);
            case "steps" -> update.setSteps(value);
            case "tips" -> update.setTips(value);
            default -> {
                return;
            }
        }
        fields.put(key, value);
    }

    /**
     * 把数组结果序列化为 JSON 文本
     *
     * <p>只接受集合：空集合、非集合（模型未按 schema 返回）一律返回 null，
     * 避免写入非法 JSON 或覆盖原值。</p>
     *
     * @param value 模型返回的字段值
     * @return JSON 文本或 null
     */
    private String nonEmptyJson(Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(value);
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

    /**
     * 读取文本补齐的 system 提示词（可在「参数设置」修改，未配置时用缺省值）
     */
    private String systemPrompt() {
        return ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_DISH_SYSTEM, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * 读取自动审核的 system 提示词：在补齐提示词之后追加判定说明
     */
    private String auditPrompt() {
        return systemPrompt() + ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_DISH_AUDIT_SUFFIX, DEFAULT_AUDIT_SUFFIX);
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

    private String buildAuditPrompt(String name, Collection<String> categoryNames, boolean withCategories) {
        return buildPrompt(name, categoryNames, withCategories)
                + "同时给出 approved 判定；不通过时填写 rejectReason。";
    }

    private DishAiTextResult emptyTextResult() {
        DishAiTextResult result = new DishAiTextResult();
        result.setFields(new LinkedHashMap<>());
        result.setMatchedCategoryIds(new ArrayList<>());
        result.setUnmatchedCategoryNames(new ArrayList<>());
        return result;
    }

    /**
     * 判定当前表单哪些字段为空（决定本次要向模型请求哪些字段）
     */
    private Missing detectMissing(DishAiGenerateRequest.Current current) {
        Missing missing = new Missing();
        missing.categories = isBlankIds(current == null ? null : current.getCategoryIds());
        missing.description = isBlank(current == null ? null : current.getDescription());
        missing.tags = isBlank(current == null ? null : current.getTags());
        missing.duration = isBlank(current == null ? null : current.getDuration());
        missing.level = isBlank(current == null ? null : current.getLevel());
        missing.serve = isBlank(current == null ? null : current.getServe());
        missing.kcal = isBlank(current == null ? null : current.getKcal());
        missing.ingredients = isBlank(current == null ? null : current.getIngredients());
        missing.steps = isBlank(current == null ? null : current.getSteps());
        missing.tips = isBlank(current == null ? null : current.getTips());
        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isBlankIds(List<Long> ids) {
        return ids == null || ids.isEmpty();
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> stringSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        return schema;
    }

    private Map<String, Object> stringSchema(String description) {
        Map<String, Object> schema = stringSchema();
        schema.put("description", description);
        return schema;
    }

    /** 截断日志里的模型原始输出，避免把长文本整段写进日志 */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
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

    /**
     * 解析布尔判定：模型可能返回布尔或字符串；缺失时按通过处理，避免误拦截上架
     */
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return true;
        }
        String text = String.valueOf(value).trim();
        return !"false".equalsIgnoreCase(text);
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

    /**
     * 当前表单缺失字段判定结果
     */
    private static final class Missing {
        private boolean categories;
        private boolean description;
        private boolean tags;
        private boolean duration;
        private boolean level;
        private boolean serve;
        private boolean kcal;
        private boolean ingredients;
        private boolean steps;
        private boolean tips;
    }
}
