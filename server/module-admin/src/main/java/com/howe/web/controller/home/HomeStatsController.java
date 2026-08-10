package com.howe.web.controller.home;

import com.howe.blog.service.IBlogStatsService;
import com.howe.common.core.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页工作台统计 门面控制器
 *
 * <p>只做统计的轻量聚合，语义中立；博客表结构知识在 module-blog 的
 * {@code IBlogStatsService}。仅需登录（默认即非匿名），不做业务权限区分。</p>
 *
 * @author howe
 */
@Tag(name = "首页工作台统计")
@RestController
@RequestMapping("/home/stats")
public class HomeStatsController {

    private final IBlogStatsService blogStatsService;

    public HomeStatsController(IBlogStatsService blogStatsService) {
        this.blogStatsService = blogStatsService;
    }

    @Operation(summary = "首页博客聚合统计，含 blogAvailable 降级标志")
    @GetMapping("/summary")
    public AjaxResult summary() {
        return AjaxResult.success(blogStatsService.getHomeStats());
    }
}