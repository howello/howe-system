-- ============================================================
-- app_version 增量脚本
--
-- 已写入：meal.sql 的「8、App 版本（wgt 资源热更新）」与「9、后台菜单」两节
--         （新库跑模块脚本即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 meal.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/app_version_20261013.sql
-- 本增量执行结束可删除。
--
-- 内容：新增 meal_app_version 表（wgt 热更新版本记录），并在「点餐管理」下挂
--       「App 版本管理」菜单与按钮权限；家庭管理员角色自动获得这些权限。
-- ============================================================

-- ----------------------------
-- 1、App 版本表
-- ----------------------------
drop table if exists meal_app_version;
create table meal_app_version (
  version_id   bigint(20)   not null auto_increment    comment '版本记录ID',
  version_name varchar(32)  not null                   comment '版本名称（展示用）',
  version_code int(11)      not null                   comment '版本序号（比较基准，整数递增）',
  wgt_url      varchar(500) not null                   comment 'wgt 资源包地址（R2）',
  wgt_size     bigint(20)   default 0                  comment '资源包字节大小',
  file_name    varchar(255) default ''                 comment '上传时的原始文件名',
  update_log   text                                    comment '更新说明',
  force_update char(1)      default '0'                comment '是否强制更新（0否 1是）',
  status       char(1)      default '0'                comment '发布状态（0已发布 1停用）',
  del_flag     char(1)      default '0'                comment '删除标记（0存在 2删除）',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime                                comment '创建时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500) default null               comment '备注',
  primary key (version_id),
  key idx_meal_app_version_code (version_code, status, del_flag)
) engine=innodb auto_increment=1 comment = '点餐-App 版本（wgt 热更新）';

-- ----------------------------
-- 2、后台菜单（不硬编码 menu_id，按名称捕获「点餐管理」目录）
-- ----------------------------
delete from sys_menu where perms like 'meal:appVersion:%';

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select 'App 版本管理', menu_id, '6', 'appversion', 'meal/appversion/index', 1, 0, 'C', '0', '0', 'meal:appVersion:list', 'download', 'admin', sysdate(), 'App 版本管理菜单（wgt 热更新）'
from sys_menu where menu_name = '点餐管理' and parent_id = 0 and menu_type = 'M' limit 1;

select @mealAppVersionMenuId := menu_id from sys_menu where perms = 'meal:appVersion:list' and menu_type = 'C' limit 1;

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('版本查询', @mealAppVersionMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'meal:appVersion:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('版本新增', @mealAppVersionMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'meal:appVersion:add', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('版本修改', @mealAppVersionMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'meal:appVersion:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('版本删除', @mealAppVersionMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'meal:appVersion:remove', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('资源包上传', @mealAppVersionMenuId, '5', '#', '', 1, 0, 'F', '0', '0', 'meal:appVersion:upload', '#', 'admin', sysdate(), '上传 wgt 资源包到 R2');

-- ----------------------------
-- 3、把新菜单授权给家庭管理员角色
-- ----------------------------
insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r
join sys_menu m on m.perms like 'meal:appVersion:%'
where r.role_key = 'meal_manager' and r.del_flag = '0'
  and not exists (
    select 1 from sys_role_menu rm where rm.role_id = r.role_id and rm.menu_id = m.menu_id
  );
