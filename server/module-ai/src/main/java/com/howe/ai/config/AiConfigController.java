package com.howe.ai.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.howe.ai.web.AiConfigRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/admin/config")
@Tag(name="管理员助手配置")
public class AiConfigController {
    private final AiConfigService service;
    public AiConfigController(AiConfigService service) { this.service=service; }
    @PostMapping("/{resource}")
    @PreAuthorize("@ss.hasPermi('ai:config:add')")
    @Operation(summary="创建配置")
    public Map<String,Object> create(@Parameter(description="资源类型") @PathVariable String resource, @RequestBody AiConfigRequest request) { service.create(resource, request); return service.prepare(resource, request); }
    @PutMapping("/{resource}/{id}")
    @PreAuthorize("@ss.hasPermi('ai:config:edit')")
    @Operation(summary="更新配置")
    public Map<String,Object> update(@Parameter(description="资源类型") @PathVariable String resource, @Parameter(description="配置 ID") @PathVariable long id, @RequestBody AiConfigRequest request) { service.update(resource, id, request); return service.prepare(resource, request); }
    @PutMapping("/{resource}/{id}/key")
    @PreAuthorize("@ss.hasPermi('ai:config:key:replace')")
    @Operation(summary="替换 API Key")
    public Map<String,Object> replaceKey(@Parameter(description="资源类型") @PathVariable String resource, @Parameter(description="配置 ID") @PathVariable long id, @RequestBody AiConfigRequest request) { var key = service.replaceApiKeyValue(id, request.apiKey()); return Map.of("keySummary", key.summary(), "keyVersion", key.keyVersion()); }
    @PostMapping("/{resource}/{id}/test")
    @PreAuthorize("@ss.hasPermi('ai:config:test')")
    @Operation(summary="测试连通性")
    public Map<String,Object> test(@Parameter(description="资源类型") @PathVariable String resource, @Parameter(description="配置 ID") @PathVariable long id) { service.get(resource, id); return Map.of("ok", false, "message", "连接测试由运行时执行"); }
    @PutMapping("/{resource}/{id}/disable")
    @PreAuthorize("@ss.hasPermi('ai:config:edit')")
    @Operation(summary="停用配置")
    public int disable(@Parameter(description="资源类型") @PathVariable String resource, @Parameter(description="配置 ID") @PathVariable long id) { return service.toggle(resource, id, false); }

    @GetMapping("/{resource}")
    @PreAuthorize("@ss.hasPermi('ai:config:list')")
    @Operation(summary="配置列表")
    public List<Map<String,Object>> list(@Parameter(description="资源类型") @PathVariable String resource, @Parameter(description="关键词") @RequestParam(required=false) String keyword,
                                         @Parameter(description="页码") @RequestParam(defaultValue="1") int pageNum, @Parameter(description="页大小") @RequestParam(defaultValue="10") int pageSize) {
        return service.list(resource,keyword,pageNum,pageSize);
    }
    @GetMapping("/{resource}/{id}")
    @PreAuthorize("@ss.hasPermi('ai:config:query')")
    @Operation(summary="配置详情")
    public Map<String,Object> get(@Parameter(description="资源类型") @PathVariable String resource,@Parameter(description = "配置 ID") @PathVariable long id) { return service.get(resource,id); }
    @PutMapping("/{resource}/{id}/status")
    @PreAuthorize("@ss.hasPermi('ai:config:edit')")
    @Operation(summary="启停配置")
    public int toggle(@Parameter(description="资源类型") @PathVariable String resource,@Parameter(description="配置 ID") @PathVariable long id,@Parameter(description="是否启用") @RequestParam boolean enabled) { return service.toggle(resource,id,enabled); }
    @DeleteMapping("/{resource}/{id}")
    @PreAuthorize("@ss.hasPermi('ai:config:remove')")
    @Operation(summary="删除配置")
    public int delete(@Parameter(description="资源类型") @PathVariable String resource,@Parameter(description="配置 ID") @PathVariable long id) { return service.delete(resource,id); }
}
