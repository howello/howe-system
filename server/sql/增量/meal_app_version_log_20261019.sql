-- ============================================================
-- App 发版日志 AI 润色 增量脚本
--
-- 已写入：ai.sql 的「8. 业务场景路由种子」与「9. AI 提示词参数」两节
--         （新库跑 ai.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 ai.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/meal_app_version_log_20261019.sql
-- 本增量执行结束可删除。
--
-- 内容：
--   1. 新增 CHAT 场景路由 meal.app.version.log（CI 发版日志润色）；
--   2. 新增提示词参数 ai.prompt.meal.app.versionLog。
-- 脚本幂等：已存在的路由/参数不覆盖、不重复插入。
-- ============================================================

-- ----------------------------
-- 1、场景路由：优先复制菜品 AI 更新路由的模型与参数，否则取首个启用的对话模型
-- ----------------------------
insert into ai_scene_route (scene, capability, primary_model_id, fallback_model_ids, params, enabled, remark, create_by, create_time)
select 'meal.app.version.log', 'CHAT', u.primary_model_id, u.fallback_model_ids,
       coalesce(u.params, '{"temperature":0.5}'), 1, 'App发版日志AI润色', 'admin', sysdate()
from ai_scene_route u
left join ai_scene_route a on a.scene = 'meal.app.version.log' and a.capability = 'CHAT'
where u.scene = 'meal.dish.update'
  and u.capability = 'CHAT'
  and a.id is null
order by u.id asc
limit 1;

insert into ai_scene_route (scene, capability, primary_model_id, params, enabled, remark, create_by, create_time)
select 'meal.app.version.log', 'CHAT', m.id, '{"temperature":0.5}', 1, 'App发版日志AI润色', 'admin', sysdate()
from ai_model m
left join ai_scene_route a on a.scene = 'meal.app.version.log' and a.capability = 'CHAT'
where m.capability = 'CHAT'
  and m.enabled = 1
  and a.id is null
order by (m.model_name = 'deepseek-flash') desc, m.id asc
limit 1;

-- ----------------------------
-- 2、提示词参数（已存在时不覆盖）
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'AI-App发版日志润色提示词', 'ai.prompt.meal.app.versionLog',
       '你是家庭点餐 App 的版本公告编辑。用户会把本次发版的代码提交标题交给你，请把它们改写成家庭成员能看懂的更新说明。要求：只使用给定提交里明确提到的内容，不编造、不夸大、不写技术术语（如 commit、分支、重构、依赖）；每条一行，最多 3 行，整段不超过 100 个汉字；不使用序号、Markdown、emoji；语气自然平实，例如「新增菜品编辑页，可以直接修改做法」「修复下架的菜仍显示在点餐区的问题」。',
       'Y', 'admin', sysdate(), 'CI 发版前「日志润色」使用的 system 提示词；留空则用代码内缺省值'
from dual
where not exists (select 1 from sys_config where config_key = 'ai.prompt.meal.app.versionLog');
