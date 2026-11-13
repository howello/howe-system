package com.howe.blog.tool.provider;

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
 * 博客说说写工具提供者。
 *
 * <p>只发布说说创建与说说更新两个写工具；草稿相关写工具本期不开放。
 * 请求通过 common 操作网关建立待确认操作，本类不直接执行业务写入。</p>
 */
public final class BlogTalkWriteToolProvider implements BusinessToolProvider {

    /** 能力分组：博客写。 */
    private static final String WRITE_GROUP = "blog.write";

    /** 结果预算。 */
    private static final int RESULT_BYTES = 32768;

    /** 说说创建输入字段。 */
    private static final Set<String> CREATE_FIELDS = Set.of("mode", "idempotencyKey", "content", "status", "tags",
            "pubDate", "isTop");

    /** 说说更新输入字段。 */
    private static final Set<String> UPDATE_FIELDS = Set.of("mode", "idempotencyKey", "talkId", "expectedVersion",
            "patch");

    private final ToolOperationGateway gateway;

    /**
     * 构造说说写工具提供者。
     *
     * @param gateway 操作网关
     */
    public BlogTalkWriteToolProvider(ToolOperationGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String domain() {
        return "blog";
    }

    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(talkCreate(), talkUpdate());
    }

    /** 说说创建。 */
    private ToolDefinition talkCreate() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", Map.of("type", "string", "enum", List.of("preview", "prepare"), "default", "prepare"));
        properties.put("idempotencyKey", Map.of("type", "string", "pattern", "^[A-Za-z0-9_-]{16,64}$"));
        properties.put("content", Map.of("type", "string", "minLength", 1, "maxLength", 20000));
        properties.put("status", Map.of("type", "string", "enum", List.of("published", "unpublished")));
        properties.put("tags", Map.of("type", "array", "maxItems", 20,
                "items", Map.of("type", "string", "maxLength", 50)));
        properties.put("isTop", Map.of("type", "boolean", "default", false));
        return new ToolDefinition("blog_talk_create", "1", "创建博客说说",
                "创建一条说说；status 必须显式选择公开或隐藏，需要浏览器确认后才生效。",
                WRITE_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "content", "status"), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:talk:add")),
                new ToolDefinition.ToolAnnotations("创建博客说说", false, false, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, CREATE_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredText(arguments, "content", 20000);
                    ToolArguments.requiredEnum(arguments, "status", Set.of("published", "unpublished"));
                    ToolArguments.optionalTextList(arguments, "tags", 20, 50);
                    ToolArguments.optionalBoolean(arguments, "isTop", false);
                },
                (context, arguments) -> request(context, arguments, "blog_talk_create"));
    }

    /** 说说更新。 */
    private ToolDefinition talkUpdate() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", Map.of("type", "string", "enum", List.of("preview", "prepare"), "default", "prepare"));
        properties.put("idempotencyKey", Map.of("type", "string", "pattern", "^[A-Za-z0-9_-]{16,64}$"));
        properties.put("talkId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$"));
        properties.put("expectedVersion", Map.of("type", "string", "pattern", "^(0|[1-9][0-9]{0,18})$"));
        properties.put("patch", Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("content", Map.of("type", "string", "maxLength", 20000),
                        "tags", Map.of("type", "array", "items", Map.of("type", "string", "maxLength", 50)),
                        "status", Map.of("type", "string", "enum", List.of("published", "unpublished")),
                        "isTop", Map.of("type", "boolean"))));
        return new ToolDefinition("blog_talk_update", "1", "更新博客说说",
                "按版本更新说说；公开状态变化会在确认页明示。",
                WRITE_GROUP, new ToolInputSchema("object", properties,
                        List.of("idempotencyKey", "talkId", "expectedVersion", "patch"), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:talk:edit")),
                new ToolDefinition.ToolAnnotations("更新博客说说", false, true, true, false, false),
                Duration.ofSeconds(5), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, UPDATE_FIELDS);
                    ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
                    ToolArguments.requiredText(arguments, "idempotencyKey", 64);
                    ToolArguments.requiredId(arguments, "talkId");
                    ToolArguments.requiredVersion(arguments, "expectedVersion");
                    Object patch = arguments.get("patch");
                    if (!(patch instanceof Map<?, ?> map) || map.isEmpty()) {
                        throw new IllegalArgumentException("patch 不能为空");
                    }
                },
                (context, arguments) -> request(context, arguments, "blog_talk_update"));
    }

    /** 统一把工具参数转换为操作申请，并投影固定回执。 */
    private ToolResult request(com.howe.common.tool.model.ToolExecutionContext context,
            Map<String, Object> arguments, String toolName) {
        String mode = ToolArguments.optionalEnum(arguments, "mode", Set.of("preview", "prepare"));
        String key = ToolArguments.requiredText(arguments, "idempotencyKey", 64);
        Map<String, Object> business = new LinkedHashMap<>(arguments);
        business.remove("mode");
        business.remove("idempotencyKey");
        ToolOperationRequest request = new ToolOperationRequest(toolName, "1",
                "preview".equals(mode) ? ToolOperationRequest.Mode.PREVIEW : ToolOperationRequest.Mode.PREPARE,
                key, business);
        ToolOperationReceipt receipt = gateway.request(context, request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operationId", receipt.operationId());
        data.put("state", receipt.state() == null ? null : receipt.state().name());
        data.put("stage", receipt.stage());
        data.put("confirmationRequired", receipt.confirmationRequired());
        data.put("confirmationUrl", receipt.confirmationUrl() == null ? null : receipt.confirmationUrl().toString());
        data.put("prepareExpiresAt", receipt.prepareExpiresAt());
        data.put("summary", receipt.summary());
        return ToolResults.detail(data, receipt.state() == ToolOperationReceipt.State.PREVIEW
                ? "已生成预览，尚未创建操作" : "操作已准备，等待浏览器确认");
    }
}
