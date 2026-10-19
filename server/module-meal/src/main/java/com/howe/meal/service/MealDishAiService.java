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
import com.howe.common.utils.spring.SpringUtils;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.dto.DishAiAuditResult;
import com.howe.meal.domain.dto.DishAiCoverResult;
import com.howe.meal.domain.dto.DishAiGenerateRequest;
import com.howe.meal.domain.dto.DishAiTextResult;
import com.howe.meal.domain.dto.DishAiUpdateRequest;
import com.howe.meal.mapper.MealCategoryMapper;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.service.IMealDishService;
import com.howe.meal.service.IMealWeComNotifyService;
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
import java.util.concurrent.Executor;

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
 * <p>菜品编辑页的「AI 更新」通过 {@link #submitUpdateFromText} 提交到业务线程池执行：
 * 请求线程只校验权限并立即返回；异步落库使用 {@link MealDishMapper} 直接更新，不读取登录态。</p>
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

    /** 业务线程池的 bean 名，由 module-framework 提供 */
    private static final String EXECUTOR_BEAN = "threadPoolTaskExecutor";

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
     * 自动审核的判重说明（固定追加，不随 sys_config 覆盖）
     *
     * <p>判重属于业务红线：即使库里的审核提示词被改过，也必须让模型结合已有菜品判断是否重复。</p>
     */
    private static final String AUDIT_DUPLICATE_SUFFIX =
            "如果待审核菜名与「已有菜品」中的某道菜本质上是同一道菜（只是叫法、写法、别名或加料略有不同，"
            + "例如「西红柿炒鸡蛋」与「番茄炒蛋」「西红柿炒蛋」），也必须 approved 填 \"false\"，"
            + "rejectReason 写明与哪道已有菜品重复，避免重复上架。";

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

    private final MealDishMapper mealDishMapper;

    private final IMealWeComNotifyService mealWeComNotifyService;

    /** 线程池，延迟解析：容器未就绪或模块单独使用时退化为同步执行 */
    private volatile Executor executor;

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
     * 提交一次异步 AI 更新：校验在请求线程完成，提取与落库在线程池执行
     *
     * <p>请求线程只做参数校验、菜品可见性与写权限校验，随后立即返回；AI 调用与数据库更新
     * 在业务线程池中进行。异步线程没有登录态，因此落库使用 {@link MealDishMapper} 直接更新，
     * 操作人记为 {@link #AI_UPDATER}，不再读取 {@code SecurityUtils}。线程池不可用时退化为同步执行。</p>
     *
     * @param request AI 更新请求（菜品 ID + 完整做法文本）
     */
    public void submitUpdateFromText(DishAiUpdateRequest request) {
        if (request == null || request.getDishId() == null) {
            throw new ServiceException("请选择要更新的菜品");
        }
        String text = request.getText() == null ? "" : request.getText().trim();
        if (text.isEmpty()) {
            throw new ServiceException("请先输入这道菜的完整做法");
        }

        // 请求线程内完成可见性与写权限校验，异步线程不再访问登录态
        MealDish exist = mealDishService.selectMealDishById(request.getDishId());
        MealScopeGuard.assertDishWritable(exist.getDeptId(), "菜品");

        Long dishId = exist.getDishId();
        String name = exist.getName();
        // 异步线程没有登录态：操作人与家庭归属在请求线程内捕获。
        // 通知发到操作人所在家庭；公共菜（deptId=0）没有自己的家庭群。
        Long operatorId = SecurityUtils.getUserId();
        Long operatorDeptId = SecurityUtils.getDeptId();
        Long deptId = operatorDeptId != null ? operatorDeptId : exist.getDeptId();
        Executor target = resolveExecutor();
        if (target == null) {
            // 线程池不可用时同步执行，保证功能可用
            applyUpdateFromText(dishId, name, text, deptId, operatorId);
            return;
        }
        try {
            target.execute(() -> applyUpdateFromText(dishId, name, text, deptId, operatorId));
        } catch (Exception e) {
            log.warn("提交菜品 AI 更新任务失败，dishId={}", dishId, e);
        }
    }

    /**
     * 异步执行 AI 提取并落库（不依赖登录态）
     *
     * <p>只覆盖模型实际返回的非空字段；缺失或空字段保留数据库原值。
     * 菜名、封面、分类、归属与来源不变。结束后发送企业微信结果通知。</p>
     *
     * @param dishId     菜品 ID
     * @param name       菜名，用于提示词定位菜品
     * @param text       用户输入的完整做法
     * @param deptId     菜品归属家庭 ID
     * @param operatorId 发起更新的用户 ID
     */
    private void applyUpdateFromText(Long dishId, String name, String text, Long deptId, Long operatorId) {
        String model = null;
        try {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            appendUpdateSchema(properties);

            AiChatResult chatResult = aiClient.chat(AiChatRequest.builder()
                    .scene(SCENE_UPDATE)
                    .system(updateSystemPrompt())
                    .prompt(buildUpdatePrompt(name, text))
                    .outputSchema(JSON.toJSONString(objectSchema(properties, required)))
                    .build());
            model = chatResult.getModel();
            Object structured = chatResult.getStructured();
            if (!(structured instanceof Map)) {
                log.warn("菜品 AI 更新未返回可解析的结构化结果，dishId={}，raw={}", dishId,
                        abbreviate(chatResult.getText()));
                notifyUpdateResult(deptId, dishId, name, operatorId, false, "AI 没有看懂这段做法，这次先不改了", model);
                return;
            }
            Map<String, Object> data = toMap(structured);

            Map<String, Object> fields = new LinkedHashMap<>();
            MealDish update = new MealDish();
            update.setDishId(dishId);
            fillUpdateField(update, fields, "description", toText(data.get("description")));
            fillUpdateField(update, fields, "tags", String.join(",", toStringList(data.get("tags"))));
            fillUpdateField(update, fields, "duration", toText(data.get("duration")));
            fillUpdateField(update, fields, "level", toText(data.get("level")));
            fillUpdateField(update, fields, "serve", toText(data.get("serve")));
            fillUpdateField(update, fields, "kcal", toText(data.get("kcal")));
            fillUpdateField(update, fields, "ingredients", nonEmptyJson(data.get("ingredients")));
            fillUpdateField(update, fields, "steps", nonEmptyJson(data.get("steps")));
            fillUpdateField(update, fields, "tips", nonEmptyJson(data.get("tips")));

            if (fields.isEmpty()) {
                log.info("菜品 AI 更新未提取到可用字段，dishId={}", dishId);
                notifyUpdateResult(deptId, dishId, name, operatorId, false, "这段做法里没有找到能补充的菜品信息", model);
                return;
            }
            update.setUpdateBy(AI_UPDATER);
            mealDishMapper.updateMealDish(update);
            log.info("菜品 AI 更新完成，dishId={}，写入字段={}", dishId, fields.keySet());
            notifyUpdateResult(deptId, dishId, name, operatorId, true,
                    humanFieldNames(fields.keySet()), model);
        } catch (Exception e) {
            // 异步失败只记录日志：数据库保持原值，用户可稍后重试
            log.warn("菜品 AI 更新失败，dishId={}", dishId, e);
            notifyUpdateResult(deptId, dishId, name, operatorId, false, readableMessage(e), model);
        }
    }

    /** 发送菜品 AI 更新结果通知，任何异常都不影响异步任务本身 */
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

    /** 解析业务线程池；容器未就绪或没有该 bean 时返回 null */
    private Executor resolveExecutor() {
        Executor current = this.executor;
        if (current != null) {
            return current;
        }
        try {
            Object bean = SpringUtils.getBean(EXECUTOR_BEAN);
            if (bean instanceof Executor target) {
                this.executor = target;
                return target;
            }
        } catch (Exception e) {
            log.debug("未找到 {} 线程池，菜品 AI 更新改为同步执行", EXECUTOR_BEAN);
        }
        return null;
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
     * <p>审核前会把该家庭已有的菜品（含公共菜谱）一并交给模型，用于识别「同一道菜的不同叫法」；
     * 本地还会对菜名做一次归一化相似度兜底，命中时直接驳回、不再调用模型。</p>
     *
     * @param name    菜名
     * @param current 当前已填字段快照
     * @param deptId  归属家庭 ID，决定可用分类候选与已有菜品范围
     * @return 判定结果；通过时携带补齐的字段与分类匹配结果
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
                .prompt(buildAuditPrompt(name, categoryIdByName.keySet(), requestCategories, existingNames))
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
        return systemPrompt() + ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_DISH_AUDIT_SUFFIX,
                DEFAULT_AUDIT_SUFFIX) + AUDIT_DUPLICATE_SUFFIX;
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

    /**
     * 组装自动审核的用户提示词：附带已有菜品名称，供模型判断是否为同一道菜
     *
     * @param name           待审核菜名
     * @param categoryNames  可用分类名称
     * @param withCategories 是否请求模型匹配分类
     * @param existingNames  已有菜品名称
     * @return 提示词
     */
    private String buildAuditPrompt(String name, Collection<String> categoryNames, boolean withCategories,
            List<String> existingNames) {
        StringBuilder builder = new StringBuilder(buildPrompt(name, categoryNames, withCategories));
        builder.append("\n已有菜品（用于判重）：")
                .append(existingNames == null || existingNames.isEmpty() ? "暂无" : String.join("、", existingNames))
                .append("\n请判断待审核菜名是否与已有菜品本质上是同一道菜；是则 approved 填 \"false\" 并说明与哪道重复。");
        builder.append("\n同时给出 approved 判定；不通过时填写 rejectReason。");
        return builder.toString();
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
