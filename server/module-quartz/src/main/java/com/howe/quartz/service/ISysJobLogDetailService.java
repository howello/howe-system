package com.howe.quartz.service;

import java.util.List;
import com.howe.quartz.domain.SysJobLogDetail;

/**
 * 定时任务步骤明细服务。
 */
public interface ISysJobLogDetailService
{
    /**
     * 按调度日志编号查询步骤。
     *
     * @param jobLogId 调度日志编号
     * @return 步骤明细
     */
    List<SysJobLogDetail> selectDetailList(Long jobLogId);

    /**
     * 按调度日志删除步骤。
     *
     * @param jobLogIds 调度日志编号
     * @return 影响行数
     */
    int deleteByJobLogIds(Long[] jobLogIds);

    /**
     * 清空步骤明细。
     */
    void cleanDetails();
}
