-- ============================================================
-- 家庭管理员权限收敛 增量脚本
--
-- 已写入：meal.sql 的「10、三个家庭角色」一节（新库跑 meal.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 meal.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/meal_manager_scope_20261021.sql
-- 本增量执行结束可删除。
--
-- 背景：2026-10-21 渗透事件中，攻击者先用家庭注册拿到 meal_* 角色，再借
-- 「家庭管理员」被授予的 system:user:* 进入管理端用户管理，最终自建
-- pentest_admin 角色并挂满 166 个菜单。根因是家庭角色借用了平台级权限标识。
--
-- 修复：家庭管理员不再持有 system:user:*，成员管理只走 meal:member:*。
-- meal-ui 的「家庭与成员」页功能已覆盖新增 / 改角色 / 停用 / 移除，
-- 管理端的用户管理对家庭场景是冗余入口，收回不影响任何现有功能。
-- ============================================================

-- 1) 收回家庭管理员的平台用户管理权限
delete from sys_role_menu
where role_id in (select role_id from sys_role where role_key = 'meal_manager')
  and menu_id in (
      select menu_id from sys_menu
      where perms in ('system:user:list', 'system:user:query', 'system:user:add',
                      'system:user:edit', 'system:user:remove')
  );

-- 2) 顺带收回只挂来撑侧边栏的父目录（system 目录本身）
delete from sys_role_menu
where role_id in (select role_id from sys_role where role_key = 'meal_manager')
  and menu_id in (select menu_id from sys_menu where perms = 'system:user:list' and parent_id is not null and parent_id <> 0);

-- 3) 清理历史遗留：攻击者创建的 pentest_admin 角色及其菜单关联
--    执行前先确认该角色确实无用户绑定，否则先解除绑定
--    select user_id from sys_user_role where role_id = 110;
delete from sys_role_menu where role_id = 110;
delete from sys_role_dept where role_id = 110;
delete from sys_user_role where role_id = 110;
delete from sys_role where role_id = 110 and role_key = 'pentest_admin';

-- 4) 停用攻击者此前启用的 superadmin 角色（保留记录，便于追溯）
update sys_role set status = '1', remark = concat(ifnull(remark, ''), ' [2026-10-21 渗透事件后停用]')
where role_key = 'superadmin';

-- 5) 停用 ry 演示账号（若尚未停用）
update sys_user set status = '1' where user_name = 'ry';

-- 刷新菜单缓存并让相关用户重新登录后生效
