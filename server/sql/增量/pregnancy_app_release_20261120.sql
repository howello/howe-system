-- 旧记录继续属于点餐渠道，宝宝使用同一管理页但按渠道读取版本。
set @app_channel_ddl = if(
    exists (select 1 from information_schema.columns
            where table_schema = database() and table_name = 'meal_app_version' and column_name = 'app_key'),
    'select 1',
    'alter table meal_app_version add column app_key varchar(24) not null default ''meal'' after version_id, add index idx_app_version_channel (app_key, status, del_flag, version_code)'
);
prepare app_channel_stmt from @app_channel_ddl;
execute app_channel_stmt;
deallocate prepare app_channel_stmt;

insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark, `sensitive`)
select '宝宝 App 发布令牌', 'pregnancy.app.publishToken', '', 'N', '孕期', 'admin', sysdate(),
       '仅用于发布宝宝客户端的 WGT，留空禁用脚本发布', 'Y'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.app.publishToken');

-- 原菜单 ID 和角色绑定保留，只移动父目录和重命名权限。
select @app_tools_dir := min(menu_id) from sys_menu
where path = 'tool' and parent_id = 0 and menu_type = 'M';
select @app_version_menu := min(menu_id) from sys_menu
where menu_type = 'C' and perms in ('meal:appVersion:list', 'tool:appVersion:list');

update sys_menu
set parent_id = @app_tools_dir,
    component = 'tool/appversion/index',
    perms = 'tool:appVersion:list',
    order_num = 6,
    remark = '宝宝与吃了么的 WGT 版本管理'
where menu_id = @app_version_menu and @app_tools_dir is not null;

update sys_menu
set perms = replace(perms, 'meal:appVersion:', 'tool:appVersion:')
where parent_id = @app_version_menu and menu_type = 'F'
  and perms like 'meal:appVersion:%' and @app_tools_dir is not null;

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '管理宝宝渠道', @app_version_menu, 6, '#', '', 1, 0, 'F', '0', '0', 'tool:appVersion:pregnancy', '#', 'admin', sysdate(),
       '允许在共用版本管理中操作宝宝渠道'
where @app_version_menu is not null and @app_tools_dir is not null
  and not exists (select 1 from sys_menu where parent_id = @app_version_menu and perms = 'tool:appVersion:pregnancy');

insert ignore into sys_role_menu (role_id, menu_id)
select role_id, @app_tools_dir from sys_role_menu
where menu_id = @app_version_menu and @app_tools_dir is not null;
