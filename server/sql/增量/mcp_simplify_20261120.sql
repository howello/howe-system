-- ============================================================
-- MCP 六表与三参数增量迁移
-- 已写入：mcp.sql 的客户端登记、写操作账本、MCP 参数和管理菜单。
-- 适用：已完成旧版 MCP 写操作及后续步骤安装的库。
-- 切换前停止新调用，先处理已排队和执行中的任务，再停止旧版后端并备份。
-- 执行：mysql -u root -p howe-system < 增量/mcp_simplify_20261120.sql
-- 本增量执行结束可删除。
-- ============================================================

-- 在变更结构前拒绝仍有排队或运行任务的切换。请使用客户端默认的遇错停止模式。
set @mcp_sql = if(
  exists(select 1 from mcp_operation where state in ('QUEUED','RUNNING'))
  or exists(select 1 from mcp_operation_step where state in ('QUEUED','RUNNING')),
  'select * from mcp_finish_queued_and_running_tasks_before_upgrade',
  'select 1');
prepare mcp_stmt from @mcp_sql;
execute mcp_stmt;
deallocate prepare mcp_stmt;

set @mcp_has_legacy_clients = (select count(*) from information_schema.tables
  where table_schema=database() and table_name='mcp_client_ext');
set @mcp_has_legacy_consent = (select count(*) from information_schema.tables
  where table_schema=database() and table_name='oauth2_authorization_consent');
set @mcp_sql = if(exists(select 1 from information_schema.columns
  where table_schema=database() and table_name='oauth2_registered_client' and column_name='registration_source'),
  'select 1',
  'alter table oauth2_registered_client
  add column registration_source varchar(16) not null default ''CIMD'' comment ''DCR 或 CIMD'',
  add column status char(1) not null default ''1'' comment ''0启用 1停用'',
  add column metadata_uri varchar(2048) default null,
  add column metadata_digest char(64) character set ascii collate ascii_bin default null,
  add column metadata_json mediumtext,
  add column metadata_expires_at datetime(3) default null,
  add column metadata_version bigint not null default 0 comment ''CIMD 获取结果的并发版本'',
  add column metadata_valid char(1) not null default ''Y'' comment ''Y有效 N确定验证失败；不保存失败文档'',
  add column create_time datetime(3) not null default current_timestamp(3),
  add column update_time datetime(3) not null default current_timestamp(3),
  add column last_used_at datetime(3) default null,
  add index idx_mcp_client_status (status, create_time),
  add index idx_mcp_client_cleanup (update_time, create_time)');
prepare mcp_stmt from @mcp_sql;
execute mcp_stmt;
deallocate prepare mcp_stmt;

start transaction;
-- 保留内部登记 ID 和历史记录。由于取消用户逐项授权，旧客户端由管理员重新启用。
set @mcp_sql = if(@mcp_has_legacy_clients=1,
  'update oauth2_registered_client c join mcp_client_ext e on e.registered_client_id=c.id
      set c.registration_source=e.registration_source,c.status=''1'',c.metadata_uri=e.metadata_uri,
          c.metadata_digest=e.metadata_digest,c.metadata_json=e.metadata_json,
          c.metadata_expires_at=e.metadata_expires_at,c.metadata_version=e.metadata_version,
          c.metadata_valid=e.metadata_valid,c.create_time=e.create_time,
          c.update_time=e.update_time,c.last_used_at=e.last_used_at',
  'select 1');
prepare mcp_stmt from @mcp_sql;
execute mcp_stmt;
deallocate prepare mcp_stmt;

-- 撤销旧凭据，后续登录重新绑定当前账号权限。保留哈希和授权载荷的过期清理路径。
update mcp_grant set status='1',authorization_version=authorization_version+1,
       revoked_at=current_timestamp,update_time=current_timestamp
 where @mcp_has_legacy_consent=1 and status='0';
update mcp_token_index set status='REVOKED',update_time=current_timestamp
 where @mcp_has_legacy_consent=1 and status<>'REVOKED';
update mcp_operation set state='CANCELLED',stage='CANCELLED',result_code='rejected',
       row_version=row_version+1,finished_at=current_timestamp(3),update_time=current_timestamp(3)
 where state in ('PREPARED','CONFIRMED');
commit;

set @mcp_sql = if(exists(select 1 from information_schema.columns
  where table_schema=database() and table_name='mcp_operation' and column_name='confirmation_hash'),
  'alter table mcp_operation
  drop check ck_mcp_operation_state,
  drop column approved_scopes,
  drop column confirmed_by,
  drop column confirmed_at,
  drop column confirmation_summary_hash,
  drop column confirmation_session_hash,
  drop column confirmation_hash,
  drop column confirmation_expires_at,
  drop column confirmation_used_at,
  drop column prepare_expires_at,
  add constraint ck_mcp_operation_states check
    (state in (''QUEUED'',''RUNNING'',''SUCCEEDED'',''FAILED'',''UNKNOWN'',''CANCELLED'',''EXPIRED''))',
  'select 1');
prepare mcp_stmt from @mcp_sql;
execute mcp_stmt;
deallocate prepare mcp_stmt;

drop table if exists mcp_client_ext;
drop table if exists mcp_tool_config;
drop table if exists mcp_call_log;
drop table if exists mcp_operation_event;
drop table if exists oauth2_authorization_consent;

delete from sys_config where config_key in (
  'mcp.cleanup.credentialGraceMinutes',
  'mcp.cleanup.unusedClientRetentionDays',
  'mcp.log.retentionDays',
  'mcp.operation.localConcurrency',
  'mcp.operation.mealAuditConcurrency',
  'mcp.operation.mealConcurrency',
  'mcp.operation.publishConcurrency',
  'mcp.operation.retainDays',
  'mcp.operation.totalConcurrency',
  'mcp.operation.userConcurrency',
  'mcp.rate.authorizeIpPerMinute',
  'mcp.rate.browserIpPerMinute',
  'mcp.rate.loginIpPerMinute',
  'mcp.rate.mcpIpPerMinute',
  'mcp.rate.mcpUserPerMinute',
  'mcp.rate.metadataIpPerMinute',
  'mcp.rate.revokeIpPerMinute',
  'mcp.rate.tokenIpPerMinute',
  'mcp.registration.ipPerMinute',
  'mcp.registration.maxClients',
  'mcp.tool.concurrency',
  'mcp.tool.userConcurrency'
);
delete rm from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id
 where m.perms like 'mcp:tool:%' or m.perms like 'mcp:calllog:%' or m.perms like 'mcp:log:%';
delete from sys_menu
 where perms like 'mcp:tool:%' or perms like 'mcp:calllog:%' or perms like 'mcp:log:%';
