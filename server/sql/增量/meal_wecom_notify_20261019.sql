-- ============================================================
-- meal_wecom_notify 增量脚本
--
-- 已写入：meal.sql 的「8.1、企业微信通知配置」与「9、后台菜单」
--         （新库跑 meal.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 meal.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/meal_wecom_notify_20261019.sql
-- 本增量执行结束可删除。
--
-- 内容：
--   1. 新建 meal_wecom_config 表（每家庭一条企业微信群机器人配置）。
--   2. 在「点餐管理」下新增「通知管理」菜单与 5 个按钮权限。
--   3. 给家庭管理员角色授予该菜单与按钮；点餐员、厨师不授予。
--   4. 脚本幂等：表已存在时不重建，菜单已存在时不重复插入。
-- ============================================================

-- ----------------------------
-- 1、企业微信通知配置表
-- ----------------------------
create table if not exists meal_wecom_config (
  config_id    bigint(20)   not null auto_increment    comment '配置ID',
  dept_id      bigint(20)   not null                   comment '所属家庭ID',
  webhook_key  varchar(128) default ''                 comment '企业微信群机器人 key（凭证，接口返回掩码）',
  enabled      char(1)      default '0'                comment '家庭级总开关（0停用 1启用）',
  notify_order char(1)      default '1'                comment '订单通知开关（0停用 1启用）',
  notify_ai    char(1)      default '1'                comment 'AI 通知开关（0停用 1启用）',
  del_flag     char(1)      default '0'                comment '删除标记（0存在 2删除）',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime                                comment '创建时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500) default null               comment '备注',
  primary key (config_id),
  unique key uk_meal_wecom_config_dept (dept_id)
) engine=innodb auto_increment=1 comment = '点餐-企业微信通知配置';

-- 已有库若先执行过旧版建表（普通索引），补上家庭唯一约束；已存在唯一索引时跳过
set @ukExists := (select count(1) from information_schema.statistics
                  where table_schema = database()
                    and table_name = 'meal_wecom_config'
                    and index_name = 'uk_meal_wecom_config_dept');
set @idxExists := (select count(1) from information_schema.statistics
                   where table_schema = database()
                     and table_name = 'meal_wecom_config'
                     and index_name = 'idx_meal_wecom_config_dept');
set @ddl := if(@ukExists = 0 and @idxExists > 0,
               'alter table meal_wecom_config drop index idx_meal_wecom_config_dept',
               'select 1');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;
set @ddl := if(@ukExists = 0,
               'alter table meal_wecom_config add unique key uk_meal_wecom_config_dept (dept_id)',
               'select 1');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 2、菜单：点餐管理 > 通知管理
-- ----------------------------
set @mealDirId := (select menu_id from sys_menu
                   where menu_name = '点餐管理' and parent_id = 0 and menu_type = 'M'
                   order by menu_id asc limit 1);

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '通知管理', @mealDirId, '7', 'wecom', 'meal/wecom/index', 1, 0, 'C', '0', '0', 'meal:wecom:list', 'message', 'admin', sysdate(), '企业微信通知配置菜单'
from dual
where @mealDirId is not null
  and not exists (select 1 from sys_menu where perms = 'meal:wecom:list');

set @mealWeComMenuId := (select menu_id from sys_menu where perms = 'meal:wecom:list' order by menu_id asc limit 1);

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '通知查询', @mealWeComMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'meal:wecom:query', '#', 'admin', sysdate(), ''
from dual
where @mealWeComMenuId is not null
  and not exists (select 1 from sys_menu where perms = 'meal:wecom:query');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '通知新增', @mealWeComMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'meal:wecom:add', '#', 'admin', sysdate(), ''
from dual
where @mealWeComMenuId is not null
  and not exists (select 1 from sys_menu where perms = 'meal:wecom:add');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '通知修改', @mealWeComMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'meal:wecom:edit', '#', 'admin', sysdate(), ''
from dual
where @mealWeComMenuId is not null
  and not exists (select 1 from sys_menu where perms = 'meal:wecom:edit');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '通知删除', @mealWeComMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'meal:wecom:remove', '#', 'admin', sysdate(), ''
from dual
where @mealWeComMenuId is not null
  and not exists (select 1 from sys_menu where perms = 'meal:wecom:remove');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '通知测试', @mealWeComMenuId, '5', '#', '', 1, 0, 'F', '0', '0', 'meal:wecom:test', '#', 'admin', sysdate(), ''
from dual
where @mealWeComMenuId is not null
  and not exists (select 1 from sys_menu where perms = 'meal:wecom:test');

-- ----------------------------
-- 3、家庭管理员授权
-- ----------------------------
set @managerRoleId := (select role_id from sys_role
                       where role_key = 'meal_manager' and del_flag = '0'
                       order by role_id asc limit 1);

insert into sys_role_menu (role_id, menu_id)
select @managerRoleId, menu_id from sys_menu
where perms like 'meal:wecom:%'
  and @managerRoleId is not null
  and not exists (select 1 from sys_role_menu rm
                  where rm.role_id = @managerRoleId and rm.menu_id = sys_menu.menu_id);
