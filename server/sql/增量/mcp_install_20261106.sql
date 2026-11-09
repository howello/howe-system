-- MCP 首次安装增量（MySQL 8）。
-- 已写入：mcp.sql 的建表、参数和菜单章节。
-- 适用：已有系统库首次安装 MCP；保留既有表、参数值、菜单及角色绑定。
-- 本增量执行结束可删除；不得用本脚本升级未来不兼容的 MCP 表结构。
-- 执行：mysql -u root -p howe-system < 增量/mcp_install_20261106.sql

create table if not exists oauth2_registered_client (
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
  primary key (id),
  unique key uk_mcp_client_id (client_id)
) engine=innodb default charset=utf8mb4 comment='MCP OAuth 客户端登记';

create table if not exists mcp_client_ext (
  registered_client_id varchar(100) character set ascii collate ascii_bin not null,
  registration_source varchar(16) not null comment 'DCR 或 CIMD',
  status char(1) not null default '0' comment '0正常 1停用',
  metadata_uri varchar(2048) default null,
  metadata_digest char(64) character set ascii collate ascii_bin default null,
  metadata_json mediumtext,
  metadata_expires_at datetime(3) default null,
  metadata_version bigint not null default 0 comment 'CIMD 获取结果的并发版本',
  metadata_valid char(1) not null default 'Y' comment 'Y有效 N确定验证失败；不保存失败文档',
  create_time datetime(3) not null default current_timestamp(3),
  update_time datetime(3) not null default current_timestamp(3),
  last_used_at datetime(3) default null,
  primary key (registered_client_id),
  key idx_mcp_client_status (status, create_time),
  key idx_mcp_client_cleanup (update_time, create_time)
) engine=innodb default charset=utf8mb4 comment='MCP 客户端来源和管理状态';

create table if not exists oauth2_authorization_consent (
  registered_client_id varchar(100) character set ascii collate ascii_bin not null,
  principal_name varchar(100) character set ascii collate ascii_bin not null comment '系统用户ID字符串',
  authorities varchar(1000) not null,
  primary key (registered_client_id, principal_name)
) engine=innodb default charset=utf8mb4 comment='MCP OAuth 用户同意范围';

create table if not exists mcp_grant (
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
) engine=innodb default charset=utf8mb4 comment='MCP 用户客户端资源授权，不另存同意范围';

-- 自定义 OAuth2AuthorizationService 使用的加密存储，不使用框架默认明文 token 列。
create table if not exists oauth2_authorization (
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

create table if not exists mcp_token_index (
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

create table if not exists mcp_tool_config (
  tool_name varchar(64) character set ascii collate ascii_bin not null,
  enabled char(1) not null default 'N',
  update_by varchar(64) default '',
  update_time datetime(3) not null default current_timestamp(3),
  remark varchar(500) default '',
  primary key (tool_name)
) engine=innodb default charset=utf8mb4 comment='MCP 工具开放状态，定义与权限来自代码';

create table if not exists mcp_call_log (
  log_id bigint not null auto_increment,
  request_id varchar(64) character set ascii collate ascii_bin not null,
  event_type varchar(32) not null,
  user_id bigint default null,
  registered_client_id varchar(100) character set ascii collate ascii_bin default null,
  grant_id bigint default null,
  tool_name varchar(64) default null,
  result_code varchar(64) not null,
  elapsed_ms bigint not null default 0,
  summary varchar(500) not null default '' comment '仅白名单摘要，禁止保存凭据及原始报文',
  create_time datetime(3) not null default current_timestamp(3),
  primary key (log_id),
  key idx_mcp_log_user (user_id, create_time),
  key idx_mcp_log_client (registered_client_id, create_time),
  key idx_mcp_log_grant (grant_id, create_time),
  key idx_mcp_log_retention (create_time)
) engine=innodb default charset=utf8mb4 comment='MCP 授权与调用审计';

-- 新增参数仅在缺失时插入，不覆盖已有在线值；两项公共地址重启后端后生效。
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-运行开关', 'mcp.enabled', 'false', 'Y', 'MCP', 'N', 'admin', sysdate(), '安装后仍默认关闭；关闭后允许用户查看和撤销连接'
where not exists (select 1 from sys_config where config_key='mcp.enabled');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-后端公共地址', 'mcp.publicBaseUrl', 'https://s.wyantao.com', 'Y', 'MCP', 'N', 'admin', sysdate(), '派生 issuer 和 /mcp resource；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key='mcp.publicBaseUrl');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-前端授权页面地址', 'mcp.frontendBaseUrl', 'https://admin.wyantao.com', 'Y', 'MCP', 'N', 'admin', sysdate(), '登录、授权与我的连接页面；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key='mcp.frontendBaseUrl');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-公开登记每IP每分钟上限', 'mcp.registration.ipPerMinute', '10', 'Y', 'MCP', 'N', 'admin', sysdate(), 'DCR 与 CIMD 登记入口限流'
where not exists (select 1 from sys_config where config_key='mcp.registration.ipPerMinute');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-客户端登记总量上限', 'mcp.registration.maxClients', '10000', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；此参数行同时用于串行校验登记配额，不可删除'
where not exists (select 1 from sys_config where config_key='mcp.registration.maxClients');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-日志保留天数', 'mcp.log.retentionDays', '30', 'Y', 'MCP', 'N', 'admin', sysdate(), '不用于清理仍需保留的刷新重放索引'
where not exists (select 1 from sys_config where config_key='mcp.log.retentionDays');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-工具最大执行并发', 'mcp.tool.concurrency', '8', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到64，启动时读取；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key='mcp.tool.concurrency');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-协议每IP每分钟上限', 'mcp.rate.mcpIpPerMinute', '300', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.mcpIpPerMinute');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-协议每用户每分钟上限', 'mcp.rate.mcpUserPerMinute', '120', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.mcpUserPerMinute');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-授权每IP每分钟上限', 'mcp.rate.authorizeIpPerMinute', '60', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.authorizeIpPerMinute');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-令牌每IP每分钟上限', 'mcp.rate.tokenIpPerMinute', '60', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.tokenIpPerMinute');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-撤销每IP每分钟上限', 'mcp.rate.revokeIpPerMinute', '60', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.revokeIpPerMinute');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-元数据每IP每分钟上限', 'mcp.rate.metadataIpPerMinute', '120', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.metadataIpPerMinute');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-登录每IP每分钟上限', 'mcp.rate.loginIpPerMinute', '10', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.loginIpPerMinute');
insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-浏览器自助每IP每分钟上限', 'mcp.rate.browserIpPerMinute', '120', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到100000；共享Redis 60秒窗口，在线修改后续请求生效'
where not exists (select 1 from sys_config where config_key='mcp.rate.browserIpPerMinute');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-每用户工具执行并发', 'mcp.tool.userConcurrency', '2', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到64；当前进程内同一用户跨客户端共享；启动读取，修改后重启后端生效'
where not exists (select 1 from sys_config where config_key='mcp.tool.userConcurrency');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'mcp_connection_check', 'Y', 'admin', '唯一首期工具；仍受 mcp.enabled 总开关及用户授权约束'
where not exists (select 1 from mcp_tool_config where tool_name='mcp_connection_check');

-- 管理菜单按路径和权限查询父级，自增 ID；增量重跑保留既有菜单及角色绑定。
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select 'MCP 管理', 0, 8, 'mcp-admin', null, 1, 0, 'M', '0', '0', '', 'link', 'admin', sysdate(), '外部客户端与用户授权管理'
where not exists (select 1 from sys_menu where parent_id=0 and path='mcp-admin');
select @mcpDirId := menu_id from sys_menu where parent_id=0 and path='mcp-admin' limit 1;

insert into sys_menu (menu_name, parent_id, order_num, path, component, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '客户端', @mcpDirId, 1, 'client', 'mcp/client/index', 'McpClient', 1, 0, 'C', '0', '0', 'mcp:client:list', 'peoples', 'admin', sysdate(), ''
where not exists (select 1 from sys_menu where perms='mcp:client:list');
insert into sys_menu (menu_name, parent_id, order_num, path, component, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '工具目录', @mcpDirId, 2, 'tool', 'mcp/tool/index', 'McpTool', 1, 0, 'C', '0', '0', 'mcp:tool:list', 'tool', 'admin', sysdate(), ''
where not exists (select 1 from sys_menu where perms='mcp:tool:list');
insert into sys_menu (menu_name, parent_id, order_num, path, component, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '用户授权', @mcpDirId, 3, 'grant', 'mcp/grant/index', 'McpGrant', 1, 0, 'C', '0', '0', 'mcp:grant:list', 'lock', 'admin', sysdate(), ''
where not exists (select 1 from sys_menu where perms='mcp:grant:list');
insert into sys_menu (menu_name, parent_id, order_num, path, component, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '调用记录', @mcpDirId, 4, 'calllog', 'mcp/calllog/index', 'McpCalllog', 1, 0, 'C', '0', '0', 'mcp:calllog:list', 'list', 'admin', sysdate(), ''
where not exists (select 1 from sys_menu where perms='mcp:calllog:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select actions.label, parent.menu_id, actions.seq, '#', '', 1, 0, 'F', '0', '0', actions.perm, '#', 'admin', sysdate(), ''
from (
  select '客户端查询' label, 'mcp:client:list' parent_perm, 'mcp:client:query' perm, 1 seq
  union all select '客户端状态修改', 'mcp:client:list', 'mcp:client:edit', 2
  union all select '工具查询', 'mcp:tool:list', 'mcp:tool:query', 1
  union all select '工具开关修改', 'mcp:tool:list', 'mcp:tool:edit', 2
  union all select '授权查询', 'mcp:grant:list', 'mcp:grant:query', 1
  union all select '授权撤销', 'mcp:grant:list', 'mcp:grant:revoke', 2
  union all select '记录查询', 'mcp:calllog:list', 'mcp:calllog:query', 1
) actions join sys_menu parent on parent.perms=actions.parent_perm
where not exists (select 1 from sys_menu existing where existing.perms=actions.perm);

-- 安全清理参数：非法值拒绝整轮清理，不缩短为默认值。
insert into sys_config (config_name,config_key,config_value,config_type,param_type,`sensitive`,create_by,create_time,remark)
select 'MCP-凭据清理宽限分钟','mcp.cleanup.credentialGraceMinutes','60','Y','MCP','N','admin',sysdate(),'全部凭据和刷新家族绝对过期后再保留；范围 1..1440 分钟，后续清理轮次生效'
where not exists(select 1 from sys_config where config_key='mcp.cleanup.credentialGraceMinutes');
insert into sys_config (config_name,config_key,config_value,config_type,param_type,`sensitive`,create_by,create_time,remark)
select 'MCP-从未授权客户端保留天数','mcp.cleanup.unusedClientRetentionDays','30','Y','MCP','N','admin',sysdate(),'仅清理正常、元数据有效且从未授权的闲置登记；范围 1..3650 天，后续轮次生效'
where not exists(select 1 from sys_config where config_key='mcp.cleanup.unusedClientRetentionDays');
