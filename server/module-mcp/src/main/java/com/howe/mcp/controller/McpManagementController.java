package com.howe.mcp.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.mcp.service.McpManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台 JWT 与方法权限保护的管理入口，不属于 MCP Bearer 或 OAuth 浏览器安全链。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/mcp/manage")
@Tag(name = "MCP 平台管理")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpManagementController extends BaseController {
    private final McpManagementService service;

    /** 查询已自动登记的客户端，不涉及管理员预审批。 */
    @Operation(summary = "分页查询 MCP 客户端")
    @PreAuthorize("@ss.hasPermi('mcp:client:list')")
    @GetMapping("/client/list")
    public TableDataInfo clients(@ModelAttribute McpManagementService.Query query) { return table(service.clients(query)); }

    /** 只返回公开回调和认证方式，不返回 secret。 */
    @Operation(summary = "获取 MCP 客户端公开详情")
    @PreAuthorize("@ss.hasPermi('mcp:client:query')")
    @GetMapping("/client/{id}")
    public AjaxResult client(@PathVariable String id) { return success(service.client(id)); }

    /** 管理状态变更不得携带或修改已验证回调。 */
    @Operation(summary = "启停 MCP 客户端", description = "停用会撤销旧授权，恢复后需重新授权")
    @PreAuthorize("@ss.hasPermi('mcp:client:edit')")
    @Log(title = "MCP 客户端状态", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/client/{id}/status")
    public AjaxResult clientStatus(@PathVariable String id, @Valid @RequestBody ClientStatus body) {
        service.clientStatus(id, body.status());
        return success();
    }

    /** 目录由实际代码定义，不发布未实现业务工具。 */
    @Operation(summary = "查询 MCP 工具目录")
    @PreAuthorize("@ss.hasPermi('mcp:tool:list')")
    @GetMapping("/tool/list")
    public TableDataInfo tools() {
        var rows = service.tools();
        return table(new McpManagementService.Page<>(rows, rows.size()));
    }

    /** 查看固定工具契约不授予调用权限。 */
    @Operation(summary = "获取 MCP 工具契约")
    @PreAuthorize("@ss.hasPermi('mcp:tool:query')")
    @GetMapping("/tool/{name}")
    public AjaxResult tool(@PathVariable String name) { return success(service.tool(name)); }

    /** 工具状态修改采用后台当前账号作为更新者。 */
    @Operation(summary = "修改 MCP 工具开放状态")
    @PreAuthorize("@ss.hasPermi('mcp:tool:edit')")
    @Log(title = "MCP 工具状态", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/tool/{name}/status")
    public AjaxResult toolStatus(@PathVariable String name, @Valid @RequestBody ToolStatus body) {
        service.toolStatus(name, body.enabled(), getUsername());
        return success();
    }

    /** 此处是全站授权列表，普通用户仅可使用独立 self 接口。 */
    @Operation(summary = "分页查询 MCP 用户授权")
    @PreAuthorize("@ss.hasPermi('mcp:grant:list')")
    @GetMapping("/grant/list")
    public TableDataInfo grants(@ModelAttribute McpManagementService.Query query) { return table(service.grants(query)); }

    /** 查看授权状态不包含任何可用凭据。 */
    @Operation(summary = "获取 MCP 授权详情")
    @PreAuthorize("@ss.hasPermi('mcp:grant:query')")
    @GetMapping("/grant/{id}")
    public AjaxResult grant(@PathVariable Long id) { return success(service.grant(id)); }

    /** 管理撤销和本人撤销共用同一事务行为。 */
    @Operation(summary = "撤销 MCP 用户授权")
    @PreAuthorize("@ss.hasPermi('mcp:grant:revoke')")
    @Log(title = "MCP 授权撤销", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/grant/{id}/revoke")
    public AjaxResult revoke(@PathVariable Long id) { service.revoke(id); return success(); }

    /** 固定元数据查询，不开放协议原始报文。 */
    @Operation(summary = "分页查询 MCP 调用记录")
    @PreAuthorize("@ss.hasPermi('mcp:calllog:list')")
    @GetMapping("/calllog/list")
    public TableDataInfo calls(@ModelAttribute McpManagementService.Query query) { return table(service.calls(query)); }

    /** 详情采用同一字段白名单，不返回敏感摘要。 */
    @Operation(summary = "获取 MCP 调用记录详情")
    @PreAuthorize("@ss.hasPermi('mcp:calllog:query')")
    @GetMapping("/calllog/{id}")
    public AjaxResult call(@PathVariable Long id) { return success(service.call(id)); }

    /** JDBC 分页结果显式转换为后台约定，不把 PageHelper 放在 JDBC 查询之前。 */
    private static TableDataInfo table(McpManagementService.Page<?> page) {
        TableDataInfo result = new TableDataInfo(page.rows(), page.total());
        result.setCode(200); result.setMsg("查询成功");
        return result;
    }
    /** 状态请求只有单个管理状态字段。 */
    public record ClientStatus(@Pattern(regexp = "[01]") @jakarta.validation.constraints.NotNull String status) { }
    /** 工具状态不能包含动态实现、权限或 schema。 */
    public record ToolStatus(@Pattern(regexp = "[YN]") @jakarta.validation.constraints.NotNull String enabled) { }
}
