-- ============================================================
-- family_member_20261013.sql — 家庭成员管理与家庭代码注册 增量脚本
--
-- 内容：
--   1、给「家庭管理员」角色授予管理端「系统管理 > 用户管理」的菜单与按钮，
--      使其能在 admin-ui 里增删改本家庭成员。
--   2、手机端「家庭与成员」接口（/meal/member/*）用角色校验
--      （@ss.hasRole('meal_manager')）放行，不依赖菜单权限，因此无需额外 SQL。
--   3、家庭代码直接使用 sys_dept.dept_id，不新增列、不新增表，因此无需 DDL。
--
-- 数据范围：meal_manager 的 data_scope 仍是 '3'（本部门），用户列表只会返回
--           本家庭成员；归属部门固定为登录用户自己的家庭。
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < family_member_20261013.sql
-- 依赖：需先执行 base.sql 与 meal.sql（meal.sql 里才有 meal_manager 角色）。
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」，
-- 并让家庭管理员账号重新登录，权限才会生效。
-- ============================================================

-- ----------------------------
-- 1、清理旧授权，保证脚本可重复执行
--    只清理用户管理相关的菜单/按钮，不动家庭管理员既有的 meal:* 授权。
-- ----------------------------
delete from sys_role_menu
where role_id in (select role_id from sys_role where role_key = 'meal_manager')
  and menu_id in (select menu_id from sys_menu where perms like 'system:user:%');

-- ----------------------------
-- 2、授予用户管理的菜单与按钮
--    菜单：system:user:list（用户管理菜单本身）
--    按钮：system:user:query / add / edit / remove
--    不授予 export / import / resetPwd，家庭场景用不到。
-- ----------------------------
insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r
join sys_menu m on m.perms in ('system:user:list', 'system:user:query', 'system:user:add', 'system:user:edit', 'system:user:remove')
where r.role_key = 'meal_manager'
  and not exists (select 1 from sys_role_menu rm where rm.role_id = r.role_id and rm.menu_id = m.menu_id);

-- ----------------------------
-- 3、补授父级目录（系统管理）
--    只给子菜单不挂父目录，侧边栏不会渲染出该菜单，因此这里按用户管理菜单的
--    parent_id 反查父目录，避免硬编码菜单ID。
-- ----------------------------
insert into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.parent_id
from sys_role r
join sys_menu m on m.perms = 'system:user:list'
where r.role_key = 'meal_manager'
  and m.parent_id is not null
  and m.parent_id <> 0
  and not exists (select 1 from sys_role_menu rm where rm.role_id = r.role_id and rm.menu_id = m.parent_id);

-- ----------------------------
-- 4、校验（可选）
--    执行后可用下面的语句确认家庭管理员已拿到用户管理相关菜单。
-- ----------------------------
-- select m.menu_id, m.menu_name, m.perms
-- from sys_role r
-- join sys_role_menu rm on rm.role_id = r.role_id
-- join sys_menu m on m.menu_id = rm.menu_id
-- where r.role_key = 'meal_manager'
-- order by m.menu_id;
