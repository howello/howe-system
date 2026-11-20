-- 已写入 pregnancy.sql 的「孕期角色」与「参数种子」两节。
-- 用于现有库。旧角色成员的业务记录保留，管理员按需重新授予孕期管理员。
-- 本增量执行结束可删除。
start transaction;

insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
                      status, del_flag, create_by, create_time, remark)
select '孕期管理员', 'pregnancy_manager', 20, '1', 1, 1, '0', '0', 'admin', sysdate(),
       '孕期业务全部权限，可管理提醒规则与查看最近发送结果'
from dual
where not exists (select 1 from sys_role where role_key = 'pregnancy_manager');

-- 为内置 admin 账号补齐孕期角色。
insert into sys_user_role (user_id, role_id)
select u.user_id, r.role_id from sys_user u
join sys_role r on r.role_key = 'pregnancy_manager' and r.del_flag = '0'
where u.user_name = 'admin' and u.del_flag = '0'
  and not exists (select 1 from sys_user_role ur where ur.user_id = u.user_id and ur.role_id = r.role_id);


-- 旧角色不继承全部权限，避免把只读账号自动提升为管理员。
delete ur from sys_user_role ur join sys_role r on r.role_id = ur.role_id
where r.role_key in ('pregnancy_recorder', 'pregnancy_viewer');
delete rm from sys_role_menu rm join sys_role r on r.role_id = rm.role_id
where r.role_key in ('pregnancy_recorder', 'pregnancy_viewer');
delete rd from sys_role_dept rd join sys_role r on r.role_id = rd.role_id
where r.role_key in ('pregnancy_recorder', 'pregnancy_viewer');
update sys_role set del_flag = '2', status = '1'
where role_key in ('pregnancy_recorder', 'pregnancy_viewer');

-- 从旧预产期回推末次月经。已有末次月经时沿用该值。
insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark)
select '孕期-末次月经', 'pregnancy.lastMenstrualPeriod',
       coalesce((select case when config_value regexp '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
                                then date_format(date_sub(config_value, interval 280 day), '%Y-%m-%d')
                           end
                   from sys_config where config_key = 'pregnancy.dueDate' order by config_id limit 1), ''),
       'N', '孕期', 'admin', sysdate(), '填写末次月经第一天，格式 YYYY-MM-DD。37 周足月，40 周预产期'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.lastMenstrualPeriod');

delete from sys_config where config_key = 'pregnancy.dueDate'
  or config_key in ('pregnancy.file.maxBytes', 'pregnancy.file.maxPerOwner', 'pregnancy.file.retainHours',
     'pregnancy.export.buildWindowMinutes', 'pregnancy.export.readyHours', 'pregnancy.task.maxAttempts',
     'pregnancy.task.leaseMinutes', 'pregnancy.reminder.lateWindowMinutes', 'pregnancy.request.dedupHours');
update sys_config set param_type = '内部' where config_key = 'pregnancy.reminder.rules';

-- 移除未实现的后台入口，末次月经由系统参数页维护。
delete rm from sys_role_menu rm join sys_menu m on m.menu_id = rm.menu_id
where m.perms = 'pregnancy:settings:view' or (m.parent_id = 0 and m.path = 'pregnancy' and m.menu_name = '孕期管理');
delete from sys_menu where perms = 'pregnancy:settings:view'
  or (parent_id = 0 and path = 'pregnancy' and menu_name = '孕期管理');

commit;
