package com.howe.blog.controller;

import com.howe.blog.domain.BlogTalk;
import com.howe.blog.service.IBlogTalkService;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客说说 控制层
 *
 * <p>正文是 markdown 原文，管理端必须用 MarkdownEditor 录入；
 * 用产出 HTML 的富文本编辑器会把 markdown 语法破坏掉。</p>
 *
 * @author howe
 */
@Tag(name = "博客说说", description = "blog-ui 说说页的数据源，正文为 markdown 原文")
@RestController
@RequestMapping("/blog/talk")
@RequiredArgsConstructor
public class BlogTalkController extends BaseController {

    private final IBlogTalkService blogTalkService;

    /**
     * 查询说说列表
     */
    @Operation(summary = "查询说说列表", description = "分页查询，置顶优先、同级按发布时间倒序")
    @PreAuthorize("@ss.hasPermi('blog:talk:list')")
    @GetMapping("/list")
    public TableDataInfo list(BlogTalk blogTalk) {
        startPage();
        List<BlogTalk> list = blogTalkService.selectBlogTalkList(blogTalk);
        return getDataTable(list);
    }

    /**
     * 获取说说详情
     */
    @Operation(summary = "获取说说详情", description = "返回的正文是 markdown 原文，可直接回填编辑器")
    @PreAuthorize("@ss.hasPermi('blog:talk:query')")
    @GetMapping(value = "/{talkId}")
    public AjaxResult getInfo(@Parameter(description = "说说ID", required = true)
            @PathVariable("talkId") Long talkId) {
        return success(blogTalkService.selectBlogTalkById(talkId));
    }

    /**
     * 新增说说
     */
    @Operation(summary = "新增说说", description = "发布时间留空时由后端取当前时间")
    @PreAuthorize("@ss.hasPermi('blog:talk:add')")
    @Log(title = "博客说说", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BlogTalk blogTalk) {
        return toAjax(blogTalkService.insertBlogTalk(blogTalk));
    }

    /**
     * 修改说说
     */
    @Operation(summary = "修改说说")
    @PreAuthorize("@ss.hasPermi('blog:talk:edit')")
    @Log(title = "博客说说", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BlogTalk blogTalk) {
        return toAjax(blogTalkService.updateBlogTalk(blogTalk));
    }

    /**
     * 删除说说
     */
    @Operation(summary = "删除说说", description = "ID 中混入不存在的数据时整体拒绝，不做部分删除")
    @PreAuthorize("@ss.hasPermi('blog:talk:remove')")
    @Log(title = "博客说说", businessType = BusinessType.DELETE)
    @DeleteMapping("/{talkIds}")
    public AjaxResult remove(@Parameter(description = "说说ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] talkIds) {
        return toAjax(blogTalkService.deleteBlogTalkByIds(talkIds));
    }
}
