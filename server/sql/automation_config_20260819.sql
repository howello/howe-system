-- ----------------------------
-- 浏览器自动化连接参数（BrowserProperties 数据源）
-- 共 5 项，全部登记在 ConfigConstants（automation.browser.*），
-- 由 ConfigUtils 读取，在「系统管理 > 参数设置」修改即时生效，无需重启。
-- config_type='Y' 表示系统内置，界面上不可删除，避免误删导致自动化任务无法连接浏览器。
-- ----------------------------

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('自动化-浏览器连接方式', 'automation.browser.mode', 'local', 'Y', 'admin', sysdate(), '', null, 'local 在应用进程内启动本地 Chromium；remote 连接远程 Playwright WebSocket。生产部署改为 remote');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('自动化-远程浏览器地址', 'automation.browser.endpoint', '', 'Y', 'admin', sysdate(), '', null, 'Playwright server WebSocket 地址，mode=remote 时必填，形如 ws://browser:3000/');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('自动化-无头模式', 'automation.browser.headless', 'true', 'Y', 'admin', sysdate(), '', null, 'true 以 headless 模式启动本地浏览器；仅在 mode=local 时生效');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('自动化-页面操作超时', 'automation.browser.timeoutMs', '10000', 'Y', 'admin', sysdate(), '', null, '页面默认操作超时，单位毫秒');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('自动化-页面导航超时', 'automation.browser.navigationTimeoutMs', '30000', 'Y', 'admin', sysdate(), '', null, '页面导航超时，单位毫秒');