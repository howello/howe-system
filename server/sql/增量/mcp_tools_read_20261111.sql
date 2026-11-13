-- C2 只读工具：22 个业务工具默认关闭。
-- 可重复执行；只补缺失行，不覆盖管理员已调整的 enabled。
-- 执行前确认已运行 mcp_install_20261106.sql 与 mcp_business_tools_phase1_20261110.sql。

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_article_get', 'N', 'admin', 'C2：博客文章详情（可选读取正文，不回写索引）'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_article_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_draft_list', 'N', 'admin', 'C2：博客草稿列表（编辑资源范围）'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_draft_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_draft_get', 'N', 'admin', 'C2：博客草稿详情（有界正文）'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_draft_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_talk_list', 'N', 'admin', 'C2：博客说说列表（有界正文摘要）'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_talk_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'blog_talk_get', 'N', 'admin', 'C2：博客说说详情（有界正文）'
where not exists (select 1 from mcp_tool_config where tool_name = 'blog_talk_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_category_list', 'N', 'admin', 'C2：点餐分类列表（公共 + 本家庭）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_category_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_category_get', 'N', 'admin', 'C2：点餐分类详情'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_category_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_dish_list', 'N', 'admin', 'C2：点餐菜品列表（公共 + 本家庭）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_dish_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_dish_get', 'N', 'admin', 'C2：点餐菜品详情（结构化白名单字段）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_dish_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_signature_list', 'N', 'admin', 'C2：招牌菜（近 90 天最多 3 条）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_signature_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_order_my_list', 'N', 'admin', 'C2：本人订单列表（日期左闭右开）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_order_my_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_order_get', 'N', 'admin', 'C2：订单详情（家庭内可读，有界明细）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_order_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_proposal_my_list', 'N', 'admin', 'C2：本人菜品提案列表'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_proposal_my_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_proposal_get', 'N', 'admin', 'C2：提案详情（家庭内可读）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_proposal_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_review_my_list', 'N', 'admin', 'C2：本人评价列表'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_review_my_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'meal_assignee_list', 'N', 'admin', 'C2：可指派厨师（最小字段，数据库分页）'
where not exists (select 1 from mcp_tool_config where tool_name = 'meal_assignee_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'share_link_list', 'N', 'admin', 'C2：分享列表（管理范围，隐藏短链与路径）'
where not exists (select 1 from mcp_tool_config where tool_name = 'share_link_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'share_link_get', 'N', 'admin', 'C2：分享详情（不消费访问次数）'
where not exists (select 1 from mcp_tool_config where tool_name = 'share_link_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'ai_model_list', 'N', 'admin', 'C2：AI 模型目录安全投影'
where not exists (select 1 from mcp_tool_config where tool_name = 'ai_model_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'ai_usage_stats_get', 'N', 'admin', 'C2：AI 用量统计（数据库聚合分页，费用为 null）'
where not exists (select 1 from mcp_tool_config where tool_name = 'ai_usage_stats_get');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'system_notice_my_list', 'N', 'admin', 'C2：本人公告分页（正常公告 + 本人已读）'
where not exists (select 1 from mcp_tool_config where tool_name = 'system_notice_my_list');

insert into mcp_tool_config (tool_name, enabled, update_by, remark)
select 'system_health_get', 'N', 'admin', 'C2：服务器健康摘要（无主机敏感信息）'
where not exists (select 1 from mcp_tool_config where tool_name = 'system_health_get');
