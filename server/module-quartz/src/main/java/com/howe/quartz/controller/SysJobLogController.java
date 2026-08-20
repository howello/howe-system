package com.howe.quartz.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.common.utils.poi.ExcelUtil;
import com.howe.quartz.domain.SysJobLog;
import com.howe.quartz.service.ISysJobLogDetailService;
import com.howe.quartz.service.ISysJobLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 调度日志操作处理
 *
 * @author howe
 */
@Tag(name = "定时任务调度日志", description = "调度执行日志的查询、导出与清理")
@RestController
@RequestMapping("/monitor/jobLog")
public class SysJobLogController extends BaseController
{
    @Autowired
    private ISysJobLogService jobLogService;

    @Autowired
    private ISysJobLogDetailService detailService;

    /**
     * 查询定时任务调度日志列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:list')")
    @Operation(summary = "查询调度日志列表", description = "分页查询定时任务调度日志")
    @GetMapping("/list")
    public TableDataInfo list(SysJobLog sysJobLog)
    {
        startPage();
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        return getDataTable(list);
    }

    /**
     * 导出定时任务调度日志列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:export')")
    @Log(title = "任务调度日志", businessType = BusinessType.EXPORT)
    @Operation(summary = "导出调度日志列表", description = "按查询条件导出调度日志 Excel")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysJobLog sysJobLog)
    {
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        ExcelUtil<SysJobLog> util = new ExcelUtil<SysJobLog>(SysJobLog.class);
        util.exportExcel(response, list, "调度日志");
    }

    /**
     * 根据调度编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:query')")
    @Operation(summary = "获取调度日志详细信息", description = "根据调度日志编号查询详情")
    @GetMapping(value = "/{jobLogId}")
    public AjaxResult getInfo(@Parameter(description = "调度日志编号") @PathVariable Long jobLogId)
    {
        return success(jobLogService.selectJobLogById(jobLogId));
    }

    /**
     * 查询一次调度执行的步骤明细
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:query')")
    @Operation(summary = "查询调度步骤明细", description = "根据调度日志编号查询关键步骤明细")
    @GetMapping(value = "/{jobLogId}/details")
    public AjaxResult details(@Parameter(description = "调度日志编号") @PathVariable Long jobLogId)
    {
        return success(detailService.selectDetailList(jobLogId));
    }


    /**
     * 删除定时任务调度日志
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:remove')")
    @Log(title = "定时任务调度日志", businessType = BusinessType.DELETE)
    @Operation(summary = "删除调度日志", description = "按调度日志编号批量删除")
    @DeleteMapping("/{jobLogIds}")
    public AjaxResult remove(@Parameter(description = "调度日志编号数组") @PathVariable Long[] jobLogIds)
    {
        return toAjax(jobLogService.deleteJobLogByIds(jobLogIds));
    }

    /**
     * 清空定时任务调度日志
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:remove')")
    @Log(title = "调度日志", businessType = BusinessType.CLEAN)
    @Operation(summary = "清空调度日志", description = "清空全部定时任务调度日志")
    @DeleteMapping("/clean")
    public AjaxResult clean()
    {
        jobLogService.cleanJobLog();
        return success();
    }
}
