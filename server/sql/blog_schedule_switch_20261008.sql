-- ----------------------------
-- 博客定时任务开关（替代原 quartz sys_job 的启停控制）
--
-- module-quartz 移除后，博客的两个同步任务改由 Spring @Scheduled 承载，
-- 原 sys_job.status 的启停语义改由下面两个参数控制（系统管理 > 参数设置）：
--   blog.feed.syncEnabled  朋友圈 RSS 同步（每 2 小时）     默认 false 停用
--   blog.link.syncEnabled  waline 友链申请同步（每 30 分钟） 默认 false 停用
--
-- 默认停用与原 sys_job 一致；配置好订阅源 / waline 后改成 true 即可启用。
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < blog_schedule_switch_20261008.sql
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」。
-- ----------------------------
delete from sys_config where config_key in ('blog.feed.syncEnabled', 'blog.link.syncEnabled');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-朋友圈同步开关', 'blog.feed.syncEnabled', 'false', 'Y', 'admin', sysdate(), 'true 启用；每 2 小时抓取一次已启用的 RSS 订阅源');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-友链同步开关', 'blog.link.syncEnabled', 'false', 'Y', 'admin', sysdate(), 'true 启用；每 30 分钟同步 waline 友链申请留言');
