package com.howe.blog.controller;

import com.howe.blog.domain.BlogLink;
import com.howe.blog.service.IBlogLinkService;
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
 * 博客友链 控制层
 *
 * <p>友链与 RSS 订阅源共用 blog_link 表，本控制器<b>每个方法都强制把 linkType 固定为 1</b>，
 * 请求体里带的 linkType 一律被覆盖，因此持有 blog:link:* 的角色永远碰不到订阅源。</p>
 *
 * @author howe
 */
@Tag(name = "博客友链", description = "blog-ui 友链页的数据源，按字典 blog_link_group 分组")
@RestController
@RequestMapping("/blog/link")
@RequiredArgsConstructor
public class BlogLinkController extends BaseController {

    /** 类型：友链。本控制器的所有写操作都强制使用它 */
    private static final String TYPE_LINK = "1";

    private final IBlogLinkService blogLinkService;

    /**
     * 查询友链列表
     */
    @Operation(summary = "查询友链列表", description = "分页查询友链，支持按名称模糊、状态、分组过滤")
    @PreAuthorize("@ss.hasPermi('blog:link:list')")
    @GetMapping("/list")
    public TableDataInfo list(BlogLink blogLink) {
        blogLink.setLinkType(TYPE_LINK);
        startPage();
        List<BlogLink> list = blogLinkService.selectBlogLinkList(blogLink);
        return getDataTable(list);
    }

    /**
     * 获取友链详情
     */
    @Operation(summary = "获取友链详情", description = "按ID查询单条友链，ID 指向订阅源时返回空")
    @PreAuthorize("@ss.hasPermi('blog:link:query')")
    @GetMapping(value = "/{linkId}")
    public AjaxResult getInfo(@Parameter(description = "友链ID", required = true)
            @PathVariable("linkId") Long linkId) {
        return success(blogLinkService.selectBlogLinkById(linkId, TYPE_LINK));
    }

    /**
     * 新增友链
     */
    @Operation(summary = "新增友链")
    @PreAuthorize("@ss.hasPermi('blog:link:add')")
    @Log(title = "博客友链", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BlogLink blogLink) {
        blogLink.setLinkType(TYPE_LINK);
        return toAjax(blogLinkService.insertBlogLink(blogLink));
    }

    /**
     * 修改友链
     */
    @Operation(summary = "修改友链", description = "ID 指向订阅源时按「数据不存在或类型不匹配」拒绝")
    @PreAuthorize("@ss.hasPermi('blog:link:edit')")
    @Log(title = "博客友链", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BlogLink blogLink) {
        blogLink.setLinkType(TYPE_LINK);
        return toAjax(blogLinkService.updateBlogLink(blogLink));
    }

    /**
     * 删除友链
     */
    @Operation(summary = "删除友链", description = "ID 中混入订阅源时整体拒绝，不做部分删除")
    @PreAuthorize("@ss.hasPermi('blog:link:remove')")
    @Log(title = "博客友链", businessType = BusinessType.DELETE)
    @DeleteMapping("/{linkIds}")
    public AjaxResult remove(@Parameter(description = "友链ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] linkIds) {
        return toAjax(blogLinkService.deleteBlogLinkByIds(linkIds, TYPE_LINK));
    }
}
