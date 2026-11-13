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
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.MealReview;
import com.howe.meal.domain.vo.MealOrderAssignee;
import com.howe.meal.tool.dto.MealReadDtos.AssigneeView;
import com.howe.meal.tool.dto.MealReadDtos.CategoryView;
import com.howe.meal.tool.dto.MealReadDtos.DishDetail;
import com.howe.meal.tool.dto.MealReadDtos.DishSummary;
import com.howe.meal.tool.dto.MealReadDtos.OrderItemView;
import com.howe.meal.tool.dto.MealReadDtos.OrderView;
import com.howe.meal.tool.dto.MealReadDtos.ProposalDetail;
import com.howe.meal.tool.dto.MealReadDtos.ProposalSummary;
import com.howe.meal.tool.dto.MealReadDtos.ReviewView;
import com.howe.meal.tool.service.IMealReadService;
import com.howe.meal.tool.support.MealStructuredFields;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 点餐只读工具提供者。
 *
 * <p>发布 11 个只读工具，覆盖分类、菜品、招牌、本人订单、本人提案、本人评价与可指派厨师。
 * 本人列表固定 {@code scope=user}，即使是平台账号也不解除 {@code user_id} 条件；
 * 详情保留原有家庭归属守卫。</p>
 *
 * <p>本类只依赖 common 工具契约与本模块读取服务，不引用 MCP、SDK 或 OAuth 类型。</p>
 */
public final class MealReadToolProvider implements BusinessToolProvider {

    /** 能力分组。 */
    private static final String CAPABILITY_GROUP = "meal.read";

    /** 结果预算：列表。 */
    private static final int LIST_BYTES = 65536;

    /** 结果预算：详情。 */
    private static final int DETAIL_BYTES = 131072;

    /** 列表内每单明细上限。 */
    private static final int LIST_ITEM_LIMIT = 10;

    /** 详情内每单明细上限。 */
    private static final int DETAIL_ITEM_LIMIT = 100;

    /** 评价正文上限。 */
    private static final int REVIEW_CONTENT_CHARS = 1000;

    /** 菜品名摘要上限。 */
    private static final int DISH_NAMES_CHARS = 500;

    /** 本人订单日期跨度上限（天）。 */
    private static final int MAX_RANGE_DAYS = 90;

    /** 默认日期跨度（天）。 */
    private static final int DEFAULT_RANGE_DAYS = 90;

    /** 时间输出格式。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 日期输入格式：带时区的 ISO-8601。 */
    private static final Set<String> CATEGORY_LIST_FIELDS = Set.of("status", "page", "pageSize");

    /** 菜品列表输入字段。 */
    private static final Set<String> DISH_LIST_FIELDS = Set.of("name", "categoryId", "status", "page", "pageSize");

    /** 订单列表输入字段。 */
    private static final Set<String> ORDER_LIST_FIELDS = Set.of("status", "createdFrom", "createdTo", "page",
            "pageSize");

    /** 提案列表输入字段。 */
    private static final Set<String> PROPOSAL_LIST_FIELDS = Set.of("status", "page", "pageSize");

    /** 分页字段。 */
    private static final Set<String> PAGING_FIELDS = Set.of("page", "pageSize");

    /** 订单状态映射：对外枚举 → 库中值。 */
    private static final Map<String, String> ORDER_STATUS = Map.of("waiting", "0", "cooking", "1", "finished", "2",
            "canceled", "3", "unaccepted", "4");

    /** 订单关闭原因映射：库中值 → 对外枚举。 */
    private static final Map<String, String> CLOSE_REASON = Map.of("1", "canceled", "2", "timeout", "3",
            "all_refused");

    /** 提案状态映射：对外枚举 → 库中值。 */
    private static final Map<String, String> PROPOSAL_STATUS = Map.of("pending", "0", "approved", "1", "rejected",
            "2");

    /** 分类状态映射：对外枚举 → 库中值。 */
    private static final Map<String, String> CATEGORY_STATUS = Map.of("enabled", "0", "disabled", "1");

    /** 菜品状态映射：对外枚举 → 库中值。 */
    private static final Map<String, String> DISH_STATUS = Map.of("available", "0", "unavailable", "1");

    private final IMealReadService reads;

    /**
     * 构造点餐只读工具提供者。
     *
     * @param reads 只读业务读取服务
     */
    public MealReadToolProvider(IMealReadService reads) {
        this.reads = Objects.requireNonNull(reads);
    }

    /**
     * 返回领域标识。
     *
     * @return 领域标识
     */
    @Override
    public String domain() {
        return "meal";
    }

    /**
     * 返回 11 个点餐只读工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(categoryList(), categoryGet(), dishList(), dishGet(), signatureList(), orderMyList(), orderGet(),
                proposalMyList(), proposalGet(), reviewMyList(), assigneeList());
    }

    /**
     * 分类列表。
     *
     * @return 工具定义
     */
    private ToolDefinition categoryList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "string", "enum", List.of("enabled", "disabled")));
        paging(properties);
        return new ToolDefinition("meal_category_list", "1", "查询菜品分类列表",
                "查询公共分类与当前家庭可见分类；家庭账号必须有有效家庭，否则失败关闭。",
                CAPABILITY_GROUP, schema(properties, List.of()),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询菜品分类列表", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, CATEGORY_LIST_FIELDS);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    enumValue(arguments, "status", List.of("enabled", "disabled"));
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    String status = lookup(CATEGORY_STATUS, enumValue(arguments, "status", List.of("enabled", "disabled")));
                    IMealReadService.PageResult<MealCategory> result = read(
                            () -> reads.listCategories(page, pageSize, status), "分类列表");
                    List<CategoryView> data = result.items().stream().map(MealReadToolProvider::category).toList();
                    return pageResult(data, page, pageSize, result, "共 " + result.total() + " 个分类");
                });
    }

    /**
     * 分类详情。
     *
     * @return 工具定义
     */
    private ToolDefinition categoryGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("categoryId", idProperty());
        return new ToolDefinition("meal_category_get", "1", "读取菜品分类详情",
                "读取单个分类的名称、说明、图标、排序、状态与可见性；跨家庭分类不可见。",
                CAPABILITY_GROUP, schema(properties, List.of("categoryId")),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("读取菜品分类详情", true, false, true, false, true),
                Duration.ofSeconds(10), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, Set.of("categoryId"));
                    ToolArguments.requiredId(arguments, "categoryId");
                },
                (context, arguments) -> {
                    Long categoryId = ToolArguments.requiredId(arguments, "categoryId");
                    MealCategory category = read(() -> reads.getCategory(categoryId), "分类");
                    return ToolResults.detail(category(category), "已返回分类详情");
                });
    }

    /**
     * 菜品列表。
     *
     * @return 工具定义
     */
    private ToolDefinition dishList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string", "maxLength", 100));
        properties.put("categoryId", idProperty());
        properties.put("status", Map.of("type", "string", "enum", List.of("available", "unavailable")));
        paging(properties);
        return new ToolDefinition("meal_dish_list", "1", "查询菜品列表",
                "查询公共菜谱与当前家庭私有菜；家庭账号必须有有效家庭，平台账号沿用既有全量可见范围。",
                CAPABILITY_GROUP, schema(properties, List.of()),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询菜品列表", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, DISH_LIST_FIELDS);
                    ToolArguments.optionalText(arguments, "name", 100);
                    optionalId(arguments, "categoryId");
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    enumValue(arguments, "status", List.of("available", "unavailable"));
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    String name = ToolArguments.optionalText(arguments, "name", 100);
                    Long categoryId = optionalId(arguments, "categoryId");
                    String status = lookup(DISH_STATUS, enumValue(arguments, "status", List.of("available", "unavailable")));
                    IMealReadService.PageResult<MealDish> result = read(
                            () -> reads.listDishes(page, pageSize, name, categoryId, status), "菜品列表");
                    List<DishSummary> data = result.items().stream().map(MealReadToolProvider::dishSummary).toList();
                    return pageResult(data, page, pageSize, result, "共 " + result.total() + " 道菜");
                });
    }

    /**
     * 菜品详情。
     *
     * @return 工具定义
     */
    private ToolDefinition dishGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dishId", idProperty());
        return new ToolDefinition("meal_dish_get", "1", "读取菜品详情",
                "读取单道菜的可见字段与结构化用料、做法、小贴士；跨家庭私有菜不可见。",
                CAPABILITY_GROUP, schema(properties, List.of("dishId")),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("读取菜品详情", true, false, true, false, true),
                Duration.ofSeconds(10), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, Set.of("dishId"));
                    ToolArguments.requiredId(arguments, "dishId");
                },
                (context, arguments) -> {
                    Long dishId = ToolArguments.requiredId(arguments, "dishId");
                    MealDish dish = read(() -> reads.getDish(dishId), "菜品");
                    return ToolResults.detail(dishDetail(dish), "已返回菜品详情");
                });
    }

    /**
     * 招牌菜：最多 3 条，无分页。
     *
     * @return 工具定义
     */
    private ToolDefinition signatureList() {
        return new ToolDefinition("meal_signature_list", "1", "查询招牌菜",
                "返回近 90 天已完成订单中出现最多的至多 3 道菜；非平台账号只包含公共菜与本家庭可见菜。",
                CAPABILITY_GROUP, ToolInputSchema.emptyObject(),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询招牌菜", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                ToolArguments::requireEmpty,
                (context, arguments) -> {
                    List<MealDish> dishes = read(reads::listSignatureDishes, "招牌菜");
                    List<DishSummary> data = dishes.stream().map(MealReadToolProvider::dishSummary).toList();
                    return ToolResults.success(data, null, false, List.of(), "共 " + data.size() + " 道招牌菜");
                });
    }

    /**
     * 本人订单列表。
     *
     * @return 工具定义
     */
    private ToolDefinition orderMyList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "string", "enum", List.of("waiting", "cooking", "finished",
                "canceled", "unaccepted")));
        properties.put("createdFrom", Map.of("type", "string", "description", "带时区的 ISO-8601 时间，含"));
        properties.put("createdTo", Map.of("type", "string", "description", "带时区的 ISO-8601 时间，不含"));
        paging(properties);
        return new ToolDefinition("meal_order_my_list", "1", "查询我的订单",
                "固定查询当前账号本人提交的订单；日期为左闭右开区间，跨度不超过 90 天，省略时默认最近 90 天。",
                CAPABILITY_GROUP, schema(properties, List.of()),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询我的订单", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, ORDER_LIST_FIELDS);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    enumValue(arguments, "status", List.copyOf(ORDER_STATUS.keySet()));
                    orderRange(arguments);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    String status = lookup(ORDER_STATUS, enumValue(arguments, "status", List.copyOf(ORDER_STATUS.keySet())));
                    java.util.Date[] range = orderRange(arguments);
                    IMealReadService.PageResult<MealOrder> result = read(
                            () -> reads.listMyOrders(page, pageSize, status, range[0], range[1]), "订单列表");
                    List<OrderView> data = orderViews(result.items(), LIST_ITEM_LIMIT);
                    List<Map<String, Object>> warnings = itemWarnings(result.items(), LIST_ITEM_LIMIT);
                    return ToolResults.success(data, ToolArguments.pageOf(page, pageSize, data.size(), result.total()),
                            result.hasMore(), warnings, "共 " + result.total() + " 个订单");
                });
    }

    /**
     * 订单详情。
     *
     * @return 工具定义
     */
    private ToolDefinition orderGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("orderId", idProperty());
        return new ToolDefinition("meal_order_get", "1", "读取订单详情",
                "读取家庭内可见的单个订单及其有界明细摘要；跨家庭订单不可见。",
                CAPABILITY_GROUP, schema(properties, List.of("orderId")),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("读取订单详情", true, false, true, false, true),
                Duration.ofSeconds(10), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, Set.of("orderId"));
                    ToolArguments.requiredId(arguments, "orderId");
                },
                (context, arguments) -> {
                    Long orderId = ToolArguments.requiredId(arguments, "orderId");
                    MealOrder order = read(() -> reads.getOrder(orderId), "订单");
                    List<MealOrderItem> items = read(() -> reads.listOrderItemSummaries(orderId, DETAIL_ITEM_LIMIT),
                            "订单明细");
                    int totalItems = read(() -> reads.countOrderItems(orderId), "订单明细");
                    List<Map<String, Object>> warnings = totalItems > items.size()
                            ? List.of(Map.of("code", "DETAIL_TRUNCATED", "message", "订单明细已按上限截断")) : List.of();
                    OrderView view = new OrderView(String.valueOf(order.getOrderId()), order.getOrderNo(),
                            ORDER_STATUS.entrySet().stream().filter(entry -> entry.getValue()
                                    .equals(order.getStatus())).map(Map.Entry::getKey).findFirst().orElse("unknown"),
                            lookup(CLOSE_REASON, order.getCloseReason()), time(order.getCreateTime()),
                            time(order.getAcceptTime()), time(order.getFinishTime()), time(order.getCancelTime()),
                            time(order.getDeadlineTime()), order.getTotalCount(),
                            items.stream().map(MealReadToolProvider::orderItem).toList(), order.getReviewed());                    return ToolResults.success(view, null, false, warnings, "已返回订单详情");
                });
    }

    /**
     * 本人提案列表。
     *
     * @return 工具定义
     */
    private ToolDefinition proposalMyList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "string", "enum", List.of("pending", "approved", "rejected")));
        paging(properties);
        return new ToolDefinition("meal_proposal_my_list", "1", "查询我的菜品提案",
                "固定查询当前账号本人提交的提案，返回状态与分类摘要。",
                CAPABILITY_GROUP, schema(properties, List.of()),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询我的菜品提案", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, PROPOSAL_LIST_FIELDS);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    enumValue(arguments, "status", List.of("pending", "approved", "rejected"));
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    String status = lookup(PROPOSAL_STATUS, enumValue(arguments, "status", List.of("pending", "approved", "rejected")));
                    IMealReadService.PageResult<MealProposal> result = read(
                            () -> reads.listMyProposals(page, pageSize, status), "提案列表");
                    List<ProposalSummary> data = result.items().stream()
                            .map(row -> new ProposalSummary(String.valueOf(row.getProposalId()), row.getName(),
                                    proposalStatus(row.getStatus()), ids(row.getCategoryIds()),
                                    row.getCategoryNames()))
                            .toList();
                    return pageResult(data, page, pageSize, result, "共 " + result.total() + " 个提案");
                });
    }

    /**
     * 提案详情。
     *
     * @return 工具定义
     */
    private ToolDefinition proposalGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("proposalId", idProperty());
        return new ToolDefinition("meal_proposal_get", "1", "读取菜品提案详情",
                "读取家庭内可见的提案内容与审核结果；跨家庭提案不可见。",
                CAPABILITY_GROUP, schema(properties, List.of("proposalId")),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("读取菜品提案详情", true, false, true, false, true),
                Duration.ofSeconds(10), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, Set.of("proposalId"));
                    ToolArguments.requiredId(arguments, "proposalId");
                },
                (context, arguments) -> {
                    Long proposalId = ToolArguments.requiredId(arguments, "proposalId");
                    MealProposal proposal = read(() -> reads.getProposal(proposalId), "提案");
                    ProposalDetail view = new ProposalDetail(String.valueOf(proposal.getProposalId()),
                            proposal.getName(), proposalStatus(proposal.getStatus()), ids(proposal.getCategoryIds()),
                            proposal.getCategoryNames(), proposal.getDescription(),
                            MealStructuredFields.split(proposal.getTags()), proposal.getDuration(),
                            proposal.getLevel(), MealStructuredFields.ingredients(proposal.getIngredients()),
                            MealStructuredFields.textArray(proposal.getSteps()),
                            MealStructuredFields.textArray(proposal.getTips()), proposal.getReason(),
                            time(proposal.getAuditTime()), proposal.getAuditRemark(),
                            proposal.getDishId() == null ? null : String.valueOf(proposal.getDishId()));
                    return ToolResults.detail(view, "已返回提案详情");
                });
    }

    /**
     * 本人评价列表。
     *
     * @return 工具定义
     */
    private ToolDefinition reviewMyList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        paging(properties);
        return new ToolDefinition("meal_review_my_list", "1", "查询我的评价",
                "固定查询当前账号本人提交的订单评价，不提供厨师视角。",
                CAPABILITY_GROUP, schema(properties, List.of()),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询我的评价", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, PAGING_FIELDS);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    IMealReadService.PageResult<MealReview> result = read(
                            () -> reads.listMyReviews(page, pageSize), "评价列表");
                    List<ReviewView> data = result.items().stream().map(MealReadToolProvider::review).toList();
                    return pageResult(data, page, pageSize, result, "共 " + result.total() + " 条评价");
                });
    }

    /**
     * 可指派厨师列表。
     *
     * @return 工具定义
     */
    private ToolDefinition assigneeList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        paging(properties);
        return new ToolDefinition("meal_assignee_list", "1", "查询可指派厨师",
                "返回当前家庭内有效且启用厨师角色的成员，只包含用户 ID 与展示名。",
                CAPABILITY_GROUP, schema(properties, List.of()),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询可指派厨师", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, PAGING_FIELDS);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    IMealReadService.PageResult<MealOrderAssignee> result = read(
                            () -> reads.listAssignees(page, pageSize), "可指派厨师");
                    List<AssigneeView> data = result.items().stream()
                            .map(row -> new AssigneeView(String.valueOf(row.userId()), row.nickName())).toList();
                    return pageResult(data, page, pageSize, result, "共 " + result.total() + " 名可指派厨师");
                });
    }

    /**
     * 构造统一结果。
     *
     * @param data 当前页数据
     * @param page 页码
     * @param pageSize 页大小
     * @param result 分页结果
     * @param summary 摘要
     * @return 工具结果
     */
    private static ToolResult pageResult(List<?> data, int page, int pageSize,
            IMealReadService.PageResult<?> result, String summary) {
        ToolPage pageInfo = ToolArguments.pageOf(page, pageSize, data.size(), result.total());
        return ToolResults.success(data, pageInfo, result.hasMore(), List.of(), summary);
    }

    /**
     * 构造输入结构。
     *
     * @param properties 属性树
     * @param required 必填字段
     * @return 输入结构
     */
    private static ToolInputSchema schema(Map<String, Object> properties, List<String> required) {
        return new ToolInputSchema("object", properties, required, false, null, null);
    }

    /**
     * 加入分页属性。
     *
     * @param properties 属性树
     */
    private static void paging(Map<String, Object> properties) {
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10000, "default", 1));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 20));
    }

    /**
     * 构造正整数字符串 ID 属性。
     *
     * @return 属性定义
     */
    private static Map<String, Object> idProperty() {
        return Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$");
    }

    /**
     * 读取可选 ID。
     *
     * @param arguments 入参
     * @param name 字段名
     * @return ID；未提供时为 null
     */
    private static Long optionalId(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        return ToolArguments.requiredId(arguments, name);
    }

    /**
     * 读取枚举入参并校验取值。
     *
     * @param arguments 入参
     * @param name 字段名
     * @param allowed 允许的取值
     * @return 入参值；未提供时为 null
     */
    private static String enumValue(Map<String, Object> arguments, String name, List<String> allowed) {
        String value = ToolArguments.optionalText(arguments, name, 32);
        if (value == null) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("参数取值不合法：" + name);
        }
        return value;
    }

    /**
     * 解析并校验本人订单的日期区间。
     *
     * <p>两个时间必须同时省略或同时提供；同时省略时默认最近 90 天。
     * 区间左闭右开且跨度不超过 90 天。返回 {@link java.util.Date}，
     * 由 MyBatis 原生 {@code DateTypeHandler} 绑定为 TIMESTAMP。</p>
     *
     * @param arguments 入参
     * @return 长度 2 的数组：下界与上界；都省略时按默认 90 天窗口填充
     */
    private static java.util.Date[] orderRange(Map<String, Object> arguments) {
        String from = ToolArguments.optionalText(arguments, "createdFrom", 40);
        String to = ToolArguments.optionalText(arguments, "createdTo", 40);
        if (from == null && to == null) {
            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime end = LocalDate.now(zone).plusDays(1).atStartOfDay();
            LocalDateTime start = end.minusDays(DEFAULT_RANGE_DAYS);
            return new java.util.Date[] { toDate(start), toDate(end) };
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("createdFrom 与 createdTo 必须同时提供");
        }
        LocalDateTime start = parseTime(from);
        LocalDateTime end = parseTime(to);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("createdFrom 必须早于 createdTo");
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new IllegalArgumentException("日期跨度不能超过 90 天");
        }
        return new java.util.Date[] { toDate(start), toDate(end) };
    }

    /**
     * 把本地时间转换为 Date。
     *
     * @param value 本地时间
     * @return Date
     */
    private static java.util.Date toDate(LocalDateTime value) {
        return java.util.Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 解析必须带时区的 ISO-8601 时间。
     *
     * @param value 时间文本
     * @return 服务端本地时间
     */
    private static LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.ofInstant(java.time.OffsetDateTime.parse(value).toInstant(), ZoneId.systemDefault());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间必须带时区，格式为 ISO-8601");
        }
    }

    /**
     * 把订单列表映射为视图并附加截断警告。
     *
     * @param orders 订单
     * @param limit 每单明细上限
     * @return 订单视图
     */
    private List<OrderView> orderViews(List<MealOrder> orders, int limit) {
        Long[] orderIds = orders.stream().map(MealOrder::getOrderId).filter(Objects::nonNull).toArray(Long[]::new);
        Map<Long, List<MealOrderItem>> items = new HashMap<>();
        if (orderIds.length > 0) {
            read(() -> reads.listOrderItemSummaries(orderIds, limit), "订单明细").forEach(item -> items
                    .computeIfAbsent(item.getOrderId(), key -> new ArrayList<>()).add(item));
        }
        return orders.stream().map(order -> new OrderView(String.valueOf(order.getOrderId()), order.getOrderNo(),
                ORDER_STATUS.entrySet().stream().filter(entry -> entry.getValue().equals(order.getStatus()))
                        .map(Map.Entry::getKey).findFirst().orElse("unknown"),
                lookup(CLOSE_REASON, order.getCloseReason()), time(order.getCreateTime()), time(order.getAcceptTime()),
                time(order.getFinishTime()), time(order.getCancelTime()), time(order.getDeadlineTime()),
                order.getTotalCount(),
                items.getOrDefault(order.getOrderId(), List.of()).stream().limit(limit)
                        .map(MealReadToolProvider::orderItem).toList(),
                order.getReviewed())).toList();
    }

    /**
     * 计算订单列表的明细截断警告。
     *
     * @param orders 订单
     * @param limit 每单明细上限
     * @return 警告列表
     */
    private List<Map<String, Object>> itemWarnings(List<MealOrder> orders, int limit) {
        Long[] orderIds = orders.stream().map(MealOrder::getOrderId).filter(Objects::nonNull).toArray(Long[]::new);
        if (orderIds.length == 0) {
            return List.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : read(() -> reads.countOrderItems(orderIds), "订单明细统计")) {
            Object id = row.get("orderId");
            Object count = row.get("cnt");
            if (id instanceof Number idValue && count instanceof Number countValue) {
                counts.put(idValue.longValue(), countValue.intValue());
            }
        }
        boolean truncated = orders.stream()
                .anyMatch(order -> counts.getOrDefault(order.getOrderId(), 0) > limit);
        return truncated
                ? List.of(Map.of("code", "DETAIL_TRUNCATED", "message", "部分订单明细已按列表上限截断")) : List.of();
    }

    /**
     * 映射分类视图。
     *
     * @param category 分类
     * @return 视图
     */
    private static CategoryView category(MealCategory category) {
        return new CategoryView(String.valueOf(category.getCategoryId()), category.getName(),
                category.getDescription(), category.getIcon(), category.getSort(),
                "1".equals(category.getStatus()) ? "disabled" : "enabled", visibility(category.getDeptId()));
    }

    /**
     * 映射菜品摘要。
     *
     * @param dish 菜品
     * @return 摘要
     */
    private static DishSummary dishSummary(MealDish dish) {
        return new DishSummary(String.valueOf(dish.getDishId()), dish.getName(), dish.getDescription(),
                "1".equals(dish.getStatus()) ? "unavailable" : "available", visibility(dish.getDeptId()),
                ids(dish.getCategoryIds()), dish.getDataVersion());
    }

    /**
     * 映射菜品详情。
     *
     * @param dish 菜品
     * @return 详情
     */
    private static DishDetail dishDetail(MealDish dish) {
        return new DishDetail(String.valueOf(dish.getDishId()), dish.getName(), dish.getDescription(),
                "1".equals(dish.getStatus()) ? "unavailable" : "available", visibility(dish.getDeptId()),
                ids(dish.getCategoryIds()), dish.getDataVersion(),
                MealStructuredFields.ingredients(dish.getIngredients()),
                MealStructuredFields.textArray(dish.getSteps()), MealStructuredFields.textArray(dish.getTips()),
                dish.getDuration(), dish.getLevel(), dish.getServe(), dish.getKcal(),
                MealStructuredFields.split(dish.getTags()));
    }

    /**
     * 映射订单明细摘要。
     *
     * @param item 明细
     * @return 摘要
     */
    private static OrderItemView orderItem(MealOrderItem item) {
        return new OrderItemView(item.getDishId() == null ? null : String.valueOf(item.getDishId()),
                item.getDishName(), item.getCount());
    }

    /**
     * 映射评价视图，正文按码点截断。
     *
     * @param review 评价
     * @return 视图
     */
    private static ReviewView review(MealReview review) {
        return new ReviewView(String.valueOf(review.getReviewId()),
                review.getOrderId() == null ? null : String.valueOf(review.getOrderId()), review.getScore(),
                truncate(review.getContent(), REVIEW_CONTENT_CHARS),
                "1".equals(review.getAnonymous()), time(review.getCreateTime()),
                truncate(review.getDishNames(), DISH_NAMES_CHARS));
    }

    /**
     * 把 ID 集合转为字符串数组。
     *
     * @param values ID 集合
     * @return 字符串数组
     */
    private static List<String> ids(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    /**
     * 根据归属部门推导可见性。
     *
     * @param deptId 归属部门
     * @return {@code public} 或 {@code family}
     */
    private static String visibility(Long deptId) {
        return deptId != null && deptId == 0L ? "public" : "family";
    }

    /**
     * 把库中提案状态转为对外枚举。
     *
     * @param status 库中状态
     * @return 对外状态
     */
    private static String proposalStatus(String status) {
        if ("1".equals(status)) {
            return "approved";
        }
        if ("2".equals(status)) {
            return "rejected";
        }
        return "pending";
    }

    /**
     * 按 Unicode 码点截断文本。
     *
     * @param text 原始文本
     * @param maxCodePoints 最大码点数
     * @return 截断后的文本；为空时返回 null
     */
    private static String truncate(String text, int maxCodePoints) {
        if (text == null) {
            return null;
        }
        int count = text.codePointCount(0, text.length());
        if (count <= maxCodePoints) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    /**
     * 把时间格式化为带偏移量的 ISO-8601。
     *
     * @param value 时间
     * @return 格式化文本；为空时返回 null
     */
    private static String time(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return TIME_FORMAT.format(value.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    /**
     * null 安全的映射查找。
     *
     * <p>{@code Map.of} 构造的不可变集合在 {@code get(null)} 时会抛 NPE，
     * 而订单等字段允许为空，因此统一走本方法。</p>
     *
     * @param table 映射表
     * @param key 键，可为 null
     * @return 映射值；键为空或未命中时返回 null
     */
    private static String lookup(Map<String, String> table, String key) {
        return key == null ? null : table.get(key);
    }

    /**
     * 执行只读查询并把业务异常收敛为稳定工具错误。
     *
     * @param action 查询动作
     * @param label 对象名称
     * @param <T> 返回类型
     * @return 查询结果
     */
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
