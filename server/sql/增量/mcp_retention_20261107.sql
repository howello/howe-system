-- 已安装 MCP 的清理参数与候选扫描索引。幂等、非破坏；最终结构已并入 mcp.sql。
set @mcpCleanupDdl = if(exists(select 1 from information_schema.statistics where table_schema=database() and table_name='oauth2_authorization' and index_name='idx_mcp_authorization_cleanup'),
 'select 1','alter table oauth2_authorization add index idx_mcp_authorization_cleanup(update_time,id)');
prepare mcpCleanupStatement from @mcpCleanupDdl;
execute mcpCleanupStatement;
deallocate prepare mcpCleanupStatement;
set @mcpCleanupDdl = if(exists(select 1 from information_schema.statistics where table_schema=database() and table_name='mcp_client_ext' and index_name='idx_mcp_client_cleanup'),
 'select 1','alter table mcp_client_ext add index idx_mcp_client_cleanup(update_time,create_time)');
prepare mcpCleanupStatement from @mcpCleanupDdl;
execute mcpCleanupStatement;
deallocate prepare mcpCleanupStatement;

-- 安全清理参数：非法值拒绝整轮清理，不缩短为默认值。
insert into sys_config (config_name,config_key,config_value,config_type,param_type,`sensitive`,create_by,create_time,remark)
select 'MCP-凭据清理宽限分钟','mcp.cleanup.credentialGraceMinutes','60','Y','MCP','N','admin',sysdate(),'全部凭据和刷新家族绝对过期后再保留；范围 1..1440 分钟，后续清理轮次生效'
where not exists(select 1 from sys_config where config_key='mcp.cleanup.credentialGraceMinutes');
insert into sys_config (config_name,config_key,config_value,config_type,param_type,`sensitive`,create_by,create_time,remark)
select 'MCP-从未授权客户端保留天数','mcp.cleanup.unusedClientRetentionDays','30','Y','MCP','N','admin',sysdate(),'仅清理正常、元数据有效且从未授权的闲置登记；范围 1..3650 天，后续轮次生效'
where not exists(select 1 from sys_config where config_key='mcp.cleanup.unusedClientRetentionDays');
