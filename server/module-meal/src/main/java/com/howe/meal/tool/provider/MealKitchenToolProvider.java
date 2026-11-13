package com.howe.meal.tool.provider;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.model.ToolPage;
import com.howe.common.tool.model.ToolResult;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import com.howe.meal.domain.vo.MealKitchenOrderSummary;
import com.howe.meal.tool.service.IMealExecutionService;
import com.howe.meal.tool.service.IMealReadService;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 厨师工作台只读工具。
 *
 * <p>只发布待接单与制作中两个固定状态，不提供省略状态后返回家庭全量订单的入口；
 * 可见性完全复用领域 Service 的候选人与接单人条件。</p>
 */
public final class MealKitchenToolProvider implements BusinessToolProvider {

    /** 能力分组：厨房读取。 */
    private static final String GROUP = "meal.kitchen.read";

    /** 列表结果预算。 */
    private static final int LIST_BYTES = 65536;

    /** 列表输入字段。 */
    private static final Set<String> LIST_FIELDS = Set.of("status", "page", "pageSize");

    private final IMealExecutionService executions;

    /**
     * 构造厨房工具提供者。
     *
     * @param executions 领域执行服务
     */
    public MealKitchenToolProvider(IMealExecutionService executions) {
        this.executions = executions;
    }

    @Override
    public String domain() {
        return "meal";
    }

    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(orderList(), summaryGet());
    }

    /** 厨房订单列表。 */
    private ToolDefinition orderList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "string", "enum", List.of("waiting", "cooking")));
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10000, "default", 1));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 20));
        return new ToolDefinition("meal_kitchen_order_list", "1", "查询厨师工作台订单",
                "按状态查询当前账号可处理的订单：待接单只含本人作为当前轮次候选人的订单，"
                        + "制作中只含本人接单的订单；每单最多返回 10 条明细摘要。",
                GROUP, new ToolInputSchema("object", properties, List.of("status"), false, null, null),
                ToolAccess.ofPermissions(Set.of("meal:order:dispatch")),
                new ToolDefinition.ToolAnnotations("查询厨师工作台订单", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, LIST_FIELDS);
                    ToolArguments.requiredEnum(arguments, "status", Set.of("waiting", "cooking"));
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    String status = ToolArguments.requiredEnum(arguments, "status", Set.of("waiting", "cooking"));
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    IMealReadService.PageResult<IMealExecutionService.KitchenOrderView> result =
                            read(() -> executions.listKitchenOrders(status, page, pageSize), "厨房订单");
                    ToolPage pageInfo = ToolArguments.pageOf(page, pageSize, result.items().size(), result.total());
                    List<Map<String, Object>> data = result.items().stream()
                            .map(MealKitchenToolProvider::view).toList();
                    return ToolResults.success(data, pageInfo, result.hasMore(), List.of(),
                            "共 " + result.total() + " 条" + ("waiting".equals(status) ? "待接单" : "制作中") + "订单");
                });
    }

    /** 厨房角标。 */
    private ToolDefinition summaryGet() {
        return new ToolDefinition("meal_kitchen_summary_get", "1", "查询厨师工作台角标",
                "返回当前账号可见的待接单与制作中订单数量；待接单只统计本人待响应，制作中只统计本人接单。",
                GROUP, new ToolInputSchema("object", Map.of(), List.of(), false, null, null),
                ToolAccess.ofPermissions(Set.of("meal:order:dispatch")),
                new ToolDefinition.ToolAnnotations("查询厨师工作台角标", true, false, true, false, true),
                Duration.ofSeconds(10), 8192,
                ToolArguments::requireEmpty,
                (context, arguments) -> {
                    MealKitchenOrderSummary summary = read(executions::kitchenSummary, "厨房角标");
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("waitingCount", summary.pendingCount());
                    data.put("cookingCount", summary.cookingCount());
                    return ToolResults.detail(data, "已返回待办数量");
                });
    }

    /** 订单视图投影。 */
    private static Map<String, Object> view(IMealExecutionService.KitchenOrderView order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.orderId());
        data.put("orderNo", order.orderNo());
        data.put("status", "waiting".equals(order.status()) ? "waiting" : "cooking");
        data.put("version", order.version());
        data.put("candidateRound", order.candidateRound());
        data.put("assignMode", assignMode(order.assignMode()));
        data.put("customer", order.userName());
        data.put("deadlineTime", order.deadlineTime());
        data.put("candidateState", order.candidateState());
        data.put("candidateVersion", order.candidateVersion());
        List<Map<String, Object>> items = order.items().stream().map(item -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("dishName", item.dishName());
            line.put("count", item.count());
            return line;
        }).toList();
        data.put("items", items);
        data.put("itemsTotal", order.itemsTotal());
        data.put("itemsHasMore", order.itemsTotal() > items.size());
        return data;
    }

    /** 指派模式投影。 */
    private static String assignMode(String mode) {
        if (com.howe.meal.domain.MealOrder.ASSIGN_MODE_ASSIGNED.equals(mode)) {
            return "assigned";
        }
        if (com.howe.meal.domain.MealOrder.ASSIGN_MODE_REOPENED.equals(mode)) {
            return "reopened";
        }
        return "public";
    }

    /** 领域异常收敛。 */
    private static <T> T read(java.util.function.Supplier<T> action, String label) {
        try {
            return action.get();
        } catch (ToolException exception) {
            throw exception;
        } catch (ServiceException exception) {
            throw new ToolException("not_found_or_forbidden", label + "不存在或无权访问");
        } catch (RuntimeException exception) {
            throw new ToolException("internal_error", "读取" + label + "失败");
        }
    }
}
