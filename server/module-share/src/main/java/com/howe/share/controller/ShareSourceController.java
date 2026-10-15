package com.howe.share.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.share.domain.ShareSource;
import com.howe.share.domain.vo.WebDavEntry;
import com.howe.share.service.IShareSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * WebDAV 源配置 控制层
 *
 * @author howe
 */
@Tag(name = "分享-WebDAV 源", description = "WebDAV 源配置与目录浏览")
@RestController
@RequestMapping("/share/source")
@RequiredArgsConstructor
public class ShareSourceController extends BaseController {

    private final IShareSourceService shareSourceService;

    /**
     * 查询源列表
     */
    @Operation(summary = "查询源列表")
    @PreAuthorize("@ss.hasPermi('share:source:list')")
    @GetMapping("/list")
    public TableDataInfo list(ShareSource shareSource) {
        startPage();
        List<ShareSource> list = shareSourceService.selectShareSourceList(shareSource);
        return getDataTable(list);
    }

    /**
     * 查询启用状态的源列表（下拉选择用）
     */
    @Operation(summary = "查询启用状态的源列表")
    @PreAuthorize("@ss.hasPermi('share:source:list')")
    @GetMapping("/enabled")
    public AjaxResult enabled() {
        return success(shareSourceService.selectEnabledSourceList());
    }

    /**
     * 获取源详情
     */
    @Operation(summary = "获取源详情")
    @PreAuthorize("@ss.hasPermi('share:source:query')")
    @GetMapping("/{sourceId}")
    public AjaxResult getInfo(@Parameter(description = "源ID", required = true)
                              @PathVariable("sourceId") Long sourceId) {
        return success(shareSourceService.selectShareSourceById(sourceId));
    }

    /**
     * 新增源
     */
    @Operation(summary = "新增源")
    @PreAuthorize("@ss.hasPermi('share:source:add')")
    @Log(title = "WebDAV源", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody ShareSource shareSource) {
        shareSource.setCreateBy(getUsername());
        return toAjax(shareSourceService.insertShareSource(shareSource));
    }

    /**
     * 修改源
     */
    @Operation(summary = "修改源")
    @PreAuthorize("@ss.hasPermi('share:source:edit')")
    @Log(title = "WebDAV源", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody ShareSource shareSource) {
        shareSource.setUpdateBy(getUsername());
        return toAjax(shareSourceService.updateShareSource(shareSource));
    }

    /**
     * 删除源
     */
    @Operation(summary = "删除源")
    @PreAuthorize("@ss.hasPermi('share:source:remove')")
    @Log(title = "WebDAV源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{sourceIds}")
    public AjaxResult remove(@Parameter(description = "源ID数组，多个用逗号分隔", required = true)
                             @PathVariable Long[] sourceIds) {
        return toAjax(shareSourceService.deleteShareSourceByIds(sourceIds));
    }

    /**
     * 浏览源目录
     */
    @Operation(summary = "浏览 WebDAV 目录")
    @PreAuthorize("@ss.hasPermi('share:source:query')")
    @GetMapping("/browse")
    public AjaxResult browse(@Parameter(description = "源ID", required = true) @RequestParam("sourceId") Long sourceId,
                             @Parameter(description = "相对源根的路径") @RequestParam(value = "path", required = false) String path) {
        List<WebDavEntry> entries = shareSourceService.browse(sourceId, path);
        return success(entries);
    }

    /**
     * 测试源连通性
     */
    @Operation(summary = "测试源连通性")
    @PreAuthorize("@ss.hasPermi('share:source:query')")
    @GetMapping("/test")
    public AjaxResult test(@Parameter(description = "源ID", required = true) @RequestParam("sourceId") Long sourceId) {
        return success(shareSourceService.test(sourceId));
    }
}
