-- ============================================================
-- meal_dish_ai_20261009.sql — 菜品一键 AI（module-meal）
--
-- 增量脚本，独立追加，不改 base.sql 与 meal.sql：
--   1) meal_proposal 增加扩展字段（标签/耗时/难度/用料/做法/小贴士），
--      让点餐端「一键 AI」生成的全量字段有落点，审核通过时带入菜品。
--   2) 新增 AI 场景路由：
--        meal.dish.generate（CHAT）→ 优先取名为 deepseek-flash 的对话模型，否则取首个可用对话模型
--        meal.dish.cover（IMAGE）  → 取首个可用文生图模型；没有则不写入（接口按未配置降级处理）
--
-- 可重复执行（列已存在则跳过；场景路由已存在则忽略）。
-- 依赖：需先执行 base.sql、meal.sql 与 ai.sql。
-- 执行方式：mysql -u root -p howe-system < meal_dish_ai_20261009.sql
-- 执行后如服务已在运行，去「系统管理 > 参数设置」点一次「刷新缓存」。
-- ============================================================

-- ----------------------------
-- 1、meal_proposal 扩展字段
-- ----------------------------
drop procedure if exists meal_add_proposal_ext;
delimiter $$
create procedure meal_add_proposal_ext()
begin
  if not exists (select 1 from information_schema.columns
                  where table_schema = database() and table_name = 'meal_proposal' and column_name = 'tags') then
    alter table meal_proposal
      add column tags        varchar(500) default '' comment '标签，逗号分隔' after image,
      add column duration    varchar(64)  default '' comment '耗时' after tags,
      add column level       varchar(32)  default '' comment '难度' after duration,
      add column ingredients json                  comment '用料清单 [{name, amount}]' after level,
      add column steps       json                  comment '做法步骤 ["...", "..."]' after ingredients,
      add column tips        json                  comment '小贴士 ["..."]' after steps;
  end if;
end$$
delimiter ;
call meal_add_proposal_ext();
drop procedure if exists meal_add_proposal_ext;

-- ----------------------------
-- 2、AI 场景路由
-- ----------------------------
-- 对话补齐：优先 deepseek-flash，否则取首个启用的对话模型
insert ignore into ai_scene_route (scene, capability, primary_model_id, params, enabled, remark, create_by, create_time)
select 'meal.dish.generate', 'CHAT', m.id, '{"temperature":0.7}', 1, '菜品一键AI-文本补齐', 'admin', sysdate()
from ai_model m
where m.capability = 'CHAT' and m.enabled = 1
order by (m.model_name = 'deepseek-flash') desc, m.id asc
limit 1;

-- 封面图：取首个启用的文生图模型；没有可用文生图模型时不写入（接口按未配置降级）
insert ignore into ai_scene_route (scene, capability, primary_model_id, params, enabled, remark, create_by, create_time)
select 'meal.dish.cover', 'IMAGE', m.id, '{"size":"1024*1024","styleSuffixKey":"recipe.photo"}', 1, '菜品一键AI-封面图', 'admin', sysdate()
from ai_model m
where m.capability = 'IMAGE' and m.enabled = 1
order by m.id asc
limit 1;
