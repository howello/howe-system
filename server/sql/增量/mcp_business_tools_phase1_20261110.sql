-- C1 平台基础：两个试点业务工具默认关闭。
-- 可重复执行；只补缺失行，不覆盖管理员已调整的 enabled。
-- 执行前确认已运行 mcp_install_20261106.sql 等既有安装脚本。

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_article_list', 'N', 'admin', 'C1 试点：博客文章索引只读；验收后按需开启'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_article_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'system_self_get', 'N', 'admin', 'C1 试点：当前账号最小信息；验收后按需开启'
where not exists (select 1 from mcp_tool_config where tool_name = 'system_self_get');
