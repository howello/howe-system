-- ----------------------------
-- 自动化任务调度步骤明细
-- ----------------------------
alter table sys_job_log
    modify column status char(1) default '0' comment '执行状态（0正常 1失败 2执行中）';

create table if not exists sys_job_log_detail (
  detail_id     bigint(20)    not null auto_increment comment '步骤明细ID',
  job_log_id    bigint(20)    not null                      comment '调度日志ID',
  step_no      int(11)       not null                      comment '步骤序号',
  step_name    varchar(128)  not null                      comment '步骤名称',
  status       varchar(32)   not null                      comment '步骤状态',
  message      varchar(2000)                          comment '步骤消息',
  error_info   varchar(2000)                          comment '异常信息',
  start_time   datetime      not null                      comment '步骤开始时间',
  end_time     datetime                                  comment '步骤结束时间',
  duration_ms  bigint(20)                                comment '步骤耗时毫秒',
  create_time  datetime      not null                      comment '创建时间',
  primary key (detail_id),
  key idx_job_log_detail_job_log_id (job_log_id)
) engine=innodb comment = '定时任务调度步骤明细表';
