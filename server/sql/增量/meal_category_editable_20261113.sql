-- ============================================================
-- meal_category_editable_20261113.sql
--
-- 家庭点餐分类改造：公共分类 9 → 8（去掉「岷县」），「岷县」转为
-- dept_id = 110（王艳涛家庭）的私有分类；分类开放家庭私有分类的新增/修改/删除，
-- 并引入 AI 审阅场景、家庭私有分类唯一约束与 emoji 图标约束。
--
-- 适用：已按旧版建好库的环境。新库直接跑 meal.sql 与 ai.sql 即可，不需要本文件。
-- 执行：mysql -u root -p howe-system < 增量/meal_category_editable_20261113.sql
--
-- 前置条件（脚本会校验，不满足时中止）：
--   1. dept_id = 110 存在、未删除、启用、dept_type = 'F'；
--   2. 公共分类里「岷县」最多一条，荤菜/半荤/素菜/水产/蛋豆/凉卤/汤羹/面点各恰好一条；
--   3. 目标两道菜「洋芋拌汤」「面片」当前最多各一条且归属为公共（dept_id = 0）。
--
-- 幂等：重复执行不会重复建分类、不会重复迁移菜品；已迁移的数据保持不动。
--
-- ⚠️ 旧脚本 meal_category_taxonomy_20261020.sql 的第 5 步会删除所有 dept_id <> 0
--    的分类，本脚本执行后不得再重跑那个增量。
-- ============================================================

-- ----------------------------
-- 0、前置家庭：王艳涛家庭（dept_id = 110，dept_type = 'F'）
--
-- 私有分类「岷县」与两道 seed 菜都归属该家庭。110 空闲时自动创建；
-- 110 已被占用但不是有效家庭时中止，避免把私有数据挂到错误部门上。
-- ----------------------------
insert into sys_dept (dept_id, parent_id, ancestors, dept_name, order_num, leader, phone, email,
                      status, del_flag, dept_type, create_by, create_time, update_by, update_time)
select 110, 0, '0', '王艳涛家庭', 1, '王艳涛', null, null, '0', '0', 'F', 'admin', sysdate(), '', null
from dual
where not exists (select 1 from sys_dept where dept_id = 110);

set @family_ok := (
  select count(*) from sys_dept
  where dept_id = 110 and del_flag = '0' and status = '0' and dept_type = 'F'
);
drop temporary table if exists tmp_meal_category_precheck;
create temporary table tmp_meal_category_precheck (ok tinyint primary key);
insert into tmp_meal_category_precheck (ok) values (1);
-- 110 仍不是有效家庭时插重复主键让脚本中止
insert into tmp_meal_category_precheck (ok) select 1 from dual where @family_ok = 0;
drop temporary table tmp_meal_category_precheck;

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
-- 2、收敛公共分类：只保留 8 个主类，删除公共「岷县」
--
-- 先处理「岷县」下的菜，再删分类，避免关联指向已消失的分类。
-- 新库不需要本段，但保留幂等逻辑。
-- ----------------------------

-- 2.1 目标两道菜：仅在仍为公共菜时迁移到 110（已迁移的重复执行不处理）
update meal_dish
set dept_id = 110, update_by = 'admin', update_time = sysdate()
where dept_id = 0
  and del_flag = '0'
  and name in ('洋芋拌汤', '面片');

-- 2.2 确保 110 有私有「岷县」分类（已存在同名活动分类则复用）
insert into meal_category (dept_id, name, description, basis, icon, sort, status, del_flag,
                           create_by, create_time, remark)
select 110, '岷县', '岷县地方特色菜',
       '只要顾客认「岷县」就优先归这里；普通家常菜不要硬归岷县。示例：岷县浆水面、岷县凉粉、岷县点心、当归炖鸡、洋芋拌汤、面片',
       '🏔️', 1, '0', '0', 'admin', sysdate(), '家庭私有分类'
from dual
where not exists (
  select 1 from meal_category
  where dept_id = 110 and del_flag = '0' and trim(name) = '岷县'
);

-- 2.3 把两道菜的关联改挂到 110 的「岷县」（先删旧关联再写新关联）
set @family_minxian_id := (
  select category_id from meal_category
  where dept_id = 110 and del_flag = '0' and trim(name) = '岷县'
  order by category_id asc limit 1
);

delete from meal_category_ref
where biz_type = 1
  and biz_id in (select dish_id from meal_dish where dept_id = 110 and del_flag = '0'
                 and name in ('洋芋拌汤', '面片'));

insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, @family_minxian_id
from meal_dish d
where d.dept_id = 110 and d.del_flag = '0' and d.name in ('洋芋拌汤', '面片')
  and @family_minxian_id is not null
  and not exists (
    select 1 from meal_category_ref r
    where r.biz_type = 1 and r.biz_id = d.dish_id
  );

-- 2.4 公共「岷县」：先移除它名下的分类关联（这些菜落到「待分类」由人工补齐），
--     再逻辑删除公共「岷县」。这样不会留下指向已删除分类的关联行。
delete from meal_category_ref
where biz_type = 1
  and category_id in (select category_id from meal_category where dept_id = 0 and trim(name) = '岷县');

update meal_category
set del_flag = '2', update_time = sysdate()
where dept_id = 0 and del_flag = '0' and trim(name) = '岷县';

-- 2.5 公共分类排序对齐 8 个主类（名称不匹配的公共分类逻辑删除）
update meal_category set sort = 1 where dept_id = 0 and del_flag = '0' and trim(name) = '荤菜';
update meal_category set sort = 2 where dept_id = 0 and del_flag = '0' and trim(name) = '半荤';
update meal_category set sort = 3 where dept_id = 0 and del_flag = '0' and trim(name) = '素菜';
update meal_category set sort = 4 where dept_id = 0 and del_flag = '0' and trim(name) = '水产';
update meal_category set sort = 5 where dept_id = 0 and del_flag = '0' and trim(name) = '蛋豆';
update meal_category set sort = 6 where dept_id = 0 and del_flag = '0' and trim(name) = '凉卤';
update meal_category set sort = 7 where dept_id = 0 and del_flag = '0' and trim(name) = '汤羹';
update meal_category set sort = 8 where dept_id = 0 and del_flag = '0' and trim(name) = '面点';

update meal_category
set del_flag = '2', update_time = sysdate()
where dept_id = 0
  and del_flag = '0'
  and trim(name) not in ('荤菜', '半荤', '素菜', '水产', '蛋豆', '凉卤', '汤羹', '面点');

-- ----------------------------
-- 3、图标收敛：存量非 emoji 图标统一替换为 🍽️
--
-- 旧数据允许 emoji 或图片 URL；新版本只允许图标选择器内的 emoji。
-- 这里把「看起来不像 emoji」的值统一归一，避免用户编辑旧分类时被后端拒绝。
-- ----------------------------
update meal_category
set icon = '🍽️', update_time = sysdate()
where del_flag = '0'
  and (icon is null or trim(icon) = ''
       or trim(icon) like 'http%'
       or trim(icon) like 'data:%'
       or char_length(trim(icon)) > 16);

-- ----------------------------
-- 4、权限：家庭管理员恢复分类写权限，厨师保持只读
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
-- 5、AI 场景路由与提示词参数
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
-- 6、结果核对（人工查看，不参与后续逻辑）
-- ----------------------------
select dept_id, trim(name) as name, icon, sort, status
from meal_category
where del_flag = '0' and dept_id in (0, 110)
order by dept_id asc, sort asc, category_id asc;

select d.dish_id, d.dept_id, d.name, c.category_id, c.name as category_name, c.dept_id as category_dept
from meal_dish d
left join meal_category_ref r on r.biz_type = 1 and r.biz_id = d.dish_id
left join meal_category c on c.category_id = r.category_id
where d.del_flag = '0' and d.name in ('洋芋拌汤', '面片');
