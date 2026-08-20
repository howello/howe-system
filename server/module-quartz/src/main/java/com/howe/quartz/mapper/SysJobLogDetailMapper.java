package com.howe.quartz.mapper;

import java.util.List;
import com.howe.quartz.domain.SysJobLogDetail;

/**
 * 定时任务步骤明细数据层。
 */
public interface SysJobLogDetailMapper
{
    /**
     * 按调度日志编号查询步骤明细。
     *
     * @param jobLogId 调度日志编号
     * @return 步骤明细
     */
    List<SysJobLogDetail> selectDetailList(Long jobLogId);

    /**
     * 新增步骤明细。
     *
     * @param detail 步骤明细
     * @return 影响行数
     */
    int insertDetail(SysJobLogDetail detail);

    /**
     * 更新步骤终态。
     *
     * @param detail 步骤明细
     * @return 影响行数
     */
    int updateDetail(SysJobLogDetail detail);

    /**
     * 按调度日志删除步骤明细。
     *
     * @param jobLogIds 调度日志编号
     * @return 影响行数
     */
    int deleteByJobLogIds(Long[] jobLogIds);

    /**
     * 清空全部步骤明细。
     */
    int cleanDetails();
}
