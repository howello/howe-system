-- ----------------------------
-- 友链一键新增 + waline 评论同步为友链 初始化脚本
--
-- 包含：sys_config 三条：waline url/pageSize/timeout
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
