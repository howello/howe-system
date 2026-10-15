package com.howe.share.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.share.domain.ShareLink;
import com.howe.share.domain.dto.ShareLinkCreateRequest;
import com.howe.share.domain.dto.ShareLinkUpdateRequest;
import com.howe.share.service.IShareLinkService;
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
 * 分享链接 控制层
 *
 * @author howe
 */
@Tag(name = "分享-分享链接", description = "分享链接管理与访问记录")
@RestController
@RequestMapping("/share/link")
@RequiredArgsConstructor
public class ShareLinkController extends BaseController {

    private final IShareLinkService shareLinkService;

    /**
     * 查询分享列表
     */
    @Operation(summary = "查询分享列表")
    @PreAuthorize("@ss.hasPermi('share:link:list')")
    @GetMapping("/list")
    public TableDataInfo list(ShareLink shareLink) {
        startPage();
        List<ShareLink> list = shareLinkService.selectShareLinkList(shareLink);
        return getDataTable(list);
    }

    /**
     * 获取分享详情
     */
    @Operation(summary = "获取分享详情")
    @PreAuthorize("@ss.hasPermi('share:link:query')")
    @GetMapping("/{linkId}")
    public AjaxResult getInfo(@Parameter(description = "分享ID", required = true)
                              @PathVariable("linkId") Long linkId) {
        return success(shareLinkService.selectShareLinkById(linkId));
    }

    /**
     * 创建分享
     */
    @Operation(summary = "创建分享", description = "选文件 + 配参数，立即返回短链，字节异步缓存")
    @PreAuthorize("@ss.hasPermi('share:link:add')")
    @Log(title = "分享链接", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody ShareLinkCreateRequest request) {
        return success(shareLinkService.create(request, getUsername()));
    }

    /**
     * 修改分享
     */
    @Operation(summary = "修改分享")
    @PreAuthorize("@ss.hasPermi('share:link:edit')")
    @Log(title = "分享链接", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody ShareLinkUpdateRequest request) {
        return toAjax(shareLinkService.update(request, getUsername()));
    }

    /**
     * 手动失效 / 恢复
     */
    @Operation(summary = "手动失效 / 恢复")
    @PreAuthorize("@ss.hasPermi('share:link:edit')")
    @Log(title = "分享链接", businessType = BusinessType.UPDATE)
    @PutMapping("/status")
    public AjaxResult changeStatus(@Parameter(description = "分享ID", required = true) @RequestParam("linkId") Long linkId,
                                   @Parameter(description = "目标状态（0正常 1已失效）", required = true) @RequestParam("status") String status) {
        return toAjax(shareLinkService.changeStatus(linkId, status));
    }

    /**
     * 删除分享
     */
    @Operation(summary = "删除分享", description = "连带删除路由缓存、存储对象与访问日志")
    @PreAuthorize("@ss.hasPermi('share:link:remove')")
    @Log(title = "分享链接", businessType = BusinessType.DELETE)
    @DeleteMapping("/{linkIds}")
    public AjaxResult remove(@Parameter(description = "分享ID数组，多个用逗号分隔", required = true)
                             @PathVariable Long[] linkIds) {
        return toAjax(shareLinkService.deleteByIds(linkIds));
    }

    /**
     * 手动重新缓存
     */
    @Operation(summary = "手动重新缓存")
    @PreAuthorize("@ss.hasPermi('share:link:recache')")
    @Log(title = "分享链接", businessType = BusinessType.UPDATE)
    @PostMapping("/recache/{linkId}")
    public AjaxResult recache(@Parameter(description = "分享ID", required = true)
                              @PathVariable("linkId") Long linkId) {
        return toAjax(shareLinkService.recache(linkId));
    }

    /**
     * 查询分享访问记录
     */
    @Operation(summary = "查询分享访问记录")
    @PreAuthorize("@ss.hasPermi('share:link:query')")
    @GetMapping("/logs/{linkId}")
    public AjaxResult logs(@Parameter(description = "分享ID", required = true)
                           @PathVariable("linkId") Long linkId) {
        return success(shareLinkService.selectLogs(linkId));
    }
}
