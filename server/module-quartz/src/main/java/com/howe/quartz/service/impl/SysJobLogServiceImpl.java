package com.howe.quartz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.howe.quartz.domain.SysJobLog;
import com.howe.quartz.mapper.SysJobLogMapper;
import com.howe.quartz.service.ISysJobLogDetailService;
import com.howe.quartz.service.ISysJobLogService;

/**
 * 定时任务调度日志信息 服务层
 *
 * @author howe
 */
@Service
public class SysJobLogServiceImpl implements ISysJobLogService
{
    @Autowired
    private SysJobLogMapper jobLogMapper;

    @Autowired
    private ISysJobLogDetailService detailService;

    /**
     * 获取quartz调度器日志的计划任务
     *
     * @param jobLog 调度日志信息
     * @return 调度任务日志集合
     */
    @Override
    public List<SysJobLog> selectJobLogList(SysJobLog jobLog)
    {
        return jobLogMapper.selectJobLogList(jobLog);
    }

    /**
     * 通过调度任务日志ID查询调度信息
     *
     * @param jobLogId 调度任务日志ID
     * @return 调度任务日志对象信息
     */
    @Override
    public SysJobLog selectJobLogById(Long jobLogId)
    {
        return jobLogMapper.selectJobLogById(jobLogId);
    }

    /**
     * 新增任务日志
     *
     * @param jobLog 调度日志信息
     */
    @Override
    public void addJobLog(SysJobLog jobLog)
    {
        jobLogMapper.insertJobLog(jobLog);
    }

    /**
     * 更新任务日志
     *
     * @param jobLog 调度日志信息
     */
    @Override
    public void updateJobLog(SysJobLog jobLog)
    {
        jobLogMapper.updateJobLog(jobLog);
    }

    /**
     * 批量删除调度日志信息
     *
     * @param logIds 需要删除的数据ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteJobLogByIds(Long[] logIds)
    {
        detailService.deleteByJobLogIds(logIds);
        return jobLogMapper.deleteJobLogByIds(logIds);
    }

    /**
     * 删除任务日志
     *
     * @param jobId 调度日志ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteJobLogById(Long jobId)
    {
        detailService.deleteByJobLogIds(new Long[] { jobId });
        return jobLogMapper.deleteJobLogById(jobId);
    }

    /**
     * 清空任务日志
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanJobLog()
    {
        detailService.cleanDetails();
        jobLogMapper.cleanJobLog();
    }
}
