package com.howe.ai.controller;

import com.howe.ai.service.IAiStatsService;
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
 * AI-首页统计 控制层
 *
 * <p>供管理端首页卡片取数，权限点沿用既有权限，不新增权限点。</p>
 *
 * @author howe
 */
@Tag(name = "AI-首页统计", description = "管理端首页 AI 相关卡片数据")
@RestController
@RequestMapping("/ai/stats")
@RequiredArgsConstructor
public class AiStatsController extends BaseController {

    private final IAiStatsService aiStatsService;

    @Operation(summary = "AI 用量", description = "今日调用次数、token 消耗、成功率与按场景分布")
    @PreAuthorize("@ss.hasPermi('ai:calllog:stats')")
    @GetMapping("/summary")
    public AjaxResult summary() {
        return success(aiStatsService.getHomeStats());
    }

    @Operation(summary = "AI 渠道健康", description = "各渠道健康状态、异常与停用数、最近检测时间")
    @PreAuthorize("@ss.hasPermi('ai:channel:list')")
    @GetMapping("/channelHealth")
    public AjaxResult channelHealth() {
        return success(aiStatsService.getChannelHealth());
    }

    @Operation(summary = "AI 图片资产概况", description = "已生成图片总数、占用空间与最近生成时间")
    @PreAuthorize("@ss.hasPermi('ai:calllog:list')")
    @GetMapping("/imageAssets")
    public AjaxResult imageAssets() {
        return success(aiStatsService.getImageAssetOverview());
    }
}
