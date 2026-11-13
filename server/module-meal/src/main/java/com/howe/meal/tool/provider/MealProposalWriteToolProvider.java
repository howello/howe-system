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
 * 新菜提案写工具提供者。
 *
 * <p>只发布提案提交与人工审核两个工具。提交工具的确认页必须明确
 * 「可能自动创建并上架菜品」；AI 调用与封面生成由平台登记的固定后续步骤执行，
 * 不在工具请求中暴露任何模型、路由或任务控制参数。</p>
 */
public final class MealProposalWriteToolProvider implements BusinessToolProvider {

    /** 能力分组：点餐写。 */
    private static final String WRITE_GROUP = "meal.write";

    /** 能力分组：提案审核。 */
    private static final String AUDIT_GROUP = "meal.proposal.audit";

    /** 结果预算。 */
    private static final int RESULT_BYTES = 32768;

    /** 提交字段。 */
    private static final Set<String> SUBMIT_FIELDS = Set.of("mode", "idempotencyKey", "name", "description", "reason",
            "notes", "categoryId");

    /** 审核字段。 */
    private static final Set<String> AUDIT_FIELDS = Set.of("mode", "idempotencyKey", "proposalId", "expectedVersion",
            "expectedStatus", "decision", "reason");

    private final ToolOperationGateway gateway;

    /**
     * 构造提案写工具提供者。
     *
     * @param gateway 操作网关
     */
    public MealProposalWriteToolProvider(ToolOperationGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String domain() {
        return "meal";
    }

    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(submit(), audit());
    }

    /** 提案提交。 */
    private ToolDefinition submit() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", Map.of("type", "string", "enum", List.of("preview", "prepare"), "default", "prepare"));
        properties.put("idempotencyKey", Map.of("type", "string", "pattern", "^[A-Za-z0-9_-]{16,64}$"));
        properties.put("name", Map.of("type", "string", "minLength", 1, "maxLength", 128));
        properties.put("description", Map.of("type", "string", "maxLength", 500));
        properties.put("reason", Map.of("type", "string", "maxLength", 500));
        properties.put("notes", Map.of("type", "string", "maxLength", 8000));
        properties.put("categoryId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$"));
        return new ToolDefinition("meal_proposal_submit", "1", "提交新菜提案",
                "提交家庭新菜提案；提交后可能由 AI 自动审核，通过时自动创建并上架菜品，"
                        + "驳回时菜品不会创建。需要浏览器确认后才提交。",
                WRITE_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "name"), false, null, null),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("提交新菜提案", false, false, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, SUBMIT_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredText(arguments, "name", 128);
                    ToolArguments.optionalText(arguments, "description", 500);
                    ToolArguments.optionalText(arguments, "reason", 500);
                    ToolArguments.optionalText(arguments, "notes", 8000);
                    optionalId(arguments, "categoryId");
                },
                (context, arguments) -> request(context, arguments, "meal_proposal_submit"));
    }

    /** 人工审核。 */
    private ToolDefinition audit() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", Map.of("type", "string", "enum", List.of("preview", "prepare"), "default", "prepare"));
        properties.put("idempotencyKey", Map.of("type", "string", "pattern", "^[A-Za-z0-9_-]{16,64}$"));
        properties.put("proposalId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$"));
        properties.put("expectedVersion", Map.of("type", "string", "pattern", "^(0|[1-9][0-9]{0,18})$"));
        properties.put("expectedStatus", Map.of("type", "string", "enum", List.of("pending", "approved", "rejected")));
        properties.put("decision", Map.of("type", "string", "enum", List.of("approved", "rejected")));
        properties.put("reason", Map.of("type", "string", "maxLength", 500));
        return new ToolDefinition("meal_proposal_audit", "1", "人工审核新菜提案",
                "对提案做出通过或驳回决定，允许对已通过/已驳回改判；"
                        + "通过与驳回会同步创建、上架或下架关联菜品，确认页会展示实际影响。",
                AUDIT_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "proposalId", "expectedVersion", "expectedStatus", "decision"),
                        false, null, null),
                ToolAccess.ofPermissions(Set.of("meal:proposal:audit")),
                new ToolDefinition.ToolAnnotations("人工审核新菜提案", false, true, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, AUDIT_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredId(arguments, "proposalId");
                    ToolArguments.requiredVersion(arguments, "expectedVersion");
                    ToolArguments.requiredEnum(arguments, "expectedStatus", Set.of("pending", "approved", "rejected"));
                    ToolArguments.requiredEnum(arguments, "decision", Set.of("approved", "rejected"));
                    ToolArguments.optionalText(arguments, "reason", 500);
                    if ("rejected".equals(arguments.get("decision"))
                            && ToolArguments.optionalText(arguments, "reason", 500) == null) {
                        throw new IllegalArgumentException("驳回时必须填写原因");
                    }
                },
                (context, arguments) -> request(context, arguments, "meal_proposal_audit"));
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
            throw new ToolException("internal_error", "准备提案操作失败");
        }
    }

    /** 业务异常收敛。 */
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
