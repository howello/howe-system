package com.howe.meal.service;

import com.alibaba.fastjson2.JSON;
import com.howe.ai.api.AiClient;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.vo.MealPrepPlan;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.util.MealPrepPlanParser;
import com.howe.meal.util.MealScopeGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 一键备菜清单服务
 *
 * <p>清单由 AI 场景路由 {@code meal.order.prep} 生成，只写 Redis（TTL 10 天），不落数据库。
 * 下单后在事务提交回调中提交后台任务，避免 AI 调用拖慢下单；线程池满或调用失败都只记录日志，
 * 不影响订单本身。订单完成或取消时直接删除对应 key。</p>
 *
 * <p>提示词规则固定追加「剔除调味品、合并相似食材、按采购区域分组、主菜优先、生活化数量」，
 * 不随 {@code sys_config} 覆盖，避免自定义提示词丢失用户的硬性要求。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class MealPrepPlanService {

    /** 备菜清单生成场景（AI 场景路由 key）。 */
    private static final String SCENE_PREP = "meal.order.prep";

    /** Redis key 前缀，后接订单 ID。 */
    private static final String KEY_PREFIX = "meal:prep:plan:";

    /**
     * 「订单已结束」墓碑 key 前缀
     *
     * <p>订单完成、取消、无人接单或删除时写入。清单写入通过
     * {@link RedisCache#setStringIfGuardAbsent} 的 Lua 脚本在单次原子操作内
     * 先检查墓碑再写入，因此清理与异步写回互斥：一旦写过墓碑，
     * 任何后台线程都无法再把旧清单写回。</p>
     */
    private static final String CLOSED_KEY_PREFIX = "meal:prep:closed:";

    /** 清单保留天数：10 天。 */
    private static final long TTL_DAYS = 10L;

    /** 备菜清单 system 提示词的缺省值。 */
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一位熟悉中国菜市场的家庭采购助理。"
            + "用户会给出一张家庭订单的菜品清单与每道菜的用料，请汇总成一张可以直接拿去买菜的备菜清单。"
            + "所有内容使用简体中文，只输出 JSON。";

    /**
     * 固定内容规则：不随 sys_config 覆盖，属于用户明确的硬性要求。
     */
    private static final String FIXED_RULES = "必须严格遵守以下规则：\n"
            + "1. 剔除常见调味品，不进入采购清单：生抽、老抽、酱油、食用油、菜籽油、花生油、香油、料酒、黄酒、"
            + "白糖、冰糖、红糖、盐、鸡精、味精、胡椒粉、花椒粉、淀粉、生粉、香醋、陈醋、米醋、蚝油、"
            + "大料、八角、香叶、桂皮、花椒、干辣椒段、豆瓣酱、甜面酱、黄豆酱、番茄酱、辣椒油、孜然、五香粉。\n"
            + "2. 合并相似食材：同一种食材的不同叫法或品种合并为一条，name 写最常用的规范名称，"
            + "details 保留原始品种名与各自数量。例如青椒、红椒、尖椒、辣椒合并为「辣椒」，"
            + "details 分别写「青椒 3 个」「红椒 2 个」。\n"
            + "3. 按线下采购地点分组 market，例如肉摊、菜摊、水产摊、豆制品摊、蛋类、干货粮油、其他；"
            + "同一种采购地点的食材必须放在同一个分组，分组顺序按买菜动线排列（肉摊、水产摊、菜摊、"
            + "豆制品摊、蛋类、干货粮油、其他）。\n"
            + "4. 每个分组内主料在前、配料在后：main=true 表示这道订单的主菜用料（肉类、水产、蛋类等），"
            + "main=false 表示为配菜或辅料（蔬菜、豆制品、香辛蔬菜等）。\n"
            + "5. 数量使用菜场生活化单位：斤、两、根、个、颗、把、块、条、只、盒、袋、瓶；"
            + "禁止使用克、千克、kg、g 等重量单位。需要换算时按常见份量估算，例如 600 克肉写作「1 斤 2 两」"
            + "或「1.2 斤」，3 个辣椒写作「3 个」。\n"
            + "6. 只汇总订单里真实出现的食材，不补充订单未点的菜品；同一食材在不同菜里重复出现时合并数量。\n"
            + "7. 不输出任何解释文字、Markdown 或代码块，只输出符合 schema 的 JSON。";

    private final AiClient aiClient;

    private final RedisCache redis;

    private final MealOrderMapper mealOrderMapper;

    private final MealDishMapper mealDishMapper;

    private final Executor executor;

    /**
     * 注入 AI 任务线程池与既有 Mapper，避免为备菜再建一套执行资源。
     *
     * @param aiClient       AI 门面
     * @param redis          Redis 工具
     * @param mealOrderMapper 订单 Mapper
     * @param mealDishMapper  菜品 Mapper
     * @param executor       独立 AI 线程池，不允许退化到请求线程
     */
    public MealPrepPlanService(AiClient aiClient, RedisCache redis, MealOrderMapper mealOrderMapper,
            MealDishMapper mealDishMapper, @Qualifier("aiTaskExecutor") Executor executor) {
        this.aiClient = aiClient;
        this.redis = redis;
        this.mealOrderMapper = mealOrderMapper;
        this.mealDishMapper = mealDishMapper;
        this.executor = executor;
    }

    /**
     * 提交一次后台生成任务
     *
     * <p>先写入「生成中」占位，让厨师端能立即看到状态；随后在线程池执行 AI 调用。
     * 线程池拒绝时把占位改为失败并记录日志，不向上抛出，避免影响下单或页面调用。
     * 订单已结束时直接跳过，不写占位，避免前端轮询一个永远不会完成的生成状态。</p>
     *
     * @param orderId 订单 ID
     */
    public void submitGenerate(Long orderId) {
        if (orderId == null) {
            return;
        }
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null || isEndedStatus(order.getStatus())) {
            log.info("订单不存在或已结束，跳过备菜清单生成，orderId={}", orderId);
            return;
        }
        submitGenerate(orderId, order.getStatus());
    }

    /**
     * 提交后台生成任务（已读取订单状态时复用）
     *
     * @param orderId 订单 ID
     * @param status  提交时的订单状态
     */
    private void submitGenerate(Long orderId, String status) {
        if (isEndedStatus(status)) {
            return;
        }
        MealPrepPlan placeholder = new MealPrepPlan();
        placeholder.setOrderId(orderId);
        placeholder.setStatus(MealPrepPlan.STATUS_GENERATING);
        placeholder.setGeneratedAt(System.currentTimeMillis());
        try {
            save(placeholder);
        } catch (Exception e) {
            log.warn("写入备菜清单生成中状态失败，orderId={}", orderId, e);
        }
        try {
            executor.execute(() -> generate(orderId));
        } catch (Exception e) {
            log.warn("备菜清单任务未受理，orderId={}", orderId, e);
            markFailed(orderId, "任务繁忙，未开始生成，请稍后重新生成");
        }
    }

    /**
     * 读取订单的备菜清单
     *
     * <p>订单必须属于当前家庭；Redis 无数据时返回「未生成」状态而不是抛错，
     * 由前端展示失败提示与「重新生成」。订单已结束时返回不可重试的结束状态，
     * 前端据此隐藏「重新生成」。</p>
     *
     * @param orderId 订单 ID
     * @return 当前清单或未生成状态
     */
    public MealPrepPlan getPlan(Long orderId) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        MealPrepPlan empty = new MealPrepPlan();
        empty.setOrderId(orderId);
        // 先判订单状态再看缓存：结束态一律返回 CLOSED，
        // 即使异步线程刚写回旧清单，也不会把已结束订单的清单再展示出来
        if (isEndedStatus(order.getStatus())) {
            empty.setStatus(MealPrepPlan.STATUS_CLOSED);
            empty.setMessage("订单已结束，备菜清单已清理");
            return empty;
        }
        MealPrepPlan plan = read(orderId);
        if (plan != null) {
            return plan;
        }
        empty.setStatus(MealPrepPlan.STATUS_FAILED);
        empty.setMessage("备菜清单尚未生成或已过期，可重新生成");
        return empty;
    }

    /**
     * 重新生成：校验订单归属与状态后重新提交后台任务
     *
     * <p>结束状态与 {@link #getPlan(Long)} 使用同一判断，避免无人接单等状态下
     * 写入 GENERATING 占位后后台直接退出、前端永久轮询。</p>
     *
     * @param orderId 订单 ID
     * @return 提交后的占位清单
     */
    public MealPrepPlan regenerate(Long orderId) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        if (isEndedStatus(order.getStatus())) {
            throw new ServiceException("订单已结束，无需再生成备菜清单");
        }
        submitGenerate(orderId, order.getStatus());
        return getPlan(orderId);
    }

    /**
     * 删除订单的备菜清单
     *
     * <p>用 Lua 脚本原子地「写结束墓碑 + 删除清单」：生成线程的写回在同一脚本内
     * 检查墓碑，因此清理之后不会再有异步任务把旧清单写回来。
     * 只记录失败日志：清理属于收尾动作，不能因为 Redis 异常让订单完成或取消回滚。</p>
     *
     * @param orderId 订单 ID
     */
    public void removePlan(Long orderId) {
        if (orderId == null) {
            return;
        }
        try {
            redis.closeByGuard(CLOSED_KEY_PREFIX + orderId, KEY_PREFIX + orderId, TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("清理备菜清单失败，orderId={}", orderId, e);
        }
    }

    /**
     * 后台生成：读取订单明细与用料，调用 AI 结构化输出并写回 Redis
     *
     * @param orderId 订单 ID
     */
    private void generate(Long orderId) {
        try {
            MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
            if (order == null) {
                return;
            }
            // 下单后可能已被取消或很快完成，已结束的订单不再生成
            if (!isActiveOrder(orderId)) {
                log.info("订单已结束，跳过备菜清单生成，orderId={}", orderId);
                return;
            }
            List<MealOrderItem> items = mealOrderMapper.selectItemsByOrderId(orderId);
            if (items == null || items.isEmpty()) {
                markFailed(orderId, "订单没有可汇总的菜品");
                return;
            }
            String prompt = buildPrompt(items);
            if (prompt.isEmpty()) {
                // 无用料不是失败：写入 READY 空清单，页面展示空态而不是误导性的「生成失败」
                saveEmpty(orderId);
                return;
            }
            AiChatRequest request = AiChatRequest.builder()
                    .scene(SCENE_PREP)
                    .system(ConfigUtils.getString(ConfigConstants.AI_PROMPT_MEAL_ORDER_PREP_SYSTEM,
                            DEFAULT_SYSTEM_PROMPT) + "\n" + FIXED_RULES)
                    .prompt(prompt)
                    .outputSchema(prepSchema())
                    .build();
            AiChatResult result = aiClient.chat(request);
            MealPrepPlan plan = MealPrepPlanParser.parse(orderId,
                    result == null ? null : result.getStructured());
            if (plan == null || plan.getGroups().isEmpty()) {
                markFailed(orderId, "AI 未返回可用的备菜清单，请重新生成");
                return;
            }
            // AI 调用可能持续数秒，期间订单可能已完成或取消；写回前复查状态，避免清理后又被写回
            if (!isActiveOrder(orderId)) {
                log.info("订单已结束，丢弃本次备菜清单生成结果，orderId={}", orderId);
                return;
            }
            save(plan);
        } catch (Exception e) {
            log.warn("生成备菜清单失败，orderId={}", orderId, e);
            markFailed(orderId, "备菜清单生成失败，请重新生成");
        }
    }

    /**
     * 把订单明细与菜品用料拼成 AI 输入
     *
     * <p>用料通过 {@link MealDishMapper#selectMealDishById(Long)} 逐菜读取；
     * 单道菜缺失或 JSON 解析失败只跳过该菜，不中断整单。</p>
     *
     * @param items 订单明细
     * @return 用户提示词；全部菜品都没有用料时返回空串
     */
    private String buildPrompt(List<MealOrderItem> items) {
        StringBuilder prompt = new StringBuilder("订单菜品与用料如下：\n");
        boolean hasIngredient = false;
        for (MealOrderItem item : items) {
            int count = item.getCount() == null || item.getCount() < 1 ? 1 : item.getCount();
            prompt.append("- ").append(safe(item.getDishName())).append(" × ").append(count).append(" 份");
            MealDish dish = item.getDishId() == null ? null : mealDishMapper.selectMealDishById(item.getDishId());
            List<String> ingredientLines = ingredientLines(dish == null ? null : dish.getIngredients());
            if (ingredientLines.isEmpty()) {
                prompt.append("（无用料数据）\n");
                continue;
            }
            hasIngredient = true;
            prompt.append("，用料：").append(String.join("、", ingredientLines)).append('\n');
        }
        if (!hasIngredient) {
            return "";
        }
        prompt.append("\n请按规则汇总成采购清单，数量按菜品份数累加。");
        return prompt.toString();
    }

    /**
     * 解析菜品 ingredients JSON 为「名称 用量」文本行
     *
     * @param raw ingredients JSON 字符串
     * @return 文本行列表；解析失败返回空列表
     */
    private List<String> ingredientLines(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return lines;
        }
        try {
            Object parsed = JSON.parse(raw);
            if (!(parsed instanceof List<?> list)) {
                return lines;
            }
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Object name = map.get("name");
                Object amount = map.get("amount");
                if (name == null || String.valueOf(name).isBlank()) {
                    continue;
                }
                String line = String.valueOf(name).trim();
                if (amount != null && !String.valueOf(amount).isBlank()) {
                    line += " " + String.valueOf(amount).trim();
                }
                lines.add(line);
            }
        } catch (Exception e) {
            log.debug("菜品用料 JSON 解析失败，按无用料处理");
        }
        return lines;
    }

    /**
     * 空值安全的菜品名
     *
     * @param value 原始值
     * @return 文本
     */
    private String safe(String value) {
        return value == null || value.isBlank() ? "未命名菜品" : value.trim();
    }

    /**
     * 结构化输出 schema
     *
     * @return JSON Schema 字符串
     */
    private String prepSchema() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", "object");
        detail.put("properties", Map.of(
                "label", Map.of("type", "string"),
                "amount", Map.of("type", "string")));
        detail.put("required", List.of("label", "amount"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", Map.of(
                "name", Map.of("type", "string", "description", "合并后的规范食材名"),
                "amount", Map.of("type", "string", "description", "生活化购买数量，如 2 斤、3 个"),
                "main", Map.of("type", "string", "enum", List.of("true", "false"),
                        "description", "true=主料，false=配料"),
                "details", Map.of("type", "array", "items", detail)));
        item.put("required", List.of("name", "amount", "main", "details"));

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("type", "object");
        group.put("properties", Map.of(
                "market", Map.of("type", "string", "description", "采购区域，如肉摊、菜摊"),
                "items", Map.of("type", "array", "items", item)));
        group.put("required", List.of("market", "items"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("groups", Map.of("type", "array", "items", group)));
        schema.put("required", List.of("groups"));
        return JSON.toJSONString(schema);
    }

    /**
     * 读取清单
     *
     * <p>清单由 Lua 脚本以纯 JSON 字符串写入，直接按字符串解析；
     * 兼容历史对象序列化数据。解析失败按未生成处理。</p>
     *
     * @param orderId 订单 ID
     * @return 清单或 null
     */
    private MealPrepPlan read(Long orderId) {
        try {
            Object value = redis.getCacheObject(KEY_PREFIX + orderId);
            if (value instanceof MealPrepPlan plan) {
                return plan;
            }
            if (value instanceof String json && !json.isBlank()) {
                return JSON.parseObject(json, MealPrepPlan.class);
            }
            if (value != null) {
                return JSON.parseObject(JSON.toJSONString(value), MealPrepPlan.class);
            }
        } catch (Exception e) {
            log.warn("读取备菜清单失败，orderId={}", orderId, e);
        }
        return null;
    }

    /**
     * 写入清单并刷新 10 天 TTL
     *
     * <p>通过 Lua 脚本在单次原子操作内「检查结束墓碑 → 写入清单」：
     * 订单完成/取消/无人接单/删除写入墓碑后，即使后台线程已通过状态检查，
     * 也无法再把旧清单写回。返回值只用于日志，不向调用方抛出。</p>
     *
     * @param plan 清单
     * @return 实际写入返回 true；订单已结束或 Redis 异常返回 false
     */
    private boolean save(MealPrepPlan plan) {
        try {
            // 清单以纯 JSON 字符串读写，与守卫脚本共用字符串序列化，避免对象序列化器改写脚本参数
            String payload = JSON.toJSONString(plan);
            boolean written = redis.setStringIfGuardAbsent(
                    CLOSED_KEY_PREFIX + plan.getOrderId(),
                    KEY_PREFIX + plan.getOrderId(),
                    payload, TTL_DAYS, TimeUnit.DAYS);
            if (!written) {
                log.info("订单已结束，跳过备菜清单写入，orderId={}", plan.getOrderId());
            }
            return written;
        } catch (Exception e) {
            log.warn("写入备菜清单失败，orderId={}", plan.getOrderId(), e);
            return false;
        }
    }

    /**
     * 标记生成失败
     *
     * @param orderId 订单 ID
     * @param message 失败原因
     */
    private void markFailed(Long orderId, String message) {
        // 订单已结束时不写失败状态，避免清理之后又被写回
        if (!isActiveOrder(orderId)) {
            return;
        }
        MealPrepPlan failed = new MealPrepPlan();
        failed.setOrderId(orderId);
        failed.setStatus(MealPrepPlan.STATUS_FAILED);
        failed.setMessage(message);
        failed.setGeneratedAt(System.currentTimeMillis());
        try {
            save(failed);
        } catch (Exception e) {
            log.warn("写入备菜清单失败状态异常，orderId={}", orderId, e);
        }
    }

    /**
     * 写入「已生成但没有用料」的空清单
     *
     * <p>与生成失败区分：订单本身没有可采购的食材是正常结果，
     * 页面按空态提示展示，不提供「重新生成」误导用户。</p>
     *
     * @param orderId 订单 ID
     */
    private void saveEmpty(Long orderId) {
        MealPrepPlan empty = new MealPrepPlan();
        empty.setOrderId(orderId);
        empty.setStatus(MealPrepPlan.STATUS_READY);
        empty.setMessage("订单菜品暂无可用料数据");
        empty.setGeneratedAt(System.currentTimeMillis());
        try {
            save(empty);
        } catch (Exception e) {
            log.warn("写入空备菜清单异常，orderId={}", orderId, e);
        }
    }

    /**
     * 判断订单是否处于已结束状态
     *
     * <p>完成、取消、无人接单都视为结束：清单已清理且不再允许重新生成。
     * {@link #getPlan(Long)}、{@link #regenerate(Long)} 与异步写入统一使用本方法，
     * 避免各分支判断不一致。</p>
     *
     * @param status 订单状态
     * @return 已结束返回 true
     */
    private static boolean isEndedStatus(String status) {
        return MealOrder.STATUS_FINISHED.equals(status)
                || MealOrder.STATUS_CANCELED.equals(status)
                || MealOrder.STATUS_UNACCEPTED.equals(status);
    }

    /**
     * 判断订单是否仍处于可生成状态（待接单或制作中）
     *
     * <p>完成、取消、无人接单或已删除都不应再保留备菜清单。</p>
     *
     * @param orderId 订单 ID
     * @return 仍可生成返回 true
     */
    private boolean isActiveOrder(Long orderId) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            return false;
        }
        return !isEndedStatus(order.getStatus());
    }

    /**
     * 读取订单并校验存在
     *
     * @param orderId 订单 ID
     * @return 订单
     */
    private MealOrder requireOrder(Long orderId) {
        if (orderId == null) {
            throw new ServiceException("订单ID不能为空");
        }
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        return order;
    }
}
