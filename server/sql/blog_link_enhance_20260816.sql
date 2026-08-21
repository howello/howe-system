-- ----------------------------
-- 友链一键新增 + waline 评论定时同步为友链 初始化脚本
--
-- 包含：
--   1. sys_config 三条：waline url/pageSize/timeout
--   2. sys_job 一条：定时任务（默认停用）
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < blog_link_enhance_20260816.sql
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」。
-- ----------------------------

-- ----------------------------
-- 1、waline 参数配置
-- ----------------------------
delete from sys_config where config_key in ('blog.waline.url', 'blog.waline.pageSize', 'blog.waline.timeout');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-waline评论端点', 'blog.waline.url', 'https://waline.wyantao.com/api/comment', 'Y', 'admin', sysdate(), 'waline 评论列表基础地址（不含查询参数）');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-waline每页条数', 'blog.waline.pageSize', '10', 'Y', 'admin', sysdate(), '单次请求拉取的评论条数');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-waline请求超时', 'blog.waline.timeout', '30000', 'Y', 'admin', sysdate(), '单位毫秒');

-- ----------------------------
-- 2、定时任务
--
-- invoke_target 带参数 sync(30)：JobInvokeUtil 会把它解析成 Integer 参数。
-- concurrent='1' 禁并发；status='1' 默认停用，配置好 waline 后在界面启用。
-- ----------------------------
delete from sys_job where invoke_target like 'blogLinkRequestTask.sync%';

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values('友链评论同步', 'DEFAULT', 'blogLinkRequestTask.sync(30)', '0 */30 * * * ?', '3', '1', '1', 'admin', sysdate(), '每30分钟拉取最近的友链申请留言；默认停用，配置好 waline 后在界面启用');
