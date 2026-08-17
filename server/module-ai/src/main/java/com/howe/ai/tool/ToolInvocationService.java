package com.howe.ai.tool;

import com.howe.ai.contract.AiToolProvider;
import com.howe.ai.contract.ToolRequest;
import com.howe.ai.contract.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Tool 安全调用执行器：在调用实际 Provider 之前，按固定顺序裁决每一道安全关卡。
 *
 * <p>调用链（设计文档 §6）：</p>
 * <ol>
 *   <li>Registry 查找 —— 未知 Tool 拒绝</li>
 *   <li>Agent 白名单 —— Tool 不在 Agent 发布版本固化的允许集合内则拒绝</li>
 *   <li>输入长度上限 —— 超大入参拒绝，防止借助参数投递攻击</li>
 *   <li>Provider 执行 —— 仅 READ_ONLY Tool，受调用预算约束</li>
 *   <li>输出长度上限 —— 超长输出截断，防止单次调用灌爆上下文</li>
 *   <li>审计 —— 成功与拒绝都记录</li>
 * </ol>
 *
 * <p><b>核心安全不变量</b>：白名单裁决只依赖 {@link ToolInvocationContext#allowedTools()}
 * 与 Tool 名，{@code argumentsJson}（即模型输入）<b>绝不</b>参与裁决——因此模型无法通过
 * 改变参数绕过白名单。这一性质由 {@link #denyIfNotAllowed} 保证并由测试守护。</p>
 */
@Service
public class ToolInvocationService {

    private static final Logger log = LoggerFactory.getLogger(ToolInvocationService.class);

    /** 入参 JSON 的硬上限（字节），超过直接拒绝。 */
    public static final int MAX_ARGUMENT_BYTES = 8 * 1024;

    /** 单次 Tool 输出的硬上限（字符），超过截断并标注。 */
    public static final int MAX_OUTPUT_CHARS = 8 * 1024;

    private final ToolRegistry registry;
    private final ToolAuditRecorder audit;

    @Autowired
    public ToolInvocationService(ToolRegistry registry, ToolAuditRecorder audit) {
        this.registry = Objects.requireNonNull(registry, "ToolRegistry 不能为空");
        this.audit = Objects.requireNonNull(audit, "ToolAuditRecorder 不能为空");
    }

    /**
     * 执行一次受安全调用链保护的 Tool 调用。
     *
     * @return 调用结果（拒绝时 success=false 且带拒绝原因；放行执行则透传 Provider 结果，超长输出被截断）
     */
    public ToolResult invoke(ToolInvocationContext ctx) {
        Objects.requireNonNull(ctx, "调用上下文不能为空");

        // 关卡 1：Registry 查找 —— 未知 Tool 拒绝
        if (!registry.isKnown(ctx.toolName())) {
            return denyAndAudit(ctx, "UNKNOWN_TOOL", "未注册的 Tool: " + ctx.toolName());
        }
        // 关卡 2：Agent 白名单 —— 模型输入不参与裁决
        if (denyIfNotAllowed(ctx)) {
            return denyAndAudit(ctx, "NOT_ALLOWED",
                "Tool 不在 Agent 白名单内: " + ctx.toolName());
        }
        // 关卡 3：入参长度上限
        if (ctx.argumentsJson().length() > MAX_ARGUMENT_BYTES) {
            return denyAndAudit(ctx, "ARGUMENT_TOO_LARGE", "工具入参超出上限");
        }

        // 关卡 4：Provider 执行
        AiToolProvider provider = registry.findProvider(ctx.toolName()).orElseThrow();
        ToolResult result;
        try {
            result = provider.invoke(new ToolRequest(ctx.toolName(), ctx.argumentsJson()));
        } catch (Exception e) {
            log.warn("Tool 执行异常 tool={} agent={}", ctx.toolName(), ctx.agentId(), e);
            ToolResult failure = ToolResult.failure("TOOL_ERROR", e.getMessage());
            audit.record(new ToolAuditEntry(ctx.toolName(), ctx.agentId(), ctx.userId(),
                true, null, false, failure.errorMessage(), System.currentTimeMillis()));
            return failure;
        }

        // 关卡 5：输出长度上限（截断而非拒绝，保留可用部分）
        ToolResult sanitized = sanitizeOutput(result);
        if (!result.success()) {
            audit.record(new ToolAuditEntry(ctx.toolName(), ctx.agentId(), ctx.userId(),
                true, null, false, result.errorMessage(), System.currentTimeMillis()));
        } else {
            audit.record(new ToolAuditEntry(ctx.toolName(), ctx.agentId(), ctx.userId(),
                true, null, true, null, System.currentTimeMillis()));
        }
        return sanitized;
    }

    /**
     * 白名单裁决：只看 {@link ToolInvocationContext#allowedTools()} 与 Tool 名。
     * 故意不接受 {@code argumentsJson}，从结构上保证模型输入无法改变拒绝决定。
     */
    private boolean denyIfNotAllowed(ToolInvocationContext ctx) {
        return !ctx.allowedTools().contains(ctx.toolName());
    }

    private ToolResult denyAndAudit(ToolInvocationContext ctx, String code, String reason) {
        audit.record(new ToolAuditEntry(ctx.toolName(), ctx.agentId(), ctx.userId(),
            false, code + ": " + reason, false, null, System.currentTimeMillis()));
        return ToolResult.failure(code, reason);
    }

    private ToolResult sanitizeOutput(ToolResult result) {
        if (!result.success() || result.content() == null) {
            return result;
        }
        if (result.content().length() <= MAX_OUTPUT_CHARS) {
            return result;
        }
        return ToolResult.success(result.content().substring(0, MAX_OUTPUT_CHARS)
            + "...[输出已截断]");
    }
}
