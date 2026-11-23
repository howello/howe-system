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
import com.howe.meal.constant.MealCategoryTaxonomy;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.dto.DishAiAuditResult;
import com.howe.meal.domain.dto.DishAiCoverResult;
import com.howe.meal.domain.dto.DishAiGenerateRequest;
import com.howe.meal.domain.dto.DishAiTextResult;
import com.howe.meal.domain.dto.DishAiUpdateRequest;
import com.howe.meal.mapper.MealCategoryMapper;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.service.MealDishAiTaskService.Outcome;
import com.howe.meal.util.MealCategoryGuard;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜品一键 AI 补齐服务
 *
 * <p>拆成「文本」与「封面」两条独立流水线，前端可并行调用：文本补齐先由后端判断缺失字段
 * → 只把缺失字段交给对话模型（结构化输出）→ 分类只从该归属可见的启用分类中匹配；
 * 封面则在缺失时调文生图。两条流水线互不依赖，不引入 Agent 编排框架，模型只负责填空。</p>
 *
 * <p>只补缺失、绝不覆盖用户已填字段；生成结果不落库，由前端回填后走原有提交。
 * 另提供 {@link #auditAndComplete} 供新菜提案的 AI 自动审核在无登录态线程中复用同一套补齐逻辑
 * （显式传入家庭 ID，不读 {@code SecurityUtils}）。</p>
 *
 * <p>菜品编辑页的「AI 更新」通过 {@link #submitUpdateFromText} 提交到业务线程池执行：
 * 请求线程只校验权限并立即返回；异步落库使用 {@link MealDishMapper} 直接更新，不读取登录态。</p>
 *
 * <p>分类规则（公共分类定义与判定顺序）由 {@link MealCategoryTaxonomy} 固定追加到提示词，
 * 不随 {@code sys_config} 变化；AI 只能返回其中一个主类名称。</p>
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

    /** 菜品 AI 更新场景（路由 key）：根据用户意图调整已保存的菜谱。 */
    private static final String SCENE_UPDATE = "meal.dish.update";

    /** AI 更新写入的操作人标识 */
    private static final String AI_UPDATER = "AI 自动更新";

    /** 封面风格后缀模板 key，对应 sys_config 的 ai.image.styleSuffix.recipe.photo */
    private static final String STYLE_KEY = "recipe.photo";

    /** 菜名相似度阈值：归一化后字符集合 Jaccard 达到该值即视为同一道菜 */
    private static final double DUPLICATE_SIMILARITY = 0.8D;

    /** 参与模糊判重的最短菜名长度，过短的菜名容易误判 */
    private static final int DUPLICATE_MIN_NAME_LENGTH = 4;

    /** 菜名常见同义词，归一化时统一写法（番茄→西红柿、土豆/马铃薯→洋芋、鸡蛋→蛋） */
    private static final Map<String, String> DISH_NAME_SYNONYMS = linkedSynonyms();

    /**
     * 构造同义词表（保持插入顺序，长词在前避免被短词提前替换）
     *
     * @return 同义词映射
     */
    private static Map<String, String> linkedSynonyms() {
        Map<String, String> synonyms = new LinkedHashMap<>();
        synonyms.put("西红柿", "西红柿");
        synonyms.put("番茄", "西红柿");
        synonyms.put("马铃薯", "洋芋");
        synonyms.put("土豆", "洋芋");
        synonyms.put("鸡蛋", "蛋");
        return synonyms;
    }

    /**
     * 文本补齐 system 提示词的缺省值
     *
     * <p>实际取值优先读 sys_config（{@link ConfigConstants#AI_PROMPT_MEAL_DISH_SYSTEM}），
     * 未配置时回退到这里，保证开箱可用。分类规则由
     * {@link com.howe.meal.constant.MealCategoryTaxonomy#promptFragment()} 固定追加，不受配置影响。</p>
     */
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一位资深中餐菜谱编辑，请根据菜名补全菜品信息。"
            + "所有内容使用简体中文：简介精炼（不超过 60 字）；标签、耗时、难度贴合家常做法；"
            + "份量给出人份（如 2 人份）；热量给出每份千卡估值（如 520 千卡）；"
            + "用料给出常见份量的名称与用量；做法步骤清晰可执行；小贴士简洁实用。";

    /**
     * 用户备注的固定使用规则
     *
     * <p>备注里可能包含完整菜谱、指定厨具、必须添加或不能添加的食材。
     * 该规则不随 sys_config 覆盖，避免自定义提示词把用户的硬性要求丢掉。</p>
     */
    private static final String USER_NOTES_RULE =
            "用户备注是本次制作的硬性依据：若备注包含菜谱、用量、厨具、火候、时长或“必须加/不要加”等要求，"
            + "生成简介、用料、做法和小贴士时必须优先遵循，不得遗漏、改写为相反要求或编造冲突内容；"
            + "备注原文只作为解析依据，不能把整段原文直接复制到任一字段；应提炼成结构化内容，"
            + "其中小贴士只保留简短、可执行的注意事项。备注没有提到的部分再按家常做法合理补全。";

    /**
     * AI 自动审核在 system 提示词之后追加的判定说明缺省值
     *
     * <p>实际取值优先读 sys_config（{@link ConfigConstants#AI_PROMPT_MEAL_DISH_AUDIT_SUFFIX}）。</p>
     */
    private static final String DEFAULT_AUDIT_SUFFIX =
            "另外，请先判断该菜名是否为一道可制作的家常菜品：若是，approved 填 \"true\" 并按要求补齐字段；"
            + "若不是可制作的菜品、或内容明显不当，approved 填 \"false\"，rejectReason 用一句中文说明原因，其余字段留空。";

    /**
     * 自动审核的判重说明（固定追加，不随 sys_config 覆盖）
     *
     * <p>判重属于业务红线：即使库里的审核提示词被改过，也必须让模型结合已有菜品判断是否重复。</p>
     */
    private static final String AUDIT_DUPLICATE_SUFFIX =
            "如果待审核菜名与「已有菜品」中的某道菜本质上是同一道菜（只是叫法、写法、别名或加料略有不同，"
            + "例如「西红柿炒鸡蛋」与「番茄炒蛋」「西红柿炒蛋」），也必须 approved 填 \"false\"，"
            + "rejectReason 写明与哪道已有菜品重复，避免重复上架。";

    /** 在一次调用中确定编辑范围并生成可直接保存的菜谱字段。 */
    private static final String UPDATE_RECIPE_RULES = """
            你是一位中餐菜谱编辑。读取已保存的菜谱和用户输入，直接返回简体中文 JSON。
            自行判断 mode，无需询问用户选择，也不输出分析过程：
            - replace：用户提供了完整的新菜谱。用新菜谱替换原菜谱，保留原文的用量、火候、时间、顺序和注意事项。
              用户附带的增删要求也要执行。原文没给的精确数值不编造，用量可留空，缺失的可选信息用空值或省略。
            - revise：用户提出局部改动、指出某处不对，或要求把现有做法写清楚。没有明确要求重写时优先采用。
              只返回需要修改的字段。数组字段要返回修改后的完整列表，并保留列表里其他条目及顺序。
              删掉某食材时同步调整相关备料、下锅步骤和小贴士；改火候或顺序时同步调整受影响的步骤和耗时。
              字段省略或 null 表示沿用原值。只有用户确实要清空时才返回空字符串或空数组。
            - rewrite：用户明确给出整道菜的新思路或要求重写。保留用户指定的食材、禁忌、方法和顺序，补齐完整菜谱。
              补出的份量、用量或时间用「建议」「约」等标明，热量没有可靠依据时留空。
            - unchanged：输入无关、已有菜谱已符合要求，或缺少足够信息进行任何确定的改动。只返回 mode 和简短说明。
            replace 和 rewrite 要同时返回有效的 ingredients 和 steps；未提及的可选字段不会沿用旧菜谱。
            步骤按备料、下锅和出锅顺序写。对需要补写的关键步骤，写清食材怎么处理、什么火、
            做多久或看到什么状态再继续。只在有帮助时拆步；用料应与做法对得上。
            description 不超过 60 字；summary 用一句话说清结果或未修改的原因，不超过 60 字。
            菜名、封面和分类由现有编辑区管理。
            """;

    private final AiClient aiClient;

    private final MealCategoryMapper mealCategoryMapper;

    private final MealCategoryGuard mealCategoryGuard;

    private final IMealDishService mealDishService;

    private final MealDishMapper mealDishMapper;

    private final IMealWeComNotifyService mealWeComNotifyService;
    /** 独立任务受理和结果查询。 */
    private final MealDishAiTaskService aiTaskService;

    /**
     * 按菜名补齐当前为空的文本字段（不含封面）
     *
     * @param request 生成请求（菜名 + 已填字段快照）
     * @return 只含被补齐文本字段与唯一主类匹配结果
     */
    public DishAiTextResult generateText(DishAiGenerateRequest request) {
        return generateTextForDept(resolveName(request), request.getCurrent(), resolveDeptId(request.getDeptId()));
    }

    /**
     * 提交一次异步 AI 更新：校验在请求线程完成，生成与落库在线程池执行
     *
     * <p>请求线程只做参数校验、菜品可见性与写权限校验，随后立即返回；AI 调用与数据库更新
     * 在独立线程池中进行。调用者身份传播到任务线程供统一 AI 配额归属使用；
     * 落库仍使用 {@link MealDishMapper}，操作人记为 {@link #AI_UPDATER}。
     * 任务池不可用或已满时明确拒绝，不退化为同步调用。</p>
     *
     * @param request AI 更新请求（菜品 ID + 新菜谱或修改要求）
     * @return 后台任务 ID
     */
    public String submitUpdateFromText(DishAiUpdateRequest request) {
        if (request == null || request.getDishId() == null) {
            throw new ServiceException("请选择要更新的菜品");
        }
        String text = request.getText() == null ? "" : request.getText().trim();
        if (text.isEmpty()) {
            throw new ServiceException("请粘贴菜谱或描述你想怎么改");
        }

        // 请求线程内完成可见性与写权限校验，异步线程不再访问登录态
        MealDish exist = mealDishService.selectMealDishById(request.getDishId());
        MealScopeGuard.assertDishWritable(exist.getDeptId(), "菜品");

        Long dishId = exist.getDishId();
        // 异步线程没有登录态：操作人与家庭归属在请求线程内捕获。
        // 通知发到操作人所在家庭；公共菜（deptId=0）没有自己的家庭群。
        Long operatorId = SecurityUtils.getUserId();
        Long operatorDeptId = SecurityUtils.getDeptId();
        Long deptId = operatorDeptId != null ? operatorDeptId : exist.getDeptId();
        if (text.length() > 30000) {
            throw new ServiceException("更新内容不能超过30000个字符");
        }
        return aiTaskService.submit(dishId, () -> applyUpdateFromText(exist, text, deptId, operatorId));
    }

    /** 从受理时的菜谱生成改动，再按同一版本校验并保存。 */
    private Outcome applyUpdateFromText(MealDish current, String text, Long deptId, Long operatorId) {
        Long dishId = current.getDishId();
        String name = current.getName();
        String model = null;
        try {
            AiChatResult chatResult = aiClient.chat(AiChatRequest.builder()
                    .scene(SCENE_UPDATE)
                    .system(updateSystemPrompt())
                    .prompt(buildUpdatePrompt(current, text))
                    .outputSchema(buildUpdateSchema())
                    .build());
            model = chatResult.getModel();
            Map<String, Object> data = toMap(chatResult.getStructured());
            String mode = updateText(data.get("mode"), false);
            String summary = updateText(data.get("summary"), false);
            if (summary != null && summary.length() > 80) {
                summary = null;
            }
            if ("unchanged".equals(mode)) {
                return new Outcome(true, updateSummary(summary, "没有需要更新的菜谱内容"));
            }
            boolean replace = "replace".equals(mode) || "rewrite".equals(mode);
            if (!replace && !"revise".equals(mode)) {
                throw new ServiceException("AI 未能生成有效的菜谱更新，请调整描述后重试");
            }

            List<?> ingredients = updateArray(data.get("ingredients"), "ingredients");
            List<?> steps = updateArray(data.get("steps"), "steps");
            if (replace && (ingredients == null || ingredients.isEmpty() || steps == null || steps.isEmpty())) {
                throw new ServiceException("AI 没有生成完整的用料和做法，菜谱已保留");
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            MealDish update = new MealDish();
            update.setDishId(dishId);
            for (String key : List.of("description", "duration", "level", "serve", "kcal")) {
                fillUpdateField(update, fields, key, updateText(data.get(key), replace));
            }
            List<?> tags = updateArray(data.get("tags"), "tags");
            fillUpdateField(update, fields, "tags", tags == null && !replace ? null : String.join(",", toStringList(tags)));
            fillUpdateField(update, fields, "ingredients", toJson(ingredients));
            fillUpdateField(update, fields, "steps", toJson(steps));
            List<?> tips = updateArray(data.get("tips"), "tips");
            fillUpdateField(update, fields, "tips", replace && tips == null ? "[]" : toJson(tips));

            if (fields.isEmpty()) {
                return new Outcome(true, "没有需要更新的菜谱内容");
            }

            update.setUpdateBy(AI_UPDATER);
            update.getParams().put("expectedVersion", current.getDataVersion());
            if (mealDishMapper.updateMealDish(update) != 1) {
                throw new ServiceException("菜品已被修改，请刷新后重新提交 AI 更新");
            }
            log.info("菜品 AI 更新完成，dishId={}，写入字段={}", dishId, fields.keySet());
            String updated = humanFieldNames(fields.keySet());
            notifyUpdateResult(deptId, dishId, name, operatorId, true, updated, model);
            return new Outcome(true, updateSummary(summary, "已更新" + updated));
        } catch (Exception e) {
            log.warn("菜品 AI 更新失败，dishId={}", dishId, e);
            String detail = readableMessage(e);
            notifyUpdateResult(deptId, dishId, name, operatorId, false, detail, model);
            return new Outcome(false, detail);
        }
    }

    /**
     * 发送菜品 AI 更新结果通知，任何异常都不影响异步任务本身
     *
     * @param deptId     菜品归属家庭 ID
     * @param dishId     菜品 ID
     * @param name       菜名
     * @param operatorId 发起更新的用户 ID
     * @param success    是否成功
     * @param detail     可读结果说明
     * @param model      模型名
     */
    private void notifyUpdateResult(Long deptId, Long dishId, String name, Long operatorId, boolean success,
            String detail, String model) {
        try {
            mealWeComNotifyService.notifyDishAiUpdate(deptId, dishId, name, operatorId, success, detail, model);
        } catch (Exception e) {
            log.warn("发送菜品 AI 更新企业微信通知失败，dishId={}", dishId, e);
        }
    }

    /** 取异常的可读提示，避免把堆栈写进通知 */
    private String readableMessage(Exception e) {
        return e instanceof ServiceException && e.getMessage() != null ? e.getMessage() : "AI 这次没有帮上忙";
    }

    /** 模型没有提供简短说明时返回对应的固定提示。 */
    private String updateSummary(String summary, String fallback) {
        return summary == null || summary.isBlank() ? fallback : summary;
    }

    /**
     * 把写入字段的技术名转成家庭场景可读的中文名
     *
     * @param keys 字段名集合
     * @return 顿号分隔的中文名
     */
    private String humanFieldNames(java.util.Set<String> keys) {
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        names.put("description", "简介");
        names.put("tags", "标签");
        names.put("duration", "耗时");
        names.put("level", "难度");
        names.put("serve", "份量");
        names.put("kcal", "热量");
        names.put("ingredients", "用料");
        names.put("steps", "做法");
        names.put("tips", "小贴士");
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String key : keys) {
            result.add(names.getOrDefault(key, key));
        }
        return String.join("、", result);
    }


    /**
     * 按菜名与家庭 ID 补齐当前为空的文本字段（不依赖登录态，供异步线程复用）
     *
     * @param name    菜名
     * @param current 当前已填字段快照
     * @param deptId  归属家庭 ID，决定可用菜品范围（分类候选为该家庭的公共分类 + 私有分类）
     * @return 只含被补齐文本字段与唯一主类匹配结果
     */
    public DishAiTextResult generateTextForDept(String name, DishAiGenerateRequest.Current current, Long deptId) {
        Map<String, Long> categoryIdByName = loadCandidateCategories(deptId);
        DishAiTextResult result = emptyTextResult();
        Missing missing = detectMissing(current);
        // 分类只能从该家庭可见的启用分类里选；候选为空（未初始化）时不请求分类字段
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
                    .prompt(buildPrompt(name, userNotes(current)))
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
     * <p>审核前会把该家庭已有的菜品（含公共菜谱）一并交给模型，用于识别「同一道菜的不同叫法」；
     * 本地还会对菜名做一次归一化相似度兜底，命中时直接驳回、不再调用模型。</p>
     *
     * @param name    菜名
     * @param current 当前已填字段快照
     * @param deptId  归属家庭 ID，决定已有菜品范围（分类候选为该家庭的公共分类 + 私有分类）
     * @return 判定结果；通过时携带补齐的字段与唯一主类匹配结果
     */
    public DishAiAuditResult auditAndComplete(String name, DishAiGenerateRequest.Current current, Long deptId) {
        List<String> existingNames = loadExistingDishNames(deptId);
        // 本地兜底：菜名几乎一致时直接驳回，避免明显重复的提案浪费一次模型调用
        String duplicate = findDuplicateDish(name, existingNames);
        if (duplicate != null) {
            DishAiAuditResult rejected = new DishAiAuditResult();
            rejected.setApproved(false);
            rejected.setRejectReason(duplicateRejectReason(duplicate));
            return rejected;
        }

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
                .prompt(buildAuditPrompt(name, existingNames, userNotes(current)))
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
        // 模型判定通过后仍再做一次本地相似度兜底，模型漏判时也不会重复上架
        if (audit.isApproved()) {
            String similar = findDuplicateDish(name, existingNames);
            if (similar != null) {
                audit.setApproved(false);
                audit.setRejectReason(duplicateRejectReason(similar));
            }
        }
        DishAiTextResult text = emptyTextResult();
        if (audit.isApproved()) {
            extractFields(text, data, missing, requestCategories, categoryIdByName);
        }
        audit.setText(text);
        return audit;
    }

    /**
     * 查询审核范围内已有的菜品名称（本家庭私有菜 + 公共菜谱，含已下架）
     *
     * @param deptId 归属家庭 ID
     * @return 菜品名称列表
     */
    private List<String> loadExistingDishNames(Long deptId) {
        List<String> names = mealDishMapper.selectDishNamesByDept(deptId);
        return names == null ? new ArrayList<>() : names;
    }

    /**
     * 在已有菜品中查找与待审核菜名本质相同的菜
     *
     * <p>先做归一化（去空白标点、统一常见同义词与写法）后比较：
     * 完全一致直接判重；两个菜名都不少于 4 个字符时，字符集合相似度达到阈值也判重。
     * 这样「番茄炒蛋」与「西红柿炒鸡蛋」、「炒土豆片」与「炒洋芋片」等近似叫法都能拦住。
     * 本地无法识别的同菜异名（如别名菜）再由模型结合已有菜名判断。</p>
     *
     * @param name          待审核菜名
     * @param existingNames 已有菜品名称
     * @return 命中的已有菜名；没有重复返回 null
     */
    private String findDuplicateDish(String name, List<String> existingNames) {
        String normalized = normalizeDishName(name);
        if (normalized.isEmpty() || existingNames == null) {
            return null;
        }
        for (String existing : existingNames) {
            if (existing == null) {
                continue;
            }
            String normalizedExisting = normalizeDishName(existing);
            if (normalizedExisting.isEmpty()) {
                continue;
            }
            if (normalized.equals(normalizedExisting) || isSimilarDishName(normalized, normalizedExisting)) {
                return existing;
            }
        }
        return null;
    }

    /**
     * 归一化后的菜名是否足够相似
     *
     * <p>只对不少于 4 个字符的菜名做模糊比较，避免「鸡」「鱼」这类短名互相误杀。</p>
     *
     * @param left  归一化菜名
     * @param right 归一化菜名
     * @return 是否视为同一道菜
     */
    private boolean isSimilarDishName(String left, String right) {
        if (left.length() < DUPLICATE_MIN_NAME_LENGTH || right.length() < DUPLICATE_MIN_NAME_LENGTH) {
            return false;
        }
        return jaccard(left, right) >= DUPLICATE_SIMILARITY;
    }

    /**
     * 菜名归一化：去掉空白与标点，统一常见同义词（番茄/西红柿、土豆/马铃薯/洋芋、鸡蛋/蛋）
     *
     * @param name 原始菜名
     * @return 归一化后的菜名
     */
    private String normalizeDishName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(name.length());
        for (char ch : name.toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        String normalized = builder.toString();
        for (Map.Entry<String, String> entry : DISH_NAME_SYNONYMS.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    /**
     * 字符集合 Jaccard 相似度：同时命中的字越多、并集越小，越接近同一道菜
     *
     * @param left  归一化菜名
     * @param right 归一化菜名
     * @return 0~1 的相似度
     */
    private double jaccard(String left, String right) {
        Set<Character> leftSet = new HashSet<>();
        for (char ch : left.toCharArray()) {
            leftSet.add(ch);
        }
        Set<Character> rightSet = new HashSet<>();
        for (char ch : right.toCharArray()) {
            rightSet.add(ch);
        }
        Set<Character> intersection = new HashSet<>(leftSet);
        intersection.retainAll(rightSet);
        Set<Character> union = new HashSet<>(leftSet);
        union.addAll(rightSet);
        return union.isEmpty() ? 0D : (double) intersection.size() / union.size();
    }

    /** 重复菜品的驳回原因 */
    private String duplicateRejectReason(String existingName) {
        return "已有相似菜品「" + existingName + "」，同一道菜不重复上架";
    }

    /**
     * 按菜名生成封面；普通补全模式下已有封面不重复生成，编辑页强制重建时始终生成。
     *
     * @param request 生成请求（菜名、已填字段快照、可选图片描述与强制重建标记）
     * @return 封面永久地址或失败原因
     */
    public DishAiCoverResult generateCover(DishAiGenerateRequest request) {
        String name = resolveName(request);
        DishAiGenerateRequest.Current current = request.getCurrent();
        boolean forceRegenerate = Boolean.TRUE.equals(request.getForceRegenerate());
        String description = request.getImageDescription() == null ? "" : request.getImageDescription().trim();

        DishAiCoverResult result = new DishAiCoverResult();
        if (forceRegenerate || isBlank(current == null ? null : current.getCover())) {
            // 重新生成必须带描述；普通补全保留原有「只补缺失」行为，不要求额外输入
            if (forceRegenerate && description.isEmpty()) {
                throw new ServiceException("请填写图片描述");
            }
            fillCover(result, name, description);
        }
        return result;
    }

    /**
     * 组装封面提示词：菜名是主体，用户描述补充构图、光线、摆盘等画面要求。
     *
     * @param name        菜名
     * @param description 画面描述
     * @return 完整图片提示词
     */
    private String buildCoverPrompt(String name, String description) {
        if (description == null || description.trim().isEmpty()) {
            return name;
        }
        return "制作菜品「" + name + "」的诱人美食摄影封面。画面要求：" + description.trim()
                + "。只呈现菜品与用餐场景，不要文字、水印或人物。";
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
            properties.put("category", stringSchema("分类名称，必须是给定候选分类之一"));
            required.add("category");
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
     * 把模型返回的数据按缺失字段抽取到结果（分类只匹配给定候选，未命中的单列）
     *
     * @param result            结果输出
     * @param data              模型返回的结构化数据
     * @param missing           缺失字段判定
     * @param requestCategories 本次是否请求分类
     * @param categoryIdByName  主类名 → 分类 ID 映射
     */
    private void extractFields(DishAiTextResult result, Map<String, Object> data, Missing missing,
            boolean requestCategories, Map<String, Long> categoryIdByName) {
        if (requestCategories) {
            String categoryName = toText(data.get("category"));
            if (categoryName != null && !categoryName.isEmpty()) {
                Long categoryId = categoryIdByName.get(categoryName);
                if (categoryId != null) {
                    result.setMatchedCategoryId(categoryId);
                } else {
                    result.setUnmatchedCategoryName(categoryName);
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
     * 调用文生图生成封面；失败只记录可读原因，不影响文本补齐。
     *
     * @param result      封面结果
     * @param name        菜名
     * @param description 用户补充的画面描述；为空时只按菜名生成
     */
    private void fillCover(DishAiCoverResult result, String name, String description) {
        try {
            AiImageResult imageResult = aiClient.image(AiImageRequest.builder()
                    .scene(SCENE_COVER)
                    .prompt(buildCoverPrompt(name, description))
                    .style(STYLE_KEY)
                    .build());
            AiImage image = firstImage(imageResult);
            if (image == null) {
                result.setImageError("图片生成未返回结果");
            } else {
                result.setCover(image.getUrl());
                result.setTransferStatus(image.getTransferStatus());
                result.setTransferTaskId(image.getTransferTaskId());
            }
        } catch (AiException e) {
            result.setImageError(e.getMessage());
        }
    }

    /** 保存的菜谱字段可省略，模型同时返回自动判断的编辑范围。 */
    private String buildUpdateSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", Map.of("type", "string", "enum", List.of("replace", "revise", "rewrite", "unchanged")));
        properties.put("summary", stringSchema("一句话说明修改内容或未修改的原因"));
        properties.put("description", stringSchema());
        properties.put("tags", arrayOfStringSchema());
        properties.put("duration", stringSchema());
        properties.put("level", stringSchema());
        properties.put("serve", stringSchema());
        properties.put("kcal", stringSchema());
        properties.put("ingredients", arrayOfIngredientSchema());
        properties.put("steps", arrayOfStringSchema());
        properties.put("tips", arrayOfStringSchema());
        return JSON.toJSONString(objectSchema(properties, List.of("mode", "summary")));
    }

    /** 将已保存版本与用户的新菜谱或修改要求作为独立字段传给模型。 */
    private String buildUpdatePrompt(MealDish current, String text) {
        Map<String, Object> recipe = new LinkedHashMap<>();
        recipe.put("name", current.getName());
        recipe.put("description", current.getDescription());
        recipe.put("tags", current.getTags());
        recipe.put("duration", current.getDuration());
        recipe.put("level", current.getLevel());
        recipe.put("serve", current.getServe());
        recipe.put("kcal", current.getKcal());
        recipe.put("ingredients", JSON.parse(current.getIngredients()));
        recipe.put("steps", JSON.parse(current.getSteps()));
        recipe.put("tips", JSON.parse(current.getTips()));
        return JSON.toJSONString(Map.of("currentRecipe", recipe, "userInput", text));
    }

    /**
     * 将候选字段放入更新对象。null 保留原值，空字符串或空数组显式清空。
     *
     * @param update 更新对象
     * @param fields 本次实际写入字段集合
     * @param key    字段名
     * @param value  提取结果
     */
    private void fillUpdateField(MealDish update, Map<String, Object> fields, String key, String value) {
        if (value == null) {
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

    /** 检查模型返回的文本类型，区分全量编辑清空和局部编辑保留。 */
    private String updateText(Object value, boolean replace) {
        if (value == null) {
            return replace ? "" : null;
        }
        if (value instanceof String text) {
            return text.trim();
        }
        throw new ServiceException("AI 返回的菜谱文本格式不正确，请重试");
    }

    /** 只接收现有菜谱编辑器可读的数组结构，保留显式空数组。 */
    private List<?> updateArray(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> items && items.stream().allMatch(item -> "ingredients".equals(key)
                ? item instanceof Map<?, ?> ingredient && ingredient.get("name") instanceof String name && !name.isBlank()
                        && (ingredient.get("amount") == null || ingredient.get("amount") instanceof String)
                : item instanceof String text && !text.isBlank())) {
            return items;
        }
        throw new ServiceException("AI 返回的用料、做法或其他列表格式不正确，请重试");
    }

    /**
     * 查询指定归属下可选的分类，并建立「分类名 → 分类ID」映射
     *
     * <p>候选为该归属可见的启用分类：公共分类（{@code dept_id = 0}）与归属家庭的私有分类。
     * 公共分则只保留 9 个名称内的记录；私有分类按名称参与匹配。</p>
     *
     * @param ownerDeptId 菜品/提案归属家庭 ID；0 表示公共菜谱
     * @return 分类名 → 分类ID 映射
     */
    private Map<String, Long> loadCandidateCategories(Long ownerDeptId) {
        MealCategory query = new MealCategory();
        query.setDeptId(ownerDeptId == null ? MealCategoryTaxonomy.PUBLIC_DEPT_ID : ownerDeptId);
        query.setStatus("0");
        List<MealCategory> categories = mealCategoryMapper.selectMealCategoryList(query);
        Map<String, Long> categoryIdByName = new LinkedHashMap<>();
        for (MealCategory category : categories) {
            if (!mealCategoryGuard.isSelectable(category, ownerDeptId)) {
                continue;
            }
            String name = category.getName() == null ? null : category.getName().trim();
            if (name != null && !name.isEmpty()) {
                categoryIdByName.putIfAbsent(name, category.getCategoryId());
            }
        }
        // 兜底：库里还没初始化公共分类时，按常量清单查一次，避免分类字段整段不请求
        if (categoryIdByName.isEmpty() && (ownerDeptId == null || ownerDeptId.longValue() == 0L)) {
            for (String name : MealCategoryTaxonomy.names()) {
                Long categoryId = mealCategoryMapper.selectPrimaryCategoryId(name);
                if (categoryId != null) {
                    categoryIdByName.put(name, categoryId);
                }
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
     * 读取文本补齐的 system 提示词（可在「参数设置」修改，未配置时用缺省值），
     * 并固定追加公共分类定义与判定顺序
     *
     * @return system 提示词
     */
    private String systemPrompt() {
        return ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_DISH_SYSTEM, DEFAULT_SYSTEM_PROMPT)
                + "\n" + USER_NOTES_RULE + "\n" + MealCategoryTaxonomy.promptFragment();
    }

    /**
     * 读取自动审核的 system 提示词：在补齐提示词之后追加判定说明
     */
    private String auditPrompt() {
        return systemPrompt() + ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_DISH_AUDIT_SUFFIX,
                DEFAULT_AUDIT_SUFFIX) + AUDIT_DUPLICATE_SUFFIX;
    }

    /** 兼容已配置的提示词，在其后给出优先级更高的当前菜谱编辑规则。 */
    private String updateSystemPrompt() {
        String custom = ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_DISH_UPDATE_SYSTEM, "");
        return custom.isBlank() ? UPDATE_RECIPE_RULES : "原有菜谱编辑偏好：\n" + custom
                + "\n\n以下是当前任务规则，若与原有偏好冲突，以当前规则为准：\n" + UPDATE_RECIPE_RULES;
    }

    /**
     * 提取用户填写的原始备注；没有备注时返回空字符串
     *
     * <p>新增菜品时备注只作为 AI 解析依据，不能把原文当作已填小贴士；
     * 编辑旧数据时兼容前端只有 tips 快照的历史调用。</p>
     *
     * @param current 当前表单已填字段快照
     * @return 去掉首尾空白的备注
     */
    private String userNotes(DishAiGenerateRequest.Current current) {
        if (current == null) {
            return "";
        }
        String notes = isBlank(current.getRemark()) ? current.getTips() : current.getRemark();
        return plainNotes(notes);
    }

    /**
     * 把备注字段还原成可直接阅读的文本
     *
     * <p>新菜提案的备注以 JSON 数组保存，AI 提示词中不能直接塞入
     * {@code ["..."]} 这类存储格式，应先还原为逐行文本。</p>
     *
     * @param raw 原始备注
     * @return 去掉首尾空白的纯文本
     */
    public String plainNotes(String raw) {
        if (raw == null) {
            return "";
        }
        String notes = raw.trim();
        if (!notes.startsWith("[") || !notes.endsWith("]")) {
            return notes;
        }
        try {
            List<String> lines = JSON.parseArray(notes, String.class);
            if (lines == null || lines.isEmpty()) {
                return notes;
            }
            List<String> nonBlank = new ArrayList<>();
            for (String line : lines) {
                if (!isBlank(line)) {
                    nonBlank.add(line.trim());
                }
            }
            return nonBlank.isEmpty() ? notes : String.join("\n", nonBlank);
        } catch (Exception e) {
            // 兼容历史脏数据：无法解析时保留原文，不让备注解析因格式异常中断
            return notes;
        }
    }

    /**
     * 组装生成场景的用户提示词
     *
     * @param name  菜名
     * @param notes 用户备注（可能是菜谱或制作要求）
     * @return 提示词
     */
    private String buildPrompt(String name, String notes) {
        StringBuilder builder = new StringBuilder("菜名：").append(name).append("\n");
        if (!isBlank(notes)) {
            builder.append("用户备注（制作硬性要求，必须体现在用料和步骤中）：\n")
                    .append(notes)
                    .append("\n");
        }
        builder.append("请补全所需字段。");
        return builder.toString();
    }

    /**
     * 组装自动审核的用户提示词：附带已有菜品名称，供模型判断是否为同一道菜
     *
     * @param name          待审核菜名
     * @param existingNames 已有菜品名称
     * @param notes         用户备注（可能是菜谱或制作要求）
     * @return 提示词
     */
    private String buildAuditPrompt(String name, List<String> existingNames, String notes) {
        StringBuilder builder = new StringBuilder(buildPrompt(name, notes));
        builder.append("\n已有菜品（用于判重）：")
                .append(existingNames == null || existingNames.isEmpty() ? "暂无" : String.join("、", existingNames))
                .append("\n请判断待审核菜名是否与已有菜品本质上是同一道菜；是则 approved 填 \"false\" 并说明与哪道重复。");
        if (!isBlank(notes)) {
            builder.append("\n用户备注中的做法、指定厨具、必须添加或禁止添加的食材均为硬性要求；")
                    .append("在用料和步骤中必须严格体现，不得改成相反做法。");
        }
        builder.append("\n同时给出 approved 判定；不通过时填写 rejectReason。");
        return builder.toString();
    }

    private DishAiTextResult emptyTextResult() {
        DishAiTextResult result = new DishAiTextResult();
        result.setFields(new LinkedHashMap<>());
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

    /** 提取首张可用图片，并保留转存状态供调用端提示。 */
    private AiImage firstImage(AiImageResult result) {
        if (result == null || result.getImages() == null) {
            return null;
        }
        for (AiImage image : result.getImages()) {
            if (image != null && image.getUrl() != null && !image.getUrl().isEmpty()) {
                return image;
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
