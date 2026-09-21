package com.howe.ai.controller;

import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import com.howe.ai.service.IAiCallLogService;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.utils.DateUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * AI 调用记录 控制层
 *
 * <p>只读接口：记录由调用链异步写入，管理端只做查询与统计。</p>
 *
 * @author howe
 */
@Tag(name = "AI 调用记录", description = "每次调用（含降级的每一次尝试）一条，可按场景/状态/时间筛选")
@RestController
@RequestMapping("/ai/calllog")
@RequiredArgsConstructor
public class AiCallLogController extends BaseController
{
    private final IAiCallLogService aiCallLogService;

    /**
     * 查询调用记录列表
     */
    @Operation(summary = "查询调用记录列表", description = "分页查询，支持场景、状态、时间区间筛选")
    @PreAuthorize("@ss.hasPermi('ai:calllog:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallLog aiCallLog)
    {
        startPage();
        List<AiCallLog> list = aiCallLogService.selectAiCallLogList(aiCallLog);
        return getDataTable(list);
    }

    /**
     * 获取调用记录详情
     */
    @Operation(summary = "获取调用记录详情", description = "入参摘要已脱敏，不含完整 prompt 与密钥")
    @PreAuthorize("@ss.hasPermi('ai:calllog:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@Parameter(description = "记录ID", required = true) @PathVariable("id") Long id)
    {
        return success(aiCallLogService.selectAiCallLogById(id));
    }

    /**
     * 用量统计
     */
    @Operation(summary = "用量统计", description = "按场景、模型或天聚合，返回调用次数与 token 合计")
    @PreAuthorize("@ss.hasPermi('ai:calllog:stats')")
    @GetMapping("/stats")
    public AjaxResult stats(
            @Parameter(description = "聚合维度：scene / model / day，默认 scene")
            @RequestParam(required = false, defaultValue = "scene") String groupBy,
            @Parameter(description = "起始时间，如 2026-09-01") @RequestParam(required = false) String beginTime,
            @Parameter(description = "结束时间，如 2026-09-30") @RequestParam(required = false) String endTime,
            @Parameter(description = "场景过滤") @RequestParam(required = false) String scene)
    {
        Date begin = beginTime == null ? null : DateUtils.parseDate(beginTime);
        Date end = endTime == null ? null : DateUtils.parseDate(endTime);
        List<AiCallLogStats> stats = aiCallLogService.selectStats(groupBy, begin, end, scene);
        return success(stats);
    }
}
