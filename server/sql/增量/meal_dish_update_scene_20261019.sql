-- ============================================================
-- meal_dish_update_scene 增量脚本
--
-- 已写入：ai.sql 的「8. 业务场景路由种子（meal.* 一键 AI / 新菜审核）」与「9. 参数配置」
--         （新库跑 ai.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 ai.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/meal_dish_update_scene_20261019.sql
-- 本增量执行结束可删除。
--
-- 内容：
--   1. 为「菜品 AI 更新」补一条独立的 CHAT 场景路由 meal.dish.update；
--      库中已有 meal.dish.generate 路由时复制其主模型、降级链与启用状态，
--      参数固定为 {"temperature":0.5}；没有该路由时取首个启用的对话模型。
--   2. 补 AI 更新提示词参数 ai.prompt.meal.dish.updateSystem。
--   3. 已存在同场景路由或同名参数时不覆盖、不重复插入（脚本幂等）。
-- ============================================================

-- ----------------------------
-- 1、优先复制对话补齐路由（主模型 / 降级链 / 启用状态）
-- ----------------------------
insert into ai_scene_route (scene, capability, primary_model_id, fallback_model_ids, params, enabled, remark, create_by, create_time)
select 'meal.dish.update', 'CHAT', g.primary_model_id, g.fallback_model_ids, '{"temperature":0.5}', g.enabled,
       '菜品AI更新-做法提取', 'admin', sysdate()
from ai_scene_route g
left join ai_scene_route u on u.scene = 'meal.dish.update' and u.capability = 'CHAT'
where g.scene = 'meal.dish.generate'
  and g.capability = 'CHAT'
  and u.id is null
order by g.id asc
limit 1;

-- ----------------------------
-- 2、没有对话补齐路由时，按首个启用的对话模型创建
-- ----------------------------
insert into ai_scene_route (scene, capability, primary_model_id, fallback_model_ids, params, enabled, remark, create_by, create_time)
select 'meal.dish.update', 'CHAT', m.id, null, '{"temperature":0.5}', 1,
       '菜品AI更新-做法提取', 'admin', sysdate()
from ai_model m
left join ai_scene_route u on u.scene = 'meal.dish.update' and u.capability = 'CHAT'
where m.capability = 'CHAT'
  and m.enabled = 1
  and u.id is null
order by (m.model_name = 'deepseek-flash') desc, m.id asc
limit 1;

-- ----------------------------
-- 3、补 AI 更新提示词参数（已存在则不覆盖）
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'AI-菜品更新提示词', 'ai.prompt.meal.dish.updateSystem',
       '你是一位资深中餐菜谱编辑。用户会给出一道菜的完整做法，请从中提取结构化菜品信息。所有内容使用简体中文：简介精炼（不超过 60 字）；标签给出 3 到 6 个短词；耗时、难度、份量、热量按文本合理推断；用料提取名称与用量；做法按步骤拆分并保留原文关键信息；小贴士提取注意事项。文本中确实未提到的字段返回空字符串或空数组，不要编造。',
       'Y', 'admin', sysdate(), '菜品「AI 更新」使用的 system 提示词；留空则用代码内缺省值'
from dual
where not exists (
  select 1 from sys_config where config_key = 'ai.prompt.meal.dish.updateSystem'
);
