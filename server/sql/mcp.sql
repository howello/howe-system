-- MCP 模块完整安装脚本（MySQL 8）。先执行 base.sql。
-- 本脚本重建 MCP 表，适用于全新安装。已有 MCP 库使用增量脚本升级。

drop table if exists mcp_operation_step;
drop table if exists mcp_operation;
drop table if exists mcp_token_index;
drop table if exists oauth2_authorization;
drop table if exists mcp_grant;
drop table if exists oauth2_registered_client;

-- Spring Authorization Server 继续使用原客户端列。管理状态和 CIMD 缓存与登记合并。
create table oauth2_registered_client (
  id varchar(100) character set ascii collate ascii_bin not null,
  client_id varchar(2048) character set ascii collate ascii_bin not null,
  client_id_issued_at datetime(3) not null default current_timestamp(3),
  client_secret varchar(200) default null,
  client_secret_expires_at datetime(3) default null,
  client_name varchar(200) not null,
  client_authentication_methods varchar(1000) not null,
  authorization_grant_types varchar(1000) not null,
  redirect_uris text,
  post_logout_redirect_uris text,
  scopes varchar(1000) not null,
  client_settings text not null,
  token_settings text not null,
  registration_source varchar(16) not null default 'CIMD' comment 'DCR 或 CIMD',
  status char(1) not null default '1' comment '0启用 1停用',
  metadata_uri varchar(2048) default null,
  metadata_digest char(64) character set ascii collate ascii_bin default null,
  metadata_json mediumtext,
  metadata_expires_at datetime(3) default null,
  metadata_version bigint not null default 0 comment 'CIMD 获取结果的并发版本',
  metadata_valid char(1) not null default 'Y' comment 'Y有效 N确定验证失败；不保存失败文档',
  create_time datetime(3) not null default current_timestamp(3),
  update_time datetime(3) not null default current_timestamp(3),
  last_used_at datetime(3) default null,
  primary key (id),
  unique key uk_mcp_client_id (client_id),
  key idx_mcp_client_status (status, create_time),
  key idx_mcp_client_cleanup (update_time, create_time)
) engine=innodb default charset=utf8mb4 comment='MCP OAuth 客户端登记';

create table mcp_grant (
  grant_id bigint not null auto_increment,
  user_id bigint not null,
  registered_client_id varchar(100) character set ascii collate ascii_bin not null,
  resource_uri varchar(2048) character set ascii collate ascii_bin not null,
  status char(1) not null default '0' comment '0有效 1已撤销',
  authorization_version bigint not null default 1,
  revoked_at datetime(3) default null,
  create_time datetime(3) not null default current_timestamp(3),
  update_time datetime(3) not null default current_timestamp(3),
  primary key (grant_id),
  unique key uk_mcp_grant_owner (user_id, registered_client_id, resource_uri),
  key idx_mcp_grant_client (registered_client_id, status),
  key idx_mcp_grant_user (user_id, status, update_time)
) engine=innodb default charset=utf8mb4 comment='MCP 用户客户端资源授权，';

create table oauth2_authorization (
  id varchar(100) character set ascii collate ascii_bin not null,
  registered_client_id varchar(100) character set ascii collate ascii_bin not null,
  principal_name varchar(100) character set ascii collate ascii_bin not null,
  grant_id bigint default null comment '用户同意前可为空',
  authorization_version bigint default null,
  row_version bigint not null default 0 comment '并发保存版本',
  encrypted_payload mediumtext not null comment 'SecretCipher enc:v1 密文，不允许明文降级',
  create_time datetime(3) not null default current_timestamp(3),
  update_time datetime(3) not null default current_timestamp(3),
  primary key (id),
  key idx_mcp_authorization_grant (grant_id),
  key idx_mcp_authorization_owner (registered_client_id, principal_name),
  key idx_mcp_authorization_cleanup (update_time, id)
) engine=innodb default charset=utf8mb4 comment='MCP OAuth 加密授权载荷';

create table mcp_token_index (
  token_hash char(64) character set ascii collate ascii_bin not null,
  token_type varchar(32) character set ascii collate ascii_bin not null,
  authorization_id varchar(100) character set ascii collate ascii_bin not null,
  grant_id bigint default null,
  family_id varchar(100) character set ascii collate ascii_bin default null,
  status varchar(16) not null default 'ACTIVE' comment 'ACTIVE USED REVOKED',
  issued_at datetime(3) not null,
  expires_at datetime(3) not null,
  family_expires_at datetime(3) default null comment '刷新家族绝对截止时间，保留重放记录的下界',
  replaced_by_hash char(64) character set ascii collate ascii_bin default null,
  update_time datetime(3) not null default current_timestamp(3),
  primary key (token_hash),
  key idx_mcp_token_authorization (authorization_id, token_type),
  key idx_mcp_token_family (family_id, status),
  key idx_mcp_token_grant (grant_id, status),
  key idx_mcp_token_expiry (expires_at)
) engine=innodb default charset=utf8mb4 comment='MCP OAuth 凭据哈希索引和轮换历史';

-- 写操作账本和后续步骤。
create table mcp_operation (
  operation_id char(32) character set ascii collate ascii_bin not null,
  user_id bigint not null,
  registered_client_id varchar(100) character set ascii collate ascii_bin not null,
  grant_id bigint not null,
  grant_version bigint not null,
  resource_uri varchar(2048) character set ascii collate ascii_bin not null,
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
    comment 'QUEUED RUNNING SUCCEEDED FAILED UNKNOWN CANCELLED EXPIRED',
  stage varchar(64) character set ascii collate ascii_bin not null,
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
  constraint ck_mcp_operation_states check
    (state in ('QUEUED','RUNNING','SUCCEEDED','FAILED','UNKNOWN','CANCELLED','EXPIRED')),
  constraint ck_mcp_operation_counter check (row_version >= 0 and fence >= 0 and attempt >= 0)
) engine=innodb default charset=utf8mb4 comment='MCP 写操作账本，幂等、派发与恢复的权威状态';

create table mcp_operation_step (
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

-- MCP 参数设置。
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-运行开关', 'mcp.enabled', 'false', 'Y', 'MCP', 'N', 'admin', sysdate(), '安装后仍默认关闭；关闭后允许用户查看和撤销连接'
where not exists (select 1 from sys_config where config_key='mcp.enabled');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-后端公共地址', 'mcp.publicBaseUrl', 'https://s.wyantao.com', 'Y', 'MCP', 'N', 'admin', sysdate(), '派生 issuer 和 /mcp resource；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key='mcp.publicBaseUrl');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-前端授权页面地址', 'mcp.frontendBaseUrl', 'https://admin.wyantao.com', 'Y', 'MCP', 'N', 'admin', sysdate(), '登录、授权与我的连接页面；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key='mcp.frontendBaseUrl');

-- 客户端和授权管理菜单。
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select 'MCP 管理', 0, 8, 'mcp-admin', null, 1, 0, 'M', '0', '0', '', 'link', 'admin', sysdate(), '外部客户端与用户授权管理'
where not exists (select 1 from sys_menu where parent_id=0 and path='mcp-admin');
select @mcpDirId := menu_id from sys_menu where parent_id=0 and path='mcp-admin' limit 1;

insert into sys_menu (menu_name, parent_id, order_num, path, component, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '客户端', @mcpDirId, 1, 'client', 'mcp/client/index', 'McpClient', 1, 0, 'C', '0', '0', 'mcp:client:list', 'peoples', 'admin', sysdate(), ''
where not exists (select 1 from sys_menu where perms='mcp:client:list');
insert into sys_menu (menu_name, parent_id, order_num, path, component, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '用户授权', @mcpDirId, 3, 'grant', 'mcp/grant/index', 'McpGrant', 1, 0, 'C', '0', '0', 'mcp:grant:list', 'lock', 'admin', sysdate(), ''
where not exists (select 1 from sys_menu where perms='mcp:grant:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select actions.label, parent.menu_id, actions.seq, '#', '', 1, 0, 'F', '0', '0', actions.perm, '#', 'admin', sysdate(), ''
from (
  select '客户端查询' label, 'mcp:client:list' parent_perm, 'mcp:client:query' perm, 1 seq
  union all select '客户端状态修改', 'mcp:client:list', 'mcp:client:edit', 2
  union all select '授权查询', 'mcp:grant:list', 'mcp:grant:query', 1
  union all select '授权撤销', 'mcp:grant:list', 'mcp:grant:revoke', 2
) actions join sys_menu parent on parent.perms=actions.parent_perm
where not exists (select 1 from sys_menu existing where existing.perms=actions.perm);
