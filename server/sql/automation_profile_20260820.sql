-- ----------------------------
-- 浏览器自动化：全局持久 profile + 定时清理
-- 相比 automation_config_20260819.sql 的 mode/local-remote 会话模型，本增量为
-- 「全局单一持久 UserDataDir profile」模型新增 profileDir 参数，并登记定时清理任务。
-- config_type='Y' 表示系统内置，界面上不可删除。
-- ----------------------------

-- 全局持久浏览器 profile 目录（UserDataDir），所有任务共用；生产需挂载到宿主机持久卷。
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('自动化-浏览器全局profile目录', 'automation.browser.profileDir', '', 'Y', 'admin', sysdate(), '', null, '全局持久浏览器 profile（UserDataDir）绝对路径，所有自动化任务共用；生产挂载宿主机持久卷，容器重建不丢登录态。为空时浏览器任务无法执行。');

-- 定时清理任务：删除整个全局 profile 目录，清空登录态，下次浏览器任务重新登录。
-- cron 用「每月 1 号 03:00」作为 ≈30 天周期的实用近似（Quartz 无法精确表达每 30 天，受月份长度限制）。
-- invoke_target 走 SpringUtils.getBean('browserProfileCleanTask')，bean 定义在 module-automation。
-- status='1' 默认停用：确认真实 profile 路径后，在「系统管理 > 定时任务」启用（清理会把所有站点登录态清空，需确认无正在依赖该登录态的任务）。
delete from sys_job where invoke_target = 'browserProfileCleanTask.clean()';

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values('浏览器全局profile定期清理', 'DEFAULT', 'browserProfileCleanTask.clean()', '0 0 3 1 * ?', '3', '1', '1', 'admin', sysdate(), '每（近似）30 天删除一次全局浏览器 profile 目录，清空登录态后重新登录；默认停用，配置好 profile 目录后在界面启用');