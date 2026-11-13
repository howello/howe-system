-- ============================================================
-- meal_category_editable_20261113.sql
--
-- 家庭点餐分类能力升级：分类表增加活动名称唯一约束、家庭私有分类开放
-- 新增/修改/删除（含 AI 审阅）、图标收敛为 emoji、补充分类写权限与
-- AI 场景路由/提示词参数。
--
-- 适用：已按旧版建好库的环境。新库直接跑 meal.sql 与 ai.sql 即可，不需要本文件。
-- 执行：mysql -u root -p howe-system < 增量/meal_category_editable_20261113.sql
--
-- ⚠️ 本脚本**不迁移、不删除、不新增任何分类数据**，也不改动菜品归属。
--    公共分类与已有私有分类保持原样，只补表结构、约束、权限与 AI 配置。
--
-- 幂等：所有 DDL 与 DML 都带存在性判断，可重复执行。
-- ============================================================

-- ----------------------------
-- 1、分类表补齐约束
--
-- active_name 生成列：逻辑删除后为 NULL，配合 (dept_id, active_name) 唯一索引，
-- 既保证同一归属内不重名，又允许分类删除后复用名称。
-- ----------------------------
set @has_active_name := (
  select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'meal_category' and column_name = 'active_name'
);
set @sql := if(@has_active_name = 0,
  'alter table meal_category add column active_name varchar(64) generated always as (if(del_flag = ''0'', trim(name), null)) stored comment ''活动分类名称（逻辑删除后为 NULL，用于唯一约束）''',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 存量重名会让唯一索引创建失败，先列出（人工处理后再加索引）
select dept_id, trim(name) as name, count(*) as cnt
from meal_category
where del_flag = '0'
group by dept_id, trim(name)
having count(*) > 1;

set @has_uk := (
  select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'meal_category'
    and index_name = 'uk_meal_category_active_name'
);
set @sql := if(@has_uk = 0,
  'alter table meal_category add unique key uk_meal_category_active_name (dept_id, active_name)',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 2、图标收敛：存量非 emoji 图标统一替换为 🍽️
--
-- 旧数据允许 emoji 或图片 URL；新版本只允许图标选择器内的 emoji。
-- 这里把「看起来不像 emoji」的值统一归一，避免用户编辑旧分类时被后端拒绝。
-- 只改图标列，不动名称、说明、依据与归属。
-- ----------------------------
update meal_category
set icon = '🍽️', update_time = sysdate()
where del_flag = '0'
  and (icon is null or trim(icon) = ''
       or trim(icon) like 'http%'
       or trim(icon) like 'data:%'
       or char_length(trim(icon)) > 16);

-- ----------------------------
-- 3、权限：家庭管理员恢复分类写权限，厨师保持只读
-- ----------------------------
insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r
join sys_menu m on m.perms in ('meal:category:add', 'meal:category:edit', 'meal:category:remove')
where r.role_key = 'meal_manager'
  and not exists (
    select 1 from sys_role_menu rm where rm.role_id = r.role_id and rm.menu_id = m.menu_id
  );

delete from sys_role_menu
where role_id in (select role_id from sys_role where role_key = 'meal_chef')
  and menu_id in (
    select menu_id from (
      select menu_id from sys_menu
      where perms in ('meal:category:add', 'meal:category:edit', 'meal:category:remove')
    ) t
  );

-- ----------------------------
-- 4、AI 场景路由与提示词参数
--
-- 已存在同场景路由时不覆盖（保留现场已调过的模型与参数）。
-- ----------------------------
insert into ai_scene_route (scene, scene_name, capability, primary_model_id, params, enabled, remark,
                            create_by, create_time)
select 'meal.category.review', '点餐分类审阅与润色', 'CHAT', m.id,
       '{"temperature":0.2,"maxTokens":900,"timeoutMs":8000}', 1, '点餐分类AI审阅', 'admin', sysdate()
from ai_model m
where m.capability = 'CHAT' and m.enabled = 1
  and not exists (
    select 1 from ai_scene_route r where r.scene = 'meal.category.review' and r.capability = 'CHAT'
  )
order by (m.model_name = 'deepseek-flash') desc, m.id asc
limit 1;

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time,
                        remark, param_type)
select 'AI-点餐分类审阅提示词', 'ai.prompt.meal.category.reviewSystem',
       '你是家庭点餐分类编辑。请审阅用户提交的分类名称、分类说明和分类依据，并在合格时做克制的中文润色。分类用于帮助家庭成员判断一道菜应放在哪里。说明用一句话说明定位；依据清楚写出「放什么、不放什么、示例」。保持用户表达的地域、食材和范围，不添加用户未给出的菜名、菜品、地域事实或排除条件。只返回符合指定 JSON Schema 的对象，不要输出 Markdown。',
       'Y', 'admin', sysdate(), '点餐分类新增/修改时的 AI 审阅提示词；留空则用代码内缺省值。固定规则由代码追加，不随本配置改变', 'AI'
from dual
where not exists (
  select 1 from sys_config where config_key = 'ai.prompt.meal.category.reviewSystem'
);

-- ----------------------------
-- 5、结果核对（人工查看，不参与后续逻辑）
-- ----------------------------
select dept_id, trim(name) as name, icon, sort, status
from meal_category
where del_flag = '0'
order by dept_id asc, sort asc, category_id asc;
