-- ============================================================
-- MCP 后续步骤账本（C4）
--
-- 适用：已安装 MCP、需要「父操作成功后再执行固定步骤」的库。
-- 执行：mysql -u root -p howe-system < 增量/mcp_operation_steps_20261112.sql
-- 本增量执行结束可删除。
--
-- 只保存平台登记的固定步骤：step_key 与处理器版本都由代码冻结，
-- 不接受数据库或模型指定实现类。授权信息通过父操作引用取得，不复制 token。
-- ============================================================

create table if not exists mcp_operation_step (
  step_id char(32) character set ascii collate ascii_bin not null,
  operation_id char(32) character set ascii collate ascii_bin not null,
  step_key varchar(64) character set ascii collate ascii_bin not null,
  handler_version varchar(32) character set ascii collate ascii_bin not null,
  state varchar(16) character set ascii collate ascii_bin not null
    comment 'QUEUED RUNNING SUCCEEDED FAILED ABORTED CANCELLED UNKNOWN',
  stage varchar(64) character set ascii collate ascii_bin not null default 'QUEUED',
  result_code varchar(64) character set ascii collate ascii_bin default null,
  encrypted_payload mediumtext comment 'SecretCipher enc:v1 密文，仅步骤业务载荷',
  encrypted_receipt mediumtext comment 'SecretCipher enc:v1 密文，外部调用回执',
  encrypted_result mediumtext comment 'SecretCipher enc:v1 密文，仅安全结果',
  lease_owner varchar(64) character set ascii collate ascii_bin default null,
  lease_until datetime(3) default null,
  fence bigint not null default 0,
  attempt int not null default 0,
  next_attempt_at datetime(3) default null,
  execution_deadline_at datetime(3) default null,
  create_time datetime(3) not null default current_timestamp(3),
  update_time datetime(3) not null default current_timestamp(3),
  primary key (step_id),
  unique key uk_mcp_operation_step_key (operation_id, step_key),
  key idx_mcp_operation_step_scan (state, next_attempt_at, lease_until),
  key idx_mcp_operation_step_owner (lease_owner, state),
  key idx_mcp_operation_step_retention (operation_id, state)
) engine=innodb default charset=utf8mb4 comment='MCP 后续步骤账本，固定代码键与租约';

-- 步骤执行并发参数（默认 1，范围 1..4，启动时读取）
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-点餐自动审核并发', 'mcp.operation.mealAuditConcurrency', '1', 'Y', 'MCP', 'N', 'admin', sysdate(),
       '1到4，启动时读取；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key = 'mcp.operation.mealAuditConcurrency');

-- 点餐写工具并发参数（默认 2，范围 1..8）
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-点餐写执行并发', 'mcp.operation.mealConcurrency', '2', 'Y', 'MCP', 'N', 'admin', sysdate(),
       '1到8，启动时读取；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key = 'mcp.operation.mealConcurrency');

-- 写操作进程内总并发（默认 4，范围 1..32）
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-写操作总并发', 'mcp.operation.totalConcurrency', '4', 'Y', 'MCP', 'N', 'admin', sysdate(),
       '1到32，启动时读取；仅单进程容量，不代表集群总并发'
where not exists (select 1 from sys_config where config_key = 'mcp.operation.totalConcurrency');

-- 写操作单账号并发（默认 1，范围 1..4）
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-写操作单账号并发', 'mcp.operation.userConcurrency', '1', 'Y', 'MCP', 'N', 'admin', sysdate(),
       '1到4，启动时读取；仅单进程容量，跨实例由领域锁与围栏保证正确性'
where not exists (select 1 from sys_config where config_key = 'mcp.operation.userConcurrency');

-- C4 写工具种子（11 个工具中的 8 个写工具，默认关闭；3 个只读工具同样默认关闭）
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_kitchen_order_list', 'N', 'admin', 'C4：查询厨师工作台待响应/制作中订单'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_kitchen_order_list');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_kitchen_summary_get', 'N', 'admin', 'C4：查询厨师工作台待办角标'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_kitchen_summary_get');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_order_submit', 'N', 'admin', 'C4：家庭点餐下单（需浏览器确认）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_order_submit');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_order_cancel', 'N', 'admin', 'C4：取消本人待接单订单（版本 CAS）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_order_cancel');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_order_accept', 'N', 'admin', 'C4：厨师接单（候选轮次与版本 CAS）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_order_accept');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_order_refuse', 'N', 'admin', 'C4：厨师拒单（按原策略转公开或关闭）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_order_refuse');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_order_finish', 'N', 'admin', 'C4：接单人完成订单'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_order_finish');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_review_submit', 'N', 'admin', 'C4：本人已完成订单一单一评'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_review_submit');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_proposal_submit', 'N', 'admin', 'C4：提交新菜提案并登记 AI 自动审核步骤'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_proposal_submit');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_proposal_audit', 'N', 'admin', 'C4：人工审核提案（允许改判，联动菜品上下架）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_proposal_audit');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_member_list', 'N', 'admin', 'C4：家庭管理员分页读取成员最小字段'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_member_list');
