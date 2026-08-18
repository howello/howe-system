-- 修复「系统管理 > 参数设置」点击 404。
-- 根因：ai_menu_20260814.sql 在 AI 父菜单下插入了 path='config' 的子菜单「AI 配置」，
-- 与「系统管理」下既有的 path='config' 子菜单「参数设置」(menu_id=106) 同 path。
-- RuoYi 后端 getRouteName 在 route_name 为空时用 path 生成 route name（首字母大写），
-- 两者都得到 'Config'，Vue Router 4 的 addRoute 同名覆盖让后注册的 AI 配置挤掉参数设置，
-- 表现为点击参数设置跳转 /system/config 时落到通配 404。
-- 修复：给 AI 配置菜单指定独立 route_name='AiConfig'，消除同名冲突。
-- 幂等：只在 route_name 不是 AiConfig 时更新，可重复执行。
UPDATE sys_menu SET route_name = 'AiConfig'
WHERE path = 'config' AND parent_id IN (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE path='ai' AND parent_id=0) t)
  AND (route_name = '' OR route_name IS NULL OR route_name = 'Config');
