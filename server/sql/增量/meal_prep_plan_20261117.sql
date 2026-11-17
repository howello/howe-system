-- ============================================================================
-- 一键备菜清单（meal-prep-ai-plan）
--
-- 说明：
--   1. 只新增 AI 场景路由与提示词参数，不新增任何业务表 / 字段。
--   2. 备菜清单由 AI 生成后写入 Redis（TTL 10 天），订单完成 / 取消时清理，不落库。
--   3. 可重复执行：场景路由按 scene+capability 判重，参数按 config_key 判重。
--
-- 前置：先执行 sql/base.sql，AI 场景路由表与参数表已存在。
-- 执行：mysql -u root -p howe-system < 增量/meal_prep_plan_20261117.sql
-- ============================================================================

-- ----------------------------
-- 1、AI 场景路由：一键备菜清单
--
-- 没有可用对话模型时不会插入，之后可在「AI 管理 > 场景路由」手工维护。
-- 已存在同场景路由时不覆盖，保留现场已调过的模型与参数。
-- ----------------------------
insert into ai_scene_route (scene, scene_name, capability, primary_model_id, params, enabled, remark,
                            create_by, create_time)
select 'meal.order.prep', '一键备菜清单', 'CHAT', m.id,
       '{"temperature":0.2,"maxTokens":2000}', 1, '下单后生成备菜采购清单', 'admin', sysdate()
from ai_model m
where m.capability = 'CHAT' and m.enabled = 1
  and not exists (
    select 1 from ai_scene_route r where r.scene = 'meal.order.prep' and r.capability = 'CHAT'
  )
order by (m.model_name = 'deepseek-flash') desc, m.id asc
limit 1;

-- ----------------------------
-- 2、提示词参数：一键备菜清单 system 提示词
--
-- 这里的提示词只描述角色与输出语言；「剔除调味品、合并相似食材、按采购区域分组、
-- 主菜优先、生活化数量单位、只输出 JSON」等硬性规则由代码固定追加，不随本配置改变。
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time,
                        remark, param_type)
select 'AI-一键备菜清单提示词', 'ai.prompt.meal.order.prepSystem',
       '你是一位熟悉中国菜市场的家庭采购助理。用户会给出一张家庭订单的菜品清单与每道菜的用料，请汇总成一张可以直接拿去买菜的备菜清单。所有内容使用简体中文，只输出 JSON。',
       'Y', 'admin', sysdate(), '一键备菜清单生成的 system 提示词；留空则用代码内缺省值。固定内容规则由代码追加，不随本配置改变', 'AI'
from dual
where not exists (
  select 1 from sys_config where config_key = 'ai.prompt.meal.order.prepSystem'
);

-- ----------------------------
-- 3、结果核对（人工查看，不参与后续逻辑）
-- ----------------------------
select scene, scene_name, capability, primary_model_id, params, enabled
from ai_scene_route
where scene = 'meal.order.prep';

select config_key, config_value, param_type
from sys_config
where config_key = 'ai.prompt.meal.order.prepSystem';
