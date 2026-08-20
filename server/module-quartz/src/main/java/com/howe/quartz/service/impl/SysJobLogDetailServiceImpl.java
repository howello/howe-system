package com.howe.quartz.service.impl;

import com.howe.common.task.TaskLogPublisher;
import com.howe.common.task.TaskLogRecord;
import com.howe.quartz.domain.SysJobLogDetail;
import com.howe.quartz.mapper.SysJobLogDetailMapper;
import com.howe.quartz.service.ISysJobLogDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 定时任务步骤明细服务。
 */
@Service
public class SysJobLogDetailServiceImpl implements ISysJobLogDetailService, TaskLogPublisher {
    @Autowired
    private SysJobLogDetailMapper detailMapper;

    @Override
    public List<SysJobLogDetail> selectDetailList(Long jobLogId) {
        return detailMapper.selectDetailList(jobLogId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long begin(TaskLogRecord record) {
        SysJobLogDetail detail = toDetail(record);
        detailMapper.insertDetail(detail);
        return detail.getDetailId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void info(TaskLogRecord record) {
        detailMapper.insertDetail(toDetail(record));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long detailId, TaskLogRecord record) {
        SysJobLogDetail detail = toDetail(record);
        detail.setDetailId(detailId);
        detailMapper.updateDetail(detail);
    }

    @Override
    public int deleteByJobLogIds(Long[] jobLogIds) {
        return detailMapper.deleteByJobLogIds(jobLogIds);
    }

    @Override
    public void cleanDetails() {
        detailMapper.cleanDetails();
    }

    private SysJobLogDetail toDetail(TaskLogRecord record) {
        SysJobLogDetail detail = new SysJobLogDetail();
        detail.setJobLogId(record.jobLogId());
        detail.setStepNo(record.stepNo());
        detail.setStepName(record.stepName());
        detail.setStatus(record.status());
        detail.setMessage(record.message());
        detail.setErrorInfo(record.errorInfo());
        detail.setStartTime(record.startTime() == null ? new Date() : record.startTime());
        detail.setEndTime(record.endTime());
        detail.setDurationMs(record.durationMs());
        return detail;
    }
}
