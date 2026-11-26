package com.howe.book.controller;

import com.howe.book.domain.BookNode;
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
        return AjaxResult.success(nodes.list(SecurityUtils.getUserId(), parentId, !tree, null, null, withContent));
    }

    /** 读取节点详情。 */
    @GetMapping("/{id}")
    @Operation(summary = "查看读书节点")
    public AjaxResult get(@PathVariable String id) {
        return AjaxResult.success(nodes.get(SecurityUtils.getUserId(), id));
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
        return AjaxResult.success(nodes.update(SecurityUtils.getUserId(), id, input));
    }

    /** 删除节点及其后代。 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除读书节点")
    public AjaxResult remove(@PathVariable String id) {
        return AjaxResult.success(nodes.remove(SecurityUtils.getUserId(), id));
    }
}
