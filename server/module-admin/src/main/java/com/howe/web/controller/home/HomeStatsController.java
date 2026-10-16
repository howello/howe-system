package com.howe.web.controller.home;

import com.howe.blog.service.IBlogStatsService;
import com.howe.common.core.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页工作台 门面控制器
 *
 * <p>只做统计的轻量聚合，语义中立；表结构知识在各模块自己的 Service 内。
 * 仅需登录（默认即非匿名），不做业务权限区分：卡片清单由
 * {@link HomeCardCatalogService} 按用户权限与模块可用性下发，统计数据
 * 由各卡片各自的接口提供。</p>
 *
 * @author howe
 */
@Tag(name = "首页工作台")
@RestController
@RequestMapping("/home")
public class HomeStatsController {

    private final IBlogStatsService blogStatsService;
    private final HomeCardCatalogService cardCatalogService;
    private final HomeTodoService homeTodoService;
    private final HomeProfileService homeProfileService;

    public HomeStatsController(IBlogStatsService blogStatsService,
                               HomeCardCatalogService cardCatalogService,
                               HomeTodoService homeTodoService,
                               HomeProfileService homeProfileService) {
        this.blogStatsService = blogStatsService;
        this.cardCatalogService = cardCatalogService;
        this.homeTodoService = homeTodoService;
        this.homeProfileService = homeProfileService;
    }

    @Operation(summary = "首页博客聚合统计，含 blogAvailable 降级标志")
    @GetMapping("/stats/summary")
    public AjaxResult summary() {
        return AjaxResult.success(blogStatsService.getHomeStats());
    }

    @Operation(summary = "首页可见卡片清单（按权限与模块可用性下发）")
    @GetMapping("/cards")
    public AjaxResult cards() {
        return AjaxResult.success(
                new HomeCardCatalogService.HomeCards(cardCatalogService.visibleCards()));
    }

    @Operation(summary = "首页待办聚合", description = "仅返回当前用户有权限处理的事项；全部无权限时为空列表")
    @GetMapping("/todos")
    public AjaxResult todos() {
        return AjaxResult.success(homeTodoService.listTodos());
    }

    @Operation(summary = "首页欢迎卡信息", description = "昵称、角色名、上次登录时间与 IP")
    @GetMapping("/welcome")
    public AjaxResult welcome() {
        return AjaxResult.success(homeProfileService.getWelcomeInfo());
    }

    @Operation(summary = "首页个人足迹", description = "当前用户自己的文章/订单/提案/分享计数；模块不可用的项为 null")
    @GetMapping("/footprint")
    public AjaxResult footprint() {
        return AjaxResult.success(homeProfileService.getFootprint());
    }
}
