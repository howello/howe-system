-- CIMD 缓存并发版本及同日工具并发参数补丁。
-- 已写入：mcp.sql 的 mcp_client_ext 建表与工具并发参数一节；新安装不需要本补丁。
-- 适用：已安装早期 MCP 表的库，保留全部客户端、授权与令牌。
-- 本增量执行结束可删除。仅由运维对目标库显式执行。
set @mcpCimdDdl = if(
  (select count(*) from information_schema.columns where table_schema=database()
     and table_name='mcp_client_ext' and column_name='metadata_version')=0,
  'alter table mcp_client_ext add column metadata_version bigint not null default 0 comment ''CIMD 获取结果的并发版本''',
  'select 1');
prepare mcpCimdStatement from @mcpCimdDdl;
execute mcpCimdStatement;
deallocate prepare mcpCimdStatement;
set @mcpCimdDdl = if(
  (select count(*) from information_schema.columns where table_schema=database()
     and table_name='mcp_client_ext' and column_name='metadata_valid')=0,
  'alter table mcp_client_ext add column metadata_valid char(1) not null default ''Y'' comment ''Y有效 N确定验证失败''',
  'select 1');
prepare mcpCimdStatement from @mcpCimdDdl;
execute mcpCimdStatement;
deallocate prepare mcpCimdStatement;

-- 同日工具并发参数补种，已写回 mcp.sql 参数一节；缺失时应用仍采用默认 2。

insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-每用户工具执行并发', 'mcp.tool.userConcurrency', '2', 'Y', 'MCP', 'N', 'admin', sysdate(), '1到64；当前进程内同一用户跨客户端共享；启动读取，修改后重启后端生效'
where not exists (select 1 from sys_config where config_key='mcp.tool.userConcurrency');
