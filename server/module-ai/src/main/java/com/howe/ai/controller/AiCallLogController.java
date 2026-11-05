package com.howe.ai.controller;

import cn.hutool.core.util.StrUtil;
import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import com.howe.ai.service.IAiCallLogService;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
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
import java.util.Map;

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
     *
     * <p>日期区间按「左闭右开」处理：{@code beginTime} 取当日零点，
     * {@code endTime} 取次日零点。否则前端传 {@code 2026-11-04} 时，
     * 它会被解析成当日 00:00:00，结束日当天的记录几乎全部漏掉。</p>
     */
    @Operation(summary = "查询调用记录列表", description = "分页查询，支持场景、状态、时间区间筛选；时间区间左闭右开")
    @PreAuthorize("@ss.hasPermi('ai:calllog:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallLog aiCallLog)
    {
        // BaseEntity.params 承载 beginTime / endTime，这里把结束时间推到次日零点
        Map<String, Object> params = aiCallLog.getParams();
        Object begin = params.get("beginTime");
        Object end = params.get("endTime");
        if (begin != null)
        {
            params.put("beginTime", DayRange.startOfDay(begin));
        }
        if (end != null)
        {
            params.put("endTime", DayRange.startOfNextDay(end));
        }
        startPage();
        List<AiCallLog> list = aiCallLogService.selectAiCallLogList(aiCallLog);
        return getDataTable(list);
    }

    /**
     * 获取调用记录详情
     *
     * <p>返回带名称的详情：场景名、服务商名、渠道名、模型名与生成图。
     * 入参摘要仍是脱敏后的内容，不含完整 prompt 与密钥。</p>
     */
    @Operation(summary = "获取调用记录详情", description = "补齐场景名、渠道名、模型名与生成图；入参摘要已脱敏")
    @PreAuthorize("@ss.hasPermi('ai:calllog:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@Parameter(description = "记录ID", required = true) @PathVariable("id") Long id)
    {
        return success(aiCallLogService.selectDetail(id));
    }

    /**
     * 用量统计
     *
     * <p>结果带 {@code groupLabel}：场景显示名称、模型显示模型名、按天显示日期。
     * 按天维度按日期升序，其余维度按调用量降序。</p>
     */
    @Operation(summary = "用量统计", description = "按场景、模型或天聚合，返回调用次数、token 合计与展示名")
    @PreAuthorize("@ss.hasPermi('ai:calllog:stats')")
    @GetMapping("/stats")
    public AjaxResult stats(
            @Parameter(description = "聚合维度：scene / model / day，默认 scene")
            @RequestParam(required = false, defaultValue = "scene") String groupBy,
            @Parameter(description = "起始时间（含），如 2026-09-01") @RequestParam(required = false) String beginTime,
            @Parameter(description = "结束时间（含当天），如 2026-09-30") @RequestParam(required = false) String endTime,
            @Parameter(description = "场景过滤") @RequestParam(required = false) String scene)
    {
        Date begin = StrUtil.isBlank(beginTime) ? null : DayRange.startOfDay(beginTime);
        // 结束日整段纳入统计：推到次日零点后用「小于」比较
        Date end = StrUtil.isBlank(endTime) ? null : DayRange.startOfNextDay(endTime);
        List<AiCallLogStats> stats = aiCallLogService.selectStats(groupBy, begin, end, scene);
        return success(stats);
    }
}
