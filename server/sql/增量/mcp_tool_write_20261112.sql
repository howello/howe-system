-- ============================================================
-- MCP 写操作参数增量（C3）
--
-- 已写入：mcp.sql 的「写操作配置」一节（新库跑 mcp.sql 即可，不需要本文件）
-- 适用：已有库补齐操作执行并发与保留参数
-- 执行：mysql -u root -p howe-system < 增量/mcp_tool_write_20261112.sql
-- 本增量执行结束可删除。
-- ============================================================

insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-本地写执行并发', 'mcp.operation.localConcurrency', '4', 'Y', 'MCP', 'N', 'admin', sysdate(),
       '1到32，启动时读取；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key = 'mcp.operation.localConcurrency');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-博客发布执行并发', 'mcp.operation.publishConcurrency', '1', 'Y', 'MCP', 'N', 'admin', sysdate(),
       '1到8，启动时读取；修改后重启后端生效'
where not exists (select 1 from sys_config where config_key = 'mcp.operation.publishConcurrency');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, `sensitive`, create_by, create_time, remark)
select 'MCP-操作账本保留天数', 'mcp.operation.retainDays', '30', 'Y', 'MCP', 'N', 'admin', sysdate(),
       '已知终态元数据保留天数；UNKNOWN 不按时间清理'
where not exists (select 1 from sys_config where config_key = 'mcp.operation.retainDays');

-- C3 写工具种子（仅保留说说与公告写工具，默认关闭）。
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_talk_create', 'N', 'admin', 'C3：创建博客说说（需浏览器确认）'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_talk_create');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_talk_update', 'N', 'admin', 'C3：更新博客说说（版本 CAS）'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_talk_update');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'system_notice_mark_read', 'N', 'admin', 'C3：批量标记本人公告已读（受控可读校验）'
where not exists (select 1 from mcp_tool_config where tool_name = 'system_notice_mark_read');
insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'mcp_operation_get', 'N', 'admin', 'C3：查询本人写操作状态'
where not exists (select 1 from mcp_tool_config where tool_name = 'mcp_operation_get');
