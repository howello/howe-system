-- ----------------------------
-- 移除「定时任务(module-quartz)」与「代码生成(module-generator)」两个模块的数据库对象
--
-- 背景：后端已删除 module-quartz 与 module-generator，前端页面与菜单同步下线，
--       本脚本用于在【已存在的库】上清除这两块残留的表、菜单、按钮与字典。
--
-- 内容：
--   1. 删除定时任务相关表：sys_job、sys_job_log，以及 quartz 的 QRTZ_* 表
--   2. 删除代码生成相关表：gen_table、gen_table_column
--   3. 删除菜单：定时任务(110)、代码生成(116) 及其按钮(1049~1060)，并清理角色菜单关联
--   4. 删除字典：sys_job_status、sys_job_group
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < remove_quartz_generator_20261008.sql
--
-- 执行后需重启后端服务；已登录用户请重新登录，使菜单/权限缓存刷新。
-- ----------------------------

-- ----------------------------
-- 1、定时任务相关表
-- ----------------------------
DROP TABLE IF EXISTS sys_job;
DROP TABLE IF EXISTS sys_job_log;

-- quartz 持久化表（先删子表再删父表，避免外键约束报错）
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

-- ----------------------------
-- 2、代码生成相关表
-- ----------------------------
DROP TABLE IF EXISTS gen_table;
DROP TABLE IF EXISTS gen_table_column;

-- ----------------------------
-- 3、菜单与角色菜单关联
--   菜单：110 定时任务、116 代码生成
--   按钮：1049~1054 定时任务按钮、1055~1060 代码生成按钮
-- ----------------------------
DELETE FROM sys_role_menu WHERE menu_id IN
  (110, 116, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1057, 1058, 1059, 1060);

DELETE FROM sys_menu WHERE menu_id IN
  (110, 116, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1057, 1058, 1059, 1060);

-- ----------------------------
-- 4、字典类型与字典数据
-- ----------------------------
DELETE FROM sys_dict_data WHERE dict_type IN ('sys_job_status', 'sys_job_group');
DELETE FROM sys_dict_type WHERE dict_type IN ('sys_job_status', 'sys_job_group');

commit;
