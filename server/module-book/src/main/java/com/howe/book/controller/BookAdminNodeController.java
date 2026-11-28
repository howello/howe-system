package com.howe.book.controller;

import com.howe.book.domain.BookNode;
import com.howe.book.service.BookNodeService;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.annotation.Log;
import com.howe.common.enums.BusinessType;
import org.springframework.security.access.prepost.PreAuthorize;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 读书节点接口。 */
@RestController
@RequestMapping("/book/admin/nodes")
@RequiredArgsConstructor
public class BookAdminNodeController extends BaseController {
    private final BookNodeService nodes;

    /**
     * 查询节点。
     *
     * 不传 parentId 时返回全部节点（后台搜索用）；传 parentId 时只返回该父节点下的一层子节点，
     * 供树形浏览逐层加载，其中 0 表示顶层分类。
     */
    @GetMapping
    @PreAuthorize("@ss.hasPermi('book:node:list')")
    @Operation(summary = "查询读书节点")
    public TableDataInfo list(@RequestParam(required = false) Long userId,
        @RequestParam(required = false) String type, @RequestParam(required = false) String title,
        @RequestParam(required = false) String parentId) {
        startPage();
        try {
            if (parentId != null) {
                // 树形浏览要展示一行摘要，按层返回的数据量本身可控，所以连正文一起查
                String parent = parentId.isBlank() || "0".equals(parentId) ? null : parentId;
                return getDataTable(nodes.list(userId, parent, true, type, title, true));
            }
            return getDataTable(nodes.list(userId, null, false, type, title));
        } finally {
            clearPage();
        }
    }

    /** 读取节点详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAnyPermi('book:node:query,book:node:edit')")
    @Operation(summary = "查看读书节点")
    public AjaxResult get(@PathVariable String id) {
        return AjaxResult.success(nodes.get(id));
    }

    /** 新建节点。 */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('book:node:add')")
    @Log(title = "读书节点", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @Operation(summary = "新建读书节点")
    public AjaxResult create(@RequestBody BookNode input) {
        return AjaxResult.success(nodes.create(SecurityUtils.getUserId(), input));
    }

    /** 编辑节点。 */
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('book:node:edit')")
    @Log(title = "读书节点", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @Operation(summary = "编辑读书节点")
    public AjaxResult update(@PathVariable String id, @RequestBody BookNode input) {
        return AjaxResult.success(nodes.update(id, input));
    }

    /** 删除节点及其后代。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('book:node:remove')")
    @Log(title = "读书节点", businessType = BusinessType.DELETE)
    @Operation(summary = "删除读书节点")
    public AjaxResult remove(@PathVariable String id) {
        nodes.remove(id);
        return AjaxResult.success();
    }
}
