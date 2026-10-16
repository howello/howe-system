package com.howe.share.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.share.service.IShareStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分享-首页统计 控制层
 *
 * <p>供管理端首页卡片取数，权限点沿用既有的 {@code share:link:list}，不新增权限点。</p>
 *
 * @author howe
 */
@Tag(name = "分享-首页统计", description = "管理端首页分享相关卡片数据")
@RestController
@RequestMapping("/share/stats")
@RequiredArgsConstructor
public class ShareStatsController extends BaseController {

    private final IShareStatsService shareStatsService;

    @Operation(summary = "分享统计", description = "链接总数、今日访问与下载次数、最近创建的链接")
    @PreAuthorize("@ss.hasPermi('share:link:list')")
    @GetMapping("/summary")
    public AjaxResult summary() {
        return success(shareStatsService.getHomeStats());
    }

    @Operation(summary = "分享缓存健康", description = "缓存就绪 / 待缓存 / 失败数量")
    @PreAuthorize("@ss.hasPermi('share:link:list')")
    @GetMapping("/cacheHealth")
    public AjaxResult cacheHealth() {
        return success(shareStatsService.getCacheHealth());
    }

    @Operation(summary = "待办：即将过期的分享数", description = "默认 24 小时窗口，供首页待办聚合卡使用")
    @PreAuthorize("@ss.hasPermi('share:link:list')")
    @GetMapping("/expiringSoon")
    public AjaxResult expiringSoon() {
        return success(shareStatsService.countExpiringSoon());
    }

    @Operation(summary = "个人足迹：我创建的分享数", description = "供首页个人足迹卡使用")
    @GetMapping("/myLinks")
    public AjaxResult myLinks() {
        return success(shareStatsService.countMyLinks());
    }
}
