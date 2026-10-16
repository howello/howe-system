package com.howe.blog.controller;

import com.howe.blog.service.IBlogStatsService;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客-首页统计 控制层
 *
 * <p>供管理端首页卡片取数，权限点沿用既有权限，不新增权限点。</p>
 *
 * @author howe
 */
@Tag(name = "博客-首页统计", description = "管理端首页博客相关卡片数据")
@RestController
@RequestMapping("/blog/stats")
@RequiredArgsConstructor
public class BlogStatsController extends BaseController {

    private final IBlogStatsService blogStatsService;

    @Operation(summary = "友链与订阅同步概况", description = "订阅源总数、同步失败数、最近同步时间与失败明细")
    @PreAuthorize("@ss.hasPermi('blog:link:list')")
    @GetMapping("/linkSync")
    public AjaxResult linkSync() {
        return success(blogStatsService.getLinkSyncOverview());
    }

    @Operation(summary = "待办：未发布草稿数", description = "供首页待办聚合卡使用")
    @PreAuthorize("@ss.hasPermi('blog:draft:list')")
    @GetMapping("/unpublishedDrafts")
    public AjaxResult unpublishedDrafts() {
        return success(blogStatsService.countUnpublishedDrafts());
    }

    @Operation(summary = "个人足迹：我发布的文章数", description = "供首页个人足迹卡使用")
    @GetMapping("/myArticles")
    public AjaxResult myArticles() {
        return success(blogStatsService.countMyArticles());
    }
}
