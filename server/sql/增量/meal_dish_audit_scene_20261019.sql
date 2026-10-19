-- ============================================================
-- meal_dish_audit_scene 增量脚本
--
-- 已写入：ai.sql 的「8. 业务场景路由种子（meal.* 一键 AI / 新菜审核）」一节
--         （新库跑 ai.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 ai.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/meal_dish_audit_scene_20261019.sql
-- 本增量执行结束可删除。
--
-- 内容：为新菜提案 AI 自动审核补一条独立的 CHAT 场景路由 meal.dish.audit。
--       1. 库中已有 meal.dish.generate 路由时，复制其主模型、降级链、参数与启用状态；
--       2. 没有该路由时，取首个启用的对话模型，参数为 {"temperature":0.7}；
--       3. 已存在 meal.dish.audit 路由时不覆盖、不重复插入（脚本幂等）。
-- ============================================================

-- ----------------------------
-- 1、优先复制对话补齐路由（主模型 / 降级链 / 参数 / 启用状态）
-- ----------------------------
insert into ai_scene_route (scene, capability, primary_model_id, fallback_model_ids, params, enabled, remark, create_by, create_time)
select 'meal.dish.audit', 'CHAT', g.primary_model_id, g.fallback_model_ids, g.params, g.enabled,
       '新菜提案AI自动审核', 'admin', sysdate()
from ai_scene_route g
left join ai_scene_route a on a.scene = 'meal.dish.audit' and a.capability = 'CHAT'
where g.scene = 'meal.dish.generate'
  and g.capability = 'CHAT'
  and a.id is null
order by g.id asc
limit 1;

-- ----------------------------
-- 2、没有对话补齐路由时，按首个启用的对话模型创建
-- ----------------------------
insert into ai_scene_route (scene, capability, primary_model_id, fallback_model_ids, params, enabled, remark, create_by, create_time)
select 'meal.dish.audit', 'CHAT', m.id, null, '{"temperature":0.7}', 1,
       '新菜提案AI自动审核', 'admin', sysdate()
from ai_model m
left join ai_scene_route a on a.scene = 'meal.dish.audit' and a.capability = 'CHAT'
where m.capability = 'CHAT'
  and m.enabled = 1
  and a.id is null
order by (m.model_name = 'deepseek-flash') desc, m.id asc
limit 1;
