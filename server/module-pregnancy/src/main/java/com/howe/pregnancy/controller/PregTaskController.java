package com.howe.pregnancy.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.domain.dto.ExportCreateBody;
import com.howe.pregnancy.domain.vo.TaskVO;
import com.howe.pregnancy.service.IPregExportService;
import com.howe.pregnancy.service.IPregTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务与导出 控制层
 *
 * @author howe
 */
@Tag(name = "孕期-任务与导出", description = "物品内容生成、任务状态与私有 ZIP 导出")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregTaskController extends BaseController {

    private final IPregTaskService taskService;

    private final IPregExportService exportService;

    /**
     * 触发物品内容生成
     *
     * @param id   物品 ID
     * @param body 请求
     * @return 任务摘要
     */
    @Operation(summary = "生成物品内容", description = "首次生成或显式重新生成；已有内容继续展示")
    @Log(title = "孕期待产包内容", businessType = BusinessType.INSERT)
    @PostMapping("/bag-items/{id}/content/generate")
    public AjaxResult generate(@PathVariable("id") Long id,
                               @Valid @RequestBody ContentTriggerBody body) {
        return success(taskService.generateItemContent(id, body.getVersion(), body.getRequestId()));
    }

    /**
     * 重试物品内容生成
     *
     * @param id 物品 ID
     * @return 任务摘要
     */
    @Operation(summary = "重试物品内容", description = "只处理当前任务未完成的明确失败阶段")
    @Log(title = "孕期待产包内容", businessType = BusinessType.UPDATE)
    @PostMapping("/bag-items/{id}/content/retry")
    public AjaxResult retry(@PathVariable("id") Long id) {
        return success(taskService.retryItemContent(id));
    }

    /**
     * 查询任务摘要
     *
     * @param taskId 任务 ID
     * @return 任务摘要
     */
    @Operation(summary = "查询任务", description = "物品内容按孕期读取权限；导出仅申请者可读")
    @GetMapping("/tasks/{taskId}")
    public AjaxResult task(@PathVariable("taskId") Long taskId) {
        return success(taskService.getTask(taskId));
    }

    /**
     * 申请导出
     *
     * @param body 请求
     * @return 导出任务
     */
    @Operation(summary = "申请导出", description = "立即返回任务 ID；构建与打包在后台执行")
    @Log(title = "孕期导出", businessType = BusinessType.INSERT)
    @PostMapping("/exports")
    public AjaxResult createExport(@Valid @RequestBody ExportCreateBody body) {
        return success(exportService.create(body));
    }

    /**
     * 查询自己的导出任务
     *
     * @return 导出列表
     */
    @Operation(summary = "查询导出任务", description = "只列申请者自己的任务")
    @GetMapping("/exports")
    public AjaxResult listExports() {
        List<com.howe.pregnancy.domain.vo.ExportVO> list = exportService.list();
        return success(list);
    }

    /**
     * 查询导出详情
     *
     * @param id 任务 ID
     * @return 导出详情
     */
    @Operation(summary = "查询导出详情", description = "其他账号一律 RESOURCE_NOT_FOUND")
    @GetMapping("/exports/{id}")
    public AjaxResult getExport(@PathVariable("id") Long id) {
        return success(exportService.get(id));
    }

    /**
     * 下载导出 ZIP
     *
     * @param id 任务 ID
     * @return 二进制流
     */
    @Operation(summary = "下载导出结果", description = "READY 且未过期时返回私有 ZIP；过期为 410")
    @GetMapping("/exports/{id}/content")
    public ResponseEntity<InputStreamResource> downloadExport(@PathVariable("id") Long id) {
        IPregExportService.ExportContent content = exportService.content(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + content.fileName() + "\"")
                .body(new InputStreamResource(content.inputStream()));
    }

    /**
     * 内容触发请求
     */
    @lombok.Data
    @io.swagger.v3.oas.annotations.media.Schema(description = "内容生成触发请求")
    public static class ContentTriggerBody {

        @jakarta.validation.constraints.NotNull(message = "版本号不能为空")
        private Integer version;

        private String requestId;
    }
}
