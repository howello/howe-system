package com.howe.system.tool.provider;

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
 * 公告写工具提供者。
 *
 * <p>只发布本人公告批量已读；公告正文 HTML 不进入工具输入或结果。</p>
 */
public final class SystemNoticeWriteToolProvider implements BusinessToolProvider {

    /** 能力分组。 */
    private static final String GROUP = "system.notice.write";

    private final ToolOperationGateway gateway;

    /**
     * 构造公告写工具提供者。
     *
     * @param gateway 操作网关
     */
    public SystemNoticeWriteToolProvider(ToolOperationGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String domain() {
        return "system";
    }

    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(markRead());
    }

    /** 批量已读。 */
    private ToolDefinition markRead() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", Map.of("type", "string", "enum", List.of("preview", "prepare"), "default", "prepare"));
        properties.put("idempotencyKey", Map.of("type", "string", "pattern", "^[A-Za-z0-9_-]{16,64}$"));
        properties.put("noticeIds", Map.of("type", "array", "minItems", 1, "maxItems", 20,
                "items", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$")));
        return new ToolDefinition("system_notice_mark_read", "1", "标记公告已读",
                "把 1—20 条正常公告标记为当前账号已读；任一公告不存在或已停用时整批拒绝。",
                GROUP, new ToolInputSchema("object", properties, List.of("idempotencyKey", "noticeIds"), false,
                        null, null),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("标记公告已读", false, false, true, false, false),
                Duration.ofSeconds(5), 32768,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, Set.of("mode", "idempotencyKey", "noticeIds"));
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    Object value = arguments.get("noticeIds");
                    if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 20) {
                        throw new IllegalArgumentException("noticeIds 数量必须在 1 到 20 之间");
                    }
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (Object item : list) {
                        if (!(item instanceof String text) || !text.matches("^[1-9][0-9]{0,18}$")
                                || !seen.add(text)) {
                            throw new IllegalArgumentException("noticeIds 元素格式不正确或重复");
                        }
                    }
                },
                (context, arguments) -> {
                    String mode = ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    String key = ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    Map<String, Object> business = new LinkedHashMap<>();
                    business.put("noticeIds", arguments.get("noticeIds"));
                    ToolOperationReceipt receipt = gateway.request(context, new ToolOperationRequest(
                            "system_notice_mark_read", "1", "preview".equals(mode)
                                    ? ToolOperationRequest.Mode.PREVIEW : ToolOperationRequest.Mode.PREPARE,
                            key, business));
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("operationId", receipt.operationId());
                    data.put("state", receipt.state() == null ? null : receipt.state().name());
                    data.put("stage", receipt.stage());
                    data.put("confirmationRequired", receipt.confirmationRequired());
                    data.put("confirmationUrl", receipt.confirmationUrl() == null
                            ? null : receipt.confirmationUrl().toString());
                    data.put("prepareExpiresAt", receipt.prepareExpiresAt());
                    data.put("summary", receipt.summary());
                    ToolResult result = ToolResults.detail(data, receipt.state() == ToolOperationReceipt.State.PREVIEW
                            ? "已生成预览，尚未创建操作" : "操作已准备，等待浏览器确认");
                    return result;
                });
    }
}
