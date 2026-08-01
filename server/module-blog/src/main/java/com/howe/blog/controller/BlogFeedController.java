package com.howe.blog.controller;

import com.howe.blog.domain.BlogFeedItem;
import com.howe.blog.domain.BlogLink;
import com.howe.blog.domain.vo.BlogFeedSyncResult;
import com.howe.blog.service.IBlogFeedItemService;
import com.howe.blog.service.IBlogFeedSyncService;
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
import org.springframework.transaction.annotation.Transactional;
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
 * 博客朋友圈（RSS订阅源） 控制层
 *
 * <p>与 {@link BlogLinkController} 共用 blog_link 表，本控制器<b>每个方法都强制把 linkType 固定为 2</b>，
 * 因此持有 blog:feed:* 的角色永远碰不到友链。</p>
 *
 * @author howe
 */
@Tag(name = "博客朋友圈", description = "RSS/Atom 订阅源管理，抓取到的条目供 blog-ui 朋友圈页展示")
@RestController
@RequestMapping("/blog/feed")
@RequiredArgsConstructor
public class BlogFeedController extends BaseController {

    /** 类型：RSS订阅源。本控制器的所有写操作都强制使用它 */
    private static final String TYPE_FEED = "2";

    private final IBlogLinkService blogLinkService;

    private final IBlogFeedSyncService blogFeedSyncService;

    private final IBlogFeedItemService blogFeedItemService;

    /**
     * 查询订阅源列表
     */
    @Operation(summary = "查询订阅源列表", description = "分页查询 RSS 订阅源，含最后同步时间与失败原因")
    @PreAuthorize("@ss.hasPermi('blog:feed:list')")
    @GetMapping("/list")
    public TableDataInfo list(BlogLink blogLink) {
        blogLink.setLinkType(TYPE_FEED);
        startPage();
        List<BlogLink> list = blogLinkService.selectBlogLinkList(blogLink);
        return getDataTable(list);
    }

    /**
     * 获取订阅源详情
     */
    @Operation(summary = "获取订阅源详情", description = "按ID查询单个订阅源，ID 指向友链时返回空")
    @PreAuthorize("@ss.hasPermi('blog:feed:query')")
    @GetMapping(value = "/{linkId}")
    public AjaxResult getInfo(@Parameter(description = "订阅源ID", required = true)
            @PathVariable("linkId") Long linkId) {
        return success(blogLinkService.selectBlogLinkById(linkId, TYPE_FEED));
    }

    /**
     * 新增订阅源
     */
    @Operation(summary = "新增订阅源", description = "必须填写 RSS/Atom 订阅地址")
    @PreAuthorize("@ss.hasPermi('blog:feed:add')")
    @Log(title = "博客订阅源", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BlogLink blogLink) {
        blogLink.setLinkType(TYPE_FEED);
        return toAjax(blogLinkService.insertBlogLink(blogLink));
    }

    /**
     * 修改订阅源
     */
    @Operation(summary = "修改订阅源", description = "ID 指向友链时按「数据不存在或类型不匹配」拒绝")
    @PreAuthorize("@ss.hasPermi('blog:feed:edit')")
    @Log(title = "博客订阅源", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BlogLink blogLink) {
        blogLink.setLinkType(TYPE_FEED);
        return toAjax(blogLinkService.updateBlogLink(blogLink));
    }

    /**
     * 删除订阅源
     */
    @Operation(summary = "删除订阅源", description = "ID 中混入友链时整体拒绝，不做部分删除；删除成功后一并清掉该源已抓取的条目")
    @PreAuthorize("@ss.hasPermi('blog:feed:remove')")
    @Log(title = "博客订阅源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{linkIds}")
    @Transactional
    public AjaxResult remove(@Parameter(description = "订阅源ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] linkIds) {
        // 先删源：类型不匹配会在这里抛出并回滚，避免误删友链名下并不存在的条目
        int rows = blogLinkService.deleteBlogLinkByIds(linkIds, TYPE_FEED);
        for (Long linkId : linkIds) {
            blogFeedItemService.deleteByLinkId(linkId);
        }
        return toAjax(rows);
    }

    /**
     * 同步全部订阅源
     */
    @Operation(summary = "同步全部订阅源", description = "遍历所有启用且填了地址的源，单源失败不影响其余源")
    @PreAuthorize("@ss.hasPermi('blog:feed:sync')")
    @Log(title = "博客订阅源", businessType = BusinessType.OTHER)
    @PostMapping("/sync")
    public AjaxResult syncAll() {
        BlogFeedSyncResult result = blogFeedSyncService.syncAll();
        return success(result);
    }

    /**
     * 同步单个订阅源
     */
    @Operation(summary = "同步单个订阅源", description = "ID 指向友链或未填订阅地址时拒绝")
    @PreAuthorize("@ss.hasPermi('blog:feed:sync')")
    @Log(title = "博客订阅源", businessType = BusinessType.OTHER)
    @PostMapping("/sync/{linkId}")
    public AjaxResult syncOne(@Parameter(description = "订阅源ID", required = true)
            @PathVariable("linkId") Long linkId) {
        BlogFeedSyncResult result = blogFeedSyncService.syncOne(linkId);
        return success(result);
    }

    /**
     * 查询抓取条目列表
     */
    @Operation(summary = "查询抓取条目列表", description = "linkId 可选，传了只看该源的条目（管理端主从联动）")
    @PreAuthorize("@ss.hasPermi('blog:feed:list')")
    @GetMapping("/item/list")
    public TableDataInfo itemList(BlogFeedItem blogFeedItem) {
        startPage();
        List<BlogFeedItem> list = blogFeedItemService.selectBlogFeedItemList(blogFeedItem);
        return getDataTable(list);
    }

    /**
     * 删除抓取条目
     */
    @Operation(summary = "删除抓取条目", description = "只删本地条目，不影响源站；下次同步该 url 会重新入库")
    @PreAuthorize("@ss.hasPermi('blog:feed:remove')")
    @Log(title = "博客朋友圈条目", businessType = BusinessType.DELETE)
    @DeleteMapping("/item/{itemIds}")
    public AjaxResult removeItem(@Parameter(description = "条目ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] itemIds) {
        return toAjax(blogFeedItemService.deleteBlogFeedItemByIds(itemIds));
    }
}
