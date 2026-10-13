-- ============================================================
-- publish_token 增量脚本
--
-- 已写入：meal.sql 的「13、参数配置（App 版本发布令牌）」一节
--         （新库跑模块脚本即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 meal.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/publish_token_20261013.sql
-- 本增量执行结束可删除。
--
-- 内容：预置 sys_config 参数 meal.app.publishToken（空值），供打包脚本
--       publish:wgt 通过发布接口 POST /meal/app/version/publish 发布版本。
--       真实令牌请在「系统管理 > 参数设置」里填入，不要写进仓库。
-- ============================================================

delete from sys_config where config_key = 'meal.app.publishToken';
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('点餐-App 版本发布令牌', 'meal.app.publishToken', '', 'N', 'admin', sysdate(),
        '打包脚本 publish:wgt 用：请求头 X-Publish-Token 与之匹配即可发布版本；留空=未配置（发布接口拒绝）');
