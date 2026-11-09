-- 已安装环境的调用记录菜单命名对齐，保留 menu_id 与 sys_role_menu 的现有关联。
-- 可重复执行；新安装脚本已使用最终 calllog 路径，无需重跑全量建表。
update sys_menu set path='calllog',component='mcp/calllog/index',route_name='McpCalllog',perms='mcp:calllog:list'
 where perms='mcp:log:list';
update sys_menu set perms='mcp:calllog:query' where perms='mcp:log:query';
