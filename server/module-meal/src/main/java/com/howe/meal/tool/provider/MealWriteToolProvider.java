package com.howe.meal.tool.provider;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.model.ToolResult;
import com.howe.common.tool.operation.ToolOperationGateway;
import com.howe.common.tool.operation.ToolOperationReceipt;
import com.howe.common.tool.operation.ToolOperationRequest;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 点餐写工具提供者。
 *
 * <p>只发布下单、取消、接单、拒单、完成与评价六个写工具；每个工具都经
 * {@link ToolOperationGateway} 建立待确认操作，本类不直接执行业务写入。
 * 请求参数里不出现身份、家庭、授权或审核任务控制字段。</p>
 */
public final class MealWriteToolProvider implements BusinessToolProvider {

    /** 能力分组：点餐写。 */
    private static final String WRITE_GROUP = "meal.write";

    /** 能力分组：厨房写。 */
    private static final String KITCHEN_WRITE_GROUP = "meal.kitchen.write";

    /** 结果预算。 */
    private static final int RESULT_BYTES = 32768;

    /** 下单字段。 */
    private static final Set<String> SUBMIT_FIELDS = Set.of("mode", "idempotencyKey", "items", "orderRemark",
            "assignUserId");

    /** 单订单字段（取消）。 */
    private static final Set<String> CANCEL_FIELDS = Set.of("mode", "idempotencyKey", "orderId", "expectedVersion");

    /** 候选操作字段。 */
    private static final Set<String> CANDIDATE_FIELDS = Set.of("mode", "idempotencyKey", "orderId",
            "expectedVersion", "candidateVersion", "candidateRound");

    /** 完成字段。 */
    private static final Set<String> FINISH_FIELDS = Set.of("mode", "idempotencyKey", "orderId", "expectedVersion");

    /** 评价字段。 */
    private static final Set<String> REVIEW_FIELDS = Set.of("mode", "idempotencyKey", "orderId", "score", "content",
            "anonymous");

    private final ToolOperationGateway gateway;

    /**
     * 构造点餐写工具提供者。
     *
     * @param gateway 操作网关
     */
    public MealWriteToolProvider(ToolOperationGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String domain() {
        return "meal";
    }

    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(orderSubmit(), orderCancel(), orderAccept(), orderRefuse(), orderFinish(), reviewSubmit());
    }

    /** 下单。 */
    private ToolDefinition orderSubmit() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("dishId", idProperty());
        itemProperties.put("quantity", Map.of("type", "integer", "minimum", 1, "maximum", 20));
        itemProperties.put("remark", Map.of("type", "string", "maxLength", 255));
        Map<String, Object> properties = new LinkedHashMap<>();
        common(properties);
        properties.put("items", Map.of("type", "array", "minItems", 1, "maxItems", 20,
                "items", Map.of("type", "object", "additionalProperties", false, "properties", itemProperties,
                        "required", List.of("dishId", "quantity"))));
        properties.put("orderRemark", Map.of("type", "string", "maxLength", 500));
        properties.put("assignUserId", idProperty());
        return new ToolDefinition("meal_order_submit", "1", "提交家庭点餐订单",
                "按菜品下单选定的菜并可选指派厨师；服务端回查菜名、封面与上架状态后落成订单快照，"
                        + "需要浏览器确认后才创建订单并通知家人。",
                WRITE_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "items"), false, null, null),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("提交家庭点餐订单", false, false, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, SUBMIT_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.optionalText(arguments, "orderRemark", 500);
                    optionalId(arguments, "assignUserId");
                    Object items = arguments.get("items");
                    if (!(items instanceof List<?> list) || list.isEmpty() || list.size() > 20) {
                        throw new IllegalArgumentException("items 数量必须在 1 到 20 之间");
                    }
                    Set<String> dishIds = new java.util.HashSet<>();
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> line)) {
                            throw new IllegalArgumentException("items 元素格式不正确");
                        }
                        Set<String> keys = line.keySet().stream().map(String::valueOf)
                                .collect(java.util.stream.Collectors.toSet());
                        if (!Set.of("dishId", "quantity", "remark").containsAll(keys)) {
                            throw new IllegalArgumentException("items 元素包含不支持的字段");
                        }
                        Object dishId = line.get("dishId");
                        if (!(dishId instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) {
                            throw new IllegalArgumentException("参数格式不正确：dishId");
                        }
                        if (!dishIds.add(text)) {
                            throw new IllegalArgumentException("同一订单不能重复提交同一菜品");
                        }
                        Object quantity = line.get("quantity");
                        if (!(quantity instanceof Number number) || number.doubleValue() != number.intValue()
                                || number.intValue() < 1 || number.intValue() > 20) {
                            throw new IllegalArgumentException("份数必须是 1 到 20 的整数");
                        }
                        Object remark = line.get("remark");
                        if (remark != null && (!(remark instanceof String remarkText) || remarkText.length() > 255)) {
                            throw new IllegalArgumentException("单项备注过长");
                        }
                    }
                },
                (context, arguments) -> request(context, arguments, "meal_order_submit"));
    }

    /** 取消。 */
    private ToolDefinition orderCancel() {
        Map<String, Object> properties = new LinkedHashMap<>();
        common(properties);
        properties.put("orderId", idProperty());
        properties.put("expectedVersion", versionProperty());
        return new ToolDefinition("meal_order_cancel", "1", "取消我的待接单订单",
                "只能取消本人尚未被接单的订单；确认时会复核订单版本，已被接单或已被家人取消时拒绝。",
                WRITE_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "orderId", "expectedVersion"), false, null, null),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("取消我的待接单订单", false, true, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, CANCEL_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredId(arguments, "orderId");
                    ToolArguments.requiredVersion(arguments, "expectedVersion");
                },
                (context, arguments) -> request(context, arguments, "meal_order_cancel"));
    }

    /** 接单。 */
    private ToolDefinition orderAccept() {
        return candidateTool("meal_order_accept", "厨师接单",
                "以当前待响应候选人身份接单；确认时会复核订单版本、候选版本与轮次，"
                        + "两名厨师同时接单只有一人成功，其余候选人随即失效。",
                KITCHEN_WRITE_GROUP, "meal:order:dispatch");
    }

    /** 拒单。 */
    private ToolDefinition orderRefuse() {
        return candidateTool("meal_order_refuse", "厨师拒单",
                "以当前待响应候选人身份拒单；确认后按家庭配置转公开或关闭，"
                        + "转公开会重置接单截止时间并通知其他厨师。",
                KITCHEN_WRITE_GROUP, "meal:order:dispatch");
    }

    /** 完成。 */
    private ToolDefinition orderFinish() {
        Map<String, Object> properties = new LinkedHashMap<>();
        common(properties);
        properties.put("orderId", idProperty());
        properties.put("expectedVersion", versionProperty());
        return new ToolDefinition("meal_order_finish", "1", "完成制作中的订单",
                "只有该订单的实际接单人（或内置平台管理员）可以把制作中订单标记为已完成；"
                        + "确认时会复核订单版本。",
                KITCHEN_WRITE_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "orderId", "expectedVersion"), false, null, null),
                ToolAccess.ofPermissions(Set.of("meal:order:finish")),
                new ToolDefinition.ToolAnnotations("完成制作中的订单", false, false, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, FINISH_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredId(arguments, "orderId");
                    ToolArguments.requiredVersion(arguments, "expectedVersion");
                },
                (context, arguments) -> request(context, arguments, "meal_order_finish"));
    }

    /** 评价。 */
    private ToolDefinition reviewSubmit() {
        Map<String, Object> properties = new LinkedHashMap<>();
        common(properties);
        properties.put("orderId", idProperty());
        properties.put("score", Map.of("type", "integer", "minimum", 1, "maximum", 5));
        properties.put("content", Map.of("type", "string", "maxLength", 1000));
        properties.put("anonymous", Map.of("type", "boolean", "default", false));
        return new ToolDefinition("meal_review_submit", "1", "评价已完成的订单",
                "只能评价本人已完成且尚未评价的订单，一单一评；确认后写入评价并通知实际接单厨师。",
                WRITE_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "orderId", "score"), false, null, null),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("评价已完成的订单", false, false, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, REVIEW_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredId(arguments, "orderId");
                    ToolArguments.optionalInt(arguments, "score", 5, 1, 5);
                    ToolArguments.optionalText(arguments, "content", 1000);
                    ToolArguments.optionalBoolean(arguments, "anonymous", false);
                },
                (context, arguments) -> request(context, arguments, "meal_review_submit"));
    }

    /** 候选类写工具。 */
    private ToolDefinition candidateTool(String name, String title, String description, String group,
            String permission) {
        Map<String, Object> properties = new LinkedHashMap<>();
        common(properties);
        properties.put("orderId", idProperty());
        properties.put("expectedVersion", versionProperty());
        properties.put("candidateVersion", versionProperty());
        properties.put("candidateRound", versionProperty());
        return new ToolDefinition(name, "1", title, description, group,
                new ToolInputSchema("object", properties, List.of("idempotencyKey", "orderId", "expectedVersion",
                        "candidateVersion", "candidateRound"), false, null, null),
                ToolAccess.ofPermissions(Set.of(permission)),
                new ToolDefinition.ToolAnnotations(title, false, false, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, CANDIDATE_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredId(arguments, "orderId");
                    ToolArguments.requiredVersion(arguments, "expectedVersion");
                    ToolArguments.requiredVersion(arguments, "candidateVersion");
                    ToolArguments.requiredVersion(arguments, "candidateRound");
                },
                (context, arguments) -> request(context, arguments, name));
    }

    /** 公共写工具字段。 */
    private static void common(Map<String, Object> properties) {
        properties.put("mode", Map.of("type", "string", "enum", List.of("preview", "prepare"), "default", "prepare"));
        properties.put("idempotencyKey", Map.of("type", "string", "pattern", "^[A-Za-z0-9_-]{16,64}$"));
    }

    /** 正整数字符串 ID。 */
    private static Map<String, Object> idProperty() {
        return Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$");
    }

    /** 非负版本字符串。 */
    private static Map<String, Object> versionProperty() {
        return Map.of("type", "string", "pattern", "^(0|[1-9][0-9]{0,18})$");
    }

    /** 读取可选 ID。 */
    private static Long optionalId(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("参数格式不正确：" + name);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("参数超出范围：" + name);
        }
    }

    /** 统一把工具参数转换为操作申请并投影固定回执。 */
    private ToolResult request(com.howe.common.tool.model.ToolExecutionContext context,
            Map<String, Object> arguments, String toolName) {
        String mode = ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
        String key = ToolArguments.requiredText(arguments, "idempotencyKey", 64);
        Map<String, Object> business = new LinkedHashMap<>(arguments);
        business.remove("mode");
        business.remove("idempotencyKey");
        try {
            ToolOperationRequest request = new ToolOperationRequest(toolName, "1",
                    "preview".equals(mode) ? ToolOperationRequest.Mode.PREVIEW : ToolOperationRequest.Mode.PREPARE,
                    key, business);
            ToolOperationReceipt receipt = gateway.request(context, request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("operationId", receipt.operationId());
            data.put("state", receipt.state() == null ? null : receipt.state().name());
            data.put("stage", receipt.stage());
            data.put("confirmationRequired", receipt.confirmationRequired());
            data.put("confirmationUrl", receipt.confirmationUrl() == null ? null
                    : receipt.confirmationUrl().toString());
            data.put("prepareExpiresAt", receipt.prepareExpiresAt());
            data.put("summary", receipt.summary());
            return ToolResults.detail(data, receipt.state() == ToolOperationReceipt.State.PREVIEW
                    ? "已生成预览，尚未创建操作" : "操作已准备，等待浏览器确认");
        } catch (ToolException exception) {
            throw exception;
        } catch (ServiceException exception) {
            throw translate(exception);
        } catch (RuntimeException exception) {
            throw new ToolException("internal_error", "准备点餐操作失败");
        }
    }

    /** 业务异常收敛为固定错误码。 */
    private static ToolException translate(ServiceException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("已变化") || message.contains("已刷新")) {
            return new ToolException("version_conflict", "内容已发生变化，请重新预览");
        }
        if (message.contains("不存在") || message.contains("无权")) {
            return new ToolException("not_found_or_forbidden", "对象不存在或无权访问");
        }
        return new ToolException("state_conflict", "当前状态不允许该操作");
    }
}
