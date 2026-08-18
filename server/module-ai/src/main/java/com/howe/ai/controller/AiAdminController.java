package com.howe.ai.controller;

import com.howe.ai.application.AiAdminApplicationService;
import com.howe.ai.application.AgentDraft;
import com.howe.common.utils.SecurityUtils;
import com.howe.ai.web.AgentDraftRequest;
import com.howe.ai.web.ConversationCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/admin")
@Tag(name = "管理员助手管理")
public class AiAdminController {
    private final AiAdminApplicationService service;

    public AiAdminController(AiAdminApplicationService service) { this.service = service; }

    @PostMapping("/agents")
    @PreAuthorize("@ss.hasPermi('ai:agent:edit')")
    @Operation(summary = "创建 Agent 草稿")
    public long create(@RequestBody AgentDraftRequest request) {
        service.validateDraft(new AgentDraft(request.agentKey(), request.name(), request.systemPrompt(), request.routeJson(), request.toolJson(), request.budgetJson()));
        Map<String, Object> agent = new java.util.HashMap<>();
        agent.put("agentKey", request.agentKey()); agent.put("name", request.name());
        agent.put("draftJson", request.toJson()); agent.put("createBy", "admin");
        return service.createAgent(agent);
    }

    @PutMapping("/agents/{agentId}")
    @PreAuthorize("@ss.hasPermi('ai:agent:edit')")
    @Operation(summary = "更新 Agent 草稿")
    public int update(@Parameter(description = "Agent ID") @PathVariable long agentId,
                      @Parameter(description = "期望版本") @RequestParam int expectedVersion, @RequestBody AgentDraftRequest request) {
        service.validateDraft(new AgentDraft(request.agentKey(), request.name(), request.systemPrompt(), request.routeJson(), request.toolJson(), request.budgetJson()));
        Map<String, Object> agent = new java.util.HashMap<>(); agent.put("draftJson", request.toJson()); agent.put("updateBy", "admin");
        return service.updateAgent(agentId, expectedVersion, agent);
    }

    @GetMapping("/agents/{agentId}")
    @PreAuthorize("@ss.hasPermi('ai:agent:view')")
    @Operation(summary = "查询 Agent 草稿")
    public Map<String, Object> detail(@Parameter(description = "Agent ID") @PathVariable long agentId) { return service.getAgent(agentId); }

    @PostMapping("/agents/{agentId}/publish")
    @PreAuthorize("@ss.hasPermi('ai:agent:publish')")
    @Operation(summary = "发布 Agent 版本")
    public int publish(@Parameter(description = "Agent ID") @PathVariable long agentId, @Parameter(description = "版本号") @RequestParam int versionNo) { return service.publishAgent(agentId, versionNo, "admin"); }

    @PostMapping("/agents/{agentId}/disable")
    @PreAuthorize("@ss.hasPermi('ai:agent:disable')")
    @Operation(summary = "停用 Agent")
    public int disable(@Parameter(description = "Agent ID") @PathVariable long agentId) { return service.disableAgent(agentId); }

    @GetMapping("/agents/{agentId}/versions")
    @PreAuthorize("@ss.hasPermi('ai:agent:view')")
    @Operation(summary = "查询 Agent 版本列表")
    public List<Map<String, Object>> versions(@Parameter(description = "Agent ID") @PathVariable long agentId, @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum, @Parameter(description = "页大小") @RequestParam(defaultValue = "10") int pageSize) { return service.listAgentVersions(agentId, pageNum, pageSize); }

    @GetMapping("/agents/{agentId}/versions/{versionNo}")
    @PreAuthorize("@ss.hasPermi('ai:agent:view')")
    @Operation(summary = "查询 Agent 版本详情")
    public Map<String, Object> version(@Parameter(description = "Agent ID") @PathVariable long agentId, @Parameter(description = "版本号") @PathVariable int versionNo) { return service.getAgentVersion(agentId, versionNo); }

    @PostMapping("/agents/validate")
    @PreAuthorize("@ss.hasPermi('ai:agent:edit')")
    @Operation(summary = "校验 Agent 草稿")
    public void validate(@RequestBody AgentDraftRequest request) {
        service.validateDraft(new AgentDraft(request.agentKey(), request.name(), request.systemPrompt(),
                request.routeJson(), request.toolJson(), request.budgetJson()));
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("@ss.hasPermi('ai:run:view')")
    @Operation(summary = "查询运行")
    public Map<String, Object> getRun(@Parameter(description = "Run ID") @PathVariable long runId) { return service.getRun(runId, SecurityUtils.getUserId()); }

    @GetMapping("/runs/{runId}/events")
    @PreAuthorize("@ss.hasPermi('ai:run:view')")
    @Operation(summary = "查询运行事件")
    public List<Map<String, Object>> events(@Parameter(description = "Run ID") @PathVariable long runId, @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
                                            @Parameter(description = "页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return service.listEvents(runId, SecurityUtils.getUserId(), pageNum, pageSize);
    }

    @GetMapping("/runs/{runId}/usage")
    @PreAuthorize("@ss.hasPermi('ai:run:view')")
    @Operation(summary = "查询模型用量")
    public List<Map<String, Object>> usage(@Parameter(description = "Run ID") @PathVariable long runId, @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
                                           @Parameter(description = "页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return service.listUsage(runId, SecurityUtils.getUserId(), pageNum, pageSize);
    }

    @GetMapping("/runs/{runId}/tool-usage")
    @PreAuthorize("@ss.hasPermi('ai:run:view')")
    @Operation(summary = "查询工具用量")
    public List<Map<String, Object>> toolUsage(@Parameter(description = "Run ID") @PathVariable long runId, @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
                                               @Parameter(description = "页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return service.listToolUsage(runId, SecurityUtils.getUserId(), pageNum, pageSize);
    }

    @PostMapping("/conversations")
    @PreAuthorize("@ss.hasPermi('ai:run:execute')")
    @Operation(summary = "创建会话")
    public long createConversation(@Parameter(description = "创建会话请求") @RequestBody ConversationCreateRequest request) {
        return service.createConversation(request.agentKey(), SecurityUtils.getUserId(), request.title());
    }

    @GetMapping("/conversations")
    @PreAuthorize("@ss.hasPermi('ai:run:view')")
    @Operation(summary = "查询会话列表")
    public List<Map<String, Object>> listConversations(@Parameter(description = "关键词") @RequestParam(required = false) String keyword,
                                                       @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
                                                       @Parameter(description = "页大小") @RequestParam(defaultValue = "10") int pageSize) {
        return service.listConversations(SecurityUtils.getUserId(), keyword, pageNum, pageSize);
    }


    @PostMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("@ss.hasPermi('ai:run:execute')")
    @Operation(summary = "发送消息")
    public long enqueue(@Parameter(description = "会话 ID") @PathVariable long conversationId, @Parameter(description = "消息内容") @RequestParam String content,
                        @Parameter(description = "幂等键") @RequestParam String idempotencyKey) {
        return service.enqueueMessage(conversationId, SecurityUtils.getUserId(), content, idempotencyKey);
    }


    @PostMapping("/runs/{runId}/cancel")
    @PreAuthorize("@ss.hasPermi('ai:run:cancel')")
    @Operation(summary = "取消运行")
    public int cancel(@Parameter(description = "Run ID") @PathVariable long runId) {
        return service.requestCancel(runId, SecurityUtils.getUserId());
    }
}
