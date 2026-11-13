-- ============================================================
-- MCP 写操作账本增量脚本（C3）
--
-- 已写入：mcp.sql 的「写操作账本」一节（新库跑 mcp.sql 即可，不需要本文件）
-- 适用：已安装 MCP、需要 C3 确认与持久化任务能力的库
-- 执行：mysql -u root -p howe-system < 增量/mcp_operation_ledger_20261112.sql
-- 本增量执行结束可删除。
-- ============================================================

-- 操作账本：确认、幂等、派发与恢复的唯一权威状态。
-- 不保存 access/refresh token；原始请求与结果仅经 SecretCipher 加密保存。
create table if not exists mcp_operation (
  operation_id char(32) character set ascii collate ascii_bin not null,
  user_id bigint not null,
  registered_client_id varchar(100) character set ascii collate ascii_bin not null,
  grant_id bigint not null,
  grant_version bigint not null,
  resource_uri varchar(2048) character set ascii collate ascii_bin not null,
  approved_scopes varchar(1000) character set ascii collate ascii_bin not null,
  prepared_dept_id bigint default null,
  account_context_hash binary(32) not null,
  tool_name varchar(64) character set ascii collate ascii_bin not null,
  tool_version varchar(32) character set ascii collate ascii_bin not null,
  execution_kind varchar(32) character set ascii collate ascii_bin not null,
  queue_group varchar(32) character set ascii collate ascii_bin not null,
  idempotency_hash binary(32) not null,
  arguments_hash binary(32) not null,
  configuration_hash binary(32) not null,
  target_type varchar(32) character set ascii collate ascii_bin not null,
  target_id varchar(64) character set ascii collate ascii_bin default null,
  expected_version varchar(128) character set ascii collate ascii_bin default null,
  encrypted_request mediumtext comment 'SecretCipher enc:v1 密文；密钥不可用时不执行、不降级明文',
  encrypted_result mediumtext comment 'SecretCipher enc:v1 密文，仅保存安全结果',
  state varchar(16) character set ascii collate ascii_bin not null
    comment 'PREPARED CONFIRMED QUEUED RUNNING SUCCEEDED FAILED UNKNOWN CANCELLED EXPIRED',
  stage varchar(64) character set ascii collate ascii_bin not null,
  confirmed_by bigint default null,
  confirmed_at datetime(3) default null,
  confirmation_summary_hash binary(32) default null,
  confirmation_session_hash binary(32) default null,
  confirmation_hash binary(32) default null,
  confirmation_expires_at datetime(3) default null,
  confirmation_used_at datetime(3) default null,
  prepare_expires_at datetime(3) not null,
  execution_deadline_at datetime(3) default null,
  next_attempt_at datetime(3) default null,
  lease_owner varchar(64) character set ascii collate ascii_bin default null,
  lease_until datetime(3) default null,
  fence bigint not null default 0,
  attempt int not null default 0,
  result_code varchar(64) character set ascii collate ascii_bin default null,
  result_bytes int default null,
  row_version bigint not null default 0,
  payload_retain_until datetime(3) not null,
  retain_until datetime(3) not null,
  finished_at datetime(3) default null,
  create_time datetime(3) not null default current_timestamp(3),
  update_time datetime(3) not null default current_timestamp(3),
  primary key (operation_id),
  unique key uk_mcp_operation_key
    (user_id, registered_client_id, tool_name, tool_version, idempotency_hash),
  key idx_mcp_operation_dispatch (state, queue_group, next_attempt_at, create_time, operation_id),
  key idx_mcp_operation_lease (state, lease_until, operation_id),
  key idx_mcp_operation_owner (user_id, create_time, operation_id),
  key idx_mcp_operation_grant (grant_id, state),
  key idx_mcp_operation_retention (state, retain_until, operation_id),
  constraint ck_mcp_operation_state check
    (state in ('PREPARED','CONFIRMED','QUEUED','RUNNING','SUCCEEDED','FAILED','UNKNOWN','CANCELLED','EXPIRED')),
  constraint ck_mcp_operation_counter check (row_version >= 0 and fence >= 0 and attempt >= 0)
) engine=innodb default charset=utf8mb4 comment='MCP 写操作账本，确认、幂等、派发与恢复的权威状态';

-- 操作事件：与状态变化同事务，只记录固定事件类型与结果码。
-- 每个逻辑阶段递增一次 row_version 并写一条对应版本事件；心跳不递增、不生成高频事件。
create table if not exists mcp_operation_event (
  event_id bigint not null auto_increment,
  operation_id char(32) character set ascii collate ascii_bin not null,
  operation_version bigint not null,
  event_type varchar(32) character set ascii collate ascii_bin not null,
  from_state varchar(16) character set ascii collate ascii_bin default null,
  to_state varchar(16) character set ascii collate ascii_bin not null,
  stage varchar(64) character set ascii collate ascii_bin not null,
  result_code varchar(64) character set ascii collate ascii_bin default null,
  actor_user_id bigint default null,
  request_id varchar(64) character set ascii collate ascii_bin not null,
  fence bigint not null default 0,
  create_time datetime(3) not null default current_timestamp(3),
  primary key (event_id),
  unique key uk_mcp_operation_event_version (operation_id, operation_version),
  key idx_mcp_operation_event (operation_id, event_id)
) engine=innodb default charset=utf8mb4 comment='MCP 写操作状态事件，禁止保存业务正文与凭据';
