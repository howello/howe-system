-- ============================================================
-- home_cards 增量脚本
--
-- 已写入：base.sql 的「首页工作台参数」区块
--         （新库跑 base.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 base.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/home_cards_20261016.sql
-- 本增量执行结束可删除。
--
-- 内容：预置首页工作台用到的两个参数
--       1. sys.home.moduleCacheTtl —— 模块数据表可用性探测结果缓存秒数
--       2. sys.home.cardCacheTtl   —— 点餐/分享/AI 卡片统计结果缓存秒数
-- ============================================================

delete from sys_config where config_key in ('sys.home.moduleCacheTtl', 'sys.home.cardCacheTtl');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('首页-模块探测缓存TTL', 'sys.home.moduleCacheTtl', '300', 'Y', 'admin', sysdate(), '', null, '首页模块数据表可用性探测结果 Redis 缓存秒数，默认 300（5 分钟）');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('首页-卡片统计缓存TTL', 'sys.home.cardCacheTtl', '300', 'Y', 'admin', sysdate(), '', null, '首页点餐/分享/AI 等卡片统计结果 Redis 缓存秒数，默认 300（5 分钟）');
