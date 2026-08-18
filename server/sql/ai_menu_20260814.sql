-- 管理员助手阶段一菜单补齐：父菜单 ai 已建（ai_admin_assistant_phase_one.sql），
-- 但当时只插入了 agent 一个子菜单。前端 views/ai/ 实际有 agent/chat/config/run/usage 五个页面，
-- 其中 channel/model/route/price 合并进统一配置页 config（对应后端 AiConfigController 的 /{resource} 路径参数）。
-- 这里补齐 chat/config/run/usage 四个子菜单，权限码与各 Controller 的 @PreAuthorize 对齐：
--   chat   -> ai:run:execute（发起会话需要执行权限）
--   config -> ai:config:list（统一配置页，覆盖 provider/channel/model/route/price）
--   run    -> ai:run:view
--   usage  -> ai:run:view（用量成本复用查看权限）
-- 幂等：全部 WHERE NOT EXISTS，可重复执行。不硬编码 menu_id（auto_increment 从 2000 起，当前 AI 菜单已到 2030+）。
-- route_name 显式指定：RuoYi 的 getRouteName 在 route_name 为空时用 path 生成路由 name（path 首字母大写），
-- 若两个不同父菜单下有同名 path 子菜单（AI 的 config 与「系统管理」的 参数设置 path 都是 'config'），
-- 会生成相同的 route name（Config），Vue Router 4 的 addRoute 同名覆盖会让后注册者挤掉先注册者，
-- 表现为点击先注册那条菜单 404。故给每个 AI 子菜单显式写独立 route_name（Ai 前缀）规避。

SET @ai_parent := NULL;
SELECT @ai_parent := menu_id FROM sys_menu WHERE path='ai' AND parent_id=0 LIMIT 1;

-- AI 对话（Chat）：发起会话/消息，走 ai:run:execute
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, route_name, create_by, create_time)
SELECT 'AI 对话', @ai_parent, 2, 'chat', 'ai/chat/index', 'C', '0', '0', 'ai:run:execute', 'message', 'AiChat', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='chat' AND parent_id=@ai_parent);

-- AI 配置（统一配置页：供应商/渠道/模型/路由/价格）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, route_name, create_by, create_time)
SELECT 'AI 配置', @ai_parent, 3, 'config', 'ai/config/index', 'C', '0', '0', 'ai:config:list', 'system', 'AiConfig', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='config' AND parent_id=@ai_parent);

-- 运行记录（Run）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, route_name, create_by, create_time)
SELECT '运行记录', @ai_parent, 4, 'run', 'ai/run/index', 'C', '0', '0', 'ai:run:view', 'log', 'AiRun', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='run' AND parent_id=@ai_parent);

-- 用量成本（Usage）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, route_name, create_by, create_time)
SELECT '用量成本', @ai_parent, 5, 'usage', 'ai/usage/index', 'C', '0', '0', 'ai:run:view', 'chart', 'AiUsage', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='usage' AND parent_id=@ai_parent);

-- ---------------------------------------------------------------------------
-- 按钮权限码：把各 Controller 实际声明的 @PreAuthorize 权限注册为按钮级菜单（menu_type='F'），
-- 挂在对应页面菜单下。仅注册用于按钮显隐的细粒度权限码；list 类权限已作为页面入口权限。
-- 同样幂等、不硬编码 menu_id。
-- ---------------------------------------------------------------------------

-- Agent 页面按钮：publish / disable / view（edit 已是入口权限 ai:agent:list 的近邻，这里补动作级）
SET @ai_agent_menu := NULL;
SELECT @ai_agent_menu := menu_id FROM sys_menu WHERE path='agent' AND parent_id=@ai_parent LIMIT 1;
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT 'Agent 发布', @ai_agent_menu, 1, '', '', 'F', '0', '0', 'ai:agent:publish', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:agent:publish' AND parent_id=@ai_agent_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT 'Agent 停用', @ai_agent_menu, 2, '', '', 'F', '0', '0', 'ai:agent:disable', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:agent:disable' AND parent_id=@ai_agent_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT 'Agent 查看', @ai_agent_menu, 3, '', '', 'F', '0', '0', 'ai:agent:view', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:agent:view' AND parent_id=@ai_agent_menu);

-- Config 页面按钮：add / edit / remove / test / key:replace
SET @ai_config_menu := NULL;
SELECT @ai_config_menu := menu_id FROM sys_menu WHERE path='config' AND parent_id=@ai_parent LIMIT 1;
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '配置新增', @ai_config_menu, 1, '', '', 'F', '0', '0', 'ai:config:add', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:config:add' AND parent_id=@ai_config_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '配置修改', @ai_config_menu, 2, '', '', 'F', '0', '0', 'ai:config:edit', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:config:edit' AND parent_id=@ai_config_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '配置删除', @ai_config_menu, 3, '', '', 'F', '0', '0', 'ai:config:remove', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:config:remove' AND parent_id=@ai_config_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '连通测试', @ai_config_menu, 4, '', '', 'F', '0', '0', 'ai:config:test', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:config:test' AND parent_id=@ai_config_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '密钥替换', @ai_config_menu, 5, '', '', 'F', '0', '0', 'ai:config:key:replace', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:config:key:replace' AND parent_id=@ai_config_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '配置查询', @ai_config_menu, 6, '', '', 'F', '0', '0', 'ai:config:query', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:config:query' AND parent_id=@ai_config_menu);

-- Run 页面按钮：execute / cancel
SET @ai_run_menu := NULL;
SELECT @ai_run_menu := menu_id FROM sys_menu WHERE path='run' AND parent_id=@ai_parent LIMIT 1;
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '运行执行', @ai_run_menu, 1, '', '', 'F', '0', '0', 'ai:run:execute', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:run:execute' AND parent_id=@ai_run_menu);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, create_by, create_time)
SELECT '运行取消', @ai_run_menu, 2, '', '', 'F', '0', '0', 'ai:run:cancel', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:run:cancel' AND parent_id=@ai_run_menu);
