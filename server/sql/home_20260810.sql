-- 首页综合工作台参数（admin-ui 首页统计）
-- config_type='Y' 表示系统内置，界面上不可删除，避免误删导致统计缓存失效。
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('首页-统计缓存TTL', 'sys.home.statsCacheTtl', '600', 'Y', 'admin', sysdate(), '', null, '首页博客统计聚合结果 Redis 缓存秒数，默认 600（10 分钟）；参数页修改即时生效，无需重启');

-- 注意事项：
-- 1) 键名必须与 ConfigConstants.HOME_STATS_CACHE_TTL 完全一致，不能写岔。
-- 2) 执行后如服务已在运行，去「系统管理 > 参数设置」点一次「刷新缓存」让参数落 Redis。