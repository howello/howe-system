package com.howe.book.controller;

import com.howe.book.domain.BookNode;
import com.howe.book.domain.BookScopeRequest;
import com.howe.book.service.BookNodeService;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.utils.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 读书节点接口。 */
@RestController
@RequestMapping("/book/nodes")
@RequiredArgsConstructor
public class BookNodeController {
    private final BookNodeService nodes;

    /** 查询节点。 */
    @GetMapping
    @Operation(summary = "查询读书节点")
    public AjaxResult list(@RequestParam(required = false) String parentId,
        @RequestParam(defaultValue = "false") boolean tree,
        @RequestParam(defaultValue = "false") boolean withContent) {
        return AjaxResult.success(nodes.list(null, parentId, !tree, null, null, withContent));
    }

    /** 同步指定父节点下的直接内容。 */
    @PostMapping("/scope")
    @Operation(summary = "同步当前层读书节点")
    public AjaxResult syncScope(@RequestBody BookScopeRequest input) {
        return AjaxResult.success(nodes.syncScope(input.parentId(), input.known(), input.parentVersion()));
    }

    /** 读取当前书架统计。 */
    @GetMapping("/stats")
    @Operation(summary = "查询读书统计")
    public AjaxResult stats() {
        return AjaxResult.success(nodes.stats());
    }

    /** 读取节点及其祖先。 */
    @GetMapping("/{id}/path")
    @Operation(summary = "查询读书节点路径")
    public AjaxResult path(@PathVariable String id) {
        return AjaxResult.success(nodes.path(id));
    }

    /** 读取节点详情。 */
    @GetMapping("/{id}")
    @Operation(summary = "查看读书节点")
    public AjaxResult get(@PathVariable String id) {
        return AjaxResult.success(nodes.get(id));
    }

    /** 新建节点。 */
    @PostMapping
    @Operation(summary = "新建读书节点")
    public AjaxResult create(@RequestBody BookNode input) {
        return AjaxResult.success(nodes.create(SecurityUtils.getUserId(), input));
    }

    /** 编辑节点。 */
    @PutMapping("/{id}")
    @Operation(summary = "编辑读书节点")
    public AjaxResult update(@PathVariable String id, @RequestBody BookNode input) {
        return AjaxResult.success(nodes.update(id, input));
    }

    /** 删除节点及其后代。 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除读书节点")
    public AjaxResult remove(@PathVariable String id) {
        return AjaxResult.success(nodes.remove(id));
    }
}
