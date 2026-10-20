-- ============================================================
-- meal_category_taxonomy 增量脚本（唯一主类体系迁移）
--
-- 已写入：meal.sql 的「1、菜品分类（唯一主类）」「7、菜品分类关联」「10、三个家庭角色」
--         「11、公共菜谱分类」「12、公共菜谱」关联段（新库跑 meal.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 meal.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/meal_category_taxonomy_20261020.sql
-- 本增量执行结束可删除。
--
-- 内容：
--   1. meal_category 增加 description / basis 两列（已存在则跳过）。
--   2. 写入 9 个公共主类（已存在同名则复用，不重复插入）。
--   3. 为已知的公共菜谱逐条写死唯一主类（与 meal.sql 一致）。
--   4. 其余菜品 / 提案按菜名关键词规则兜底，规则与产品的判定顺序一致；
--      仍无法判定的留空（待分类），不强行塞进某个主类。
--   5. 清理旧分类关联与旧分类记录（含家庭私有分类），只保留 9 个主类。
--   6. meal_category_ref 主键改为 (biz_type, biz_id)，同一业务最多一条分类关联。
--   7. 收回家庭角色的分类增删改权限。
--
-- 幂等：所有插入/更新都带「不存在才写」或「按名称定位」，可重复执行。
-- ============================================================

-- ----------------------------
-- 1、分类表补列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断）
-- ----------------------------
set @has_description := (
  select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'meal_category' and column_name = 'description'
);
set @sql := if(@has_description = 0,
  'alter table meal_category add column description varchar(255) default '''' comment ''分类说明（一句话定位）'' after name',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_basis := (
  select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'meal_category' and column_name = 'basis'
);
set @sql := if(@has_basis = 0,
  'alter table meal_category add column basis varchar(1000) default '''' comment ''分类依据（放什么、不放什么、示例）'' after description',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 2、写入 9 个公共主类（已存在同名则不重复插入）
-- ----------------------------
insert into meal_category (dept_id, name, description, basis, icon, sort, status, del_flag, create_by, create_time, remark)
select 0, t.name, t.description, t.basis, t.icon, t.sort, '0', '0', 'admin', sysdate(), '公共主类'
from (
  select '岷县' as name, '岷县地方特色菜' as description,
         '只要顾客认「岷县」就优先归这里；普通家常菜不要硬归岷县。示例：岷县浆水面、岷县凉粉、岷县点心、当归炖鸡、洋芋拌汤、面片' as basis,
         '🏔️' as icon, 1 as sort
  union all select '荤菜', '以肉为主、肉量明显的菜',
         '肉少菜多的不放；鱼虾蟹贝不放；汤羹不放。示例：红烧肉、回锅肉、辣子鸡、小炒黄牛肉、糖醋排骨、火锅', '🍖', 2
  union all select '半荤', '肉少菜多、肉丝肉片配大量蔬菜、肉末配菜',
         '肉为主的不放；纯素不放。示例：鱼香肉丝、肉末豆角、蚂蚁上树、芹菜肉丝、蒜苔肉丝、青椒肉丝', '🥘', 3
  union all select '素菜', '纯素菜、时蔬、菌菇、素炒',
         '含肉的不放；豆腐/蛋为主的不放。示例：清炒时蔬、手撕包菜、蒜蓉西兰花、干锅花菜、菌菇小炒', '🥬', 4
  union all select '水产', '鱼、虾、蟹、贝、蛙等水产为主料',
         '鱼香肉丝不放；水产汤归汤羹；凉拌水产归凉卤。示例：水煮鱼、酸菜鱼、红烧鱼、辣炒虾、干锅牛蛙、辣炒花甲', '🐟', 5
  union all select '蛋豆', '蛋类菜、豆腐、豆制品为主',
         '只有少量豆腐/蛋的荤菜不放；汤里的豆腐蛋不归这里。示例：麻婆豆腐、家常豆腐、番茄炒蛋、蒸蛋、香干炒肉丝', '🥚', 6
  union all select '凉卤', '凉拌菜、卤味冷盘、冷菜',
         '热炒、热炖、热汤不放。示例：拍黄瓜、口水鸡、夫妻肺片、卤味拼盘、凉拌木耳', '🥗', 7
  union all select '汤羹', '汤、羹、煲汤、带汤炖品',
         '干锅、炒菜、面点不放。示例：番茄蛋汤、酸辣汤、当归鸡汤、排骨汤、豆腐羹', '🥣', 8
  union all select '面点', '饺子、包子、饼、面条、馒头、花卷等面食，以及米饭、炒饭、粥、煲仔饭等主食',
         '含肉面点也归面点，不归荤菜。示例：水饺、煎饺、葱油饼、牛肉面、包子、花卷、黄金炒饭、排骨煲仔饭', '🍜', 9
) t
where not exists (
  select 1 from meal_category c
  where c.dept_id = 0 and c.del_flag = '0' and c.name = t.name
);

-- 9 个主类的说明与依据以本次口径为准（覆盖旧值，便于修正历史数据）
update meal_category set description = '岷县地方特色菜',
  basis = '只要顾客认「岷县」就优先归这里；普通家常菜不要硬归岷县。示例：岷县浆水面、岷县凉粉、岷县点心、当归炖鸡、洋芋拌汤、面片',
  icon = '🏔️', sort = 1, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '岷县';
update meal_category set description = '以肉为主、肉量明显的菜',
  basis = '肉少菜多的不放；鱼虾蟹贝不放；汤羹不放。示例：红烧肉、回锅肉、辣子鸡、小炒黄牛肉、糖醋排骨、火锅',
  icon = '🍖', sort = 2, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '荤菜';
update meal_category set description = '肉少菜多、肉丝肉片配大量蔬菜、肉末配菜',
  basis = '肉为主的不放；纯素不放。示例：鱼香肉丝、肉末豆角、蚂蚁上树、芹菜肉丝、蒜苔肉丝、青椒肉丝',
  icon = '🥘', sort = 3, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '半荤';
update meal_category set description = '纯素菜、时蔬、菌菇、素炒',
  basis = '含肉的不放；豆腐/蛋为主的不放。示例：清炒时蔬、手撕包菜、蒜蓉西兰花、干锅花菜、菌菇小炒',
  icon = '🥬', sort = 4, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '素菜';
update meal_category set description = '鱼、虾、蟹、贝、蛙等水产为主料',
  basis = '鱼香肉丝不放；水产汤归汤羹；凉拌水产归凉卤。示例：水煮鱼、酸菜鱼、红烧鱼、辣炒虾、干锅牛蛙、辣炒花甲',
  icon = '🐟', sort = 5, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '水产';
update meal_category set description = '蛋类菜、豆腐、豆制品为主',
  basis = '只有少量豆腐/蛋的荤菜不放；汤里的豆腐蛋不归这里。示例：麻婆豆腐、家常豆腐、番茄炒蛋、蒸蛋、香干炒肉丝',
  icon = '🥚', sort = 6, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '蛋豆';
update meal_category set description = '凉拌菜、卤味冷盘、冷菜',
  basis = '热炒、热炖、热汤不放。示例：拍黄瓜、口水鸡、夫妻肺片、卤味拼盘、凉拌木耳',
  icon = '🥗', sort = 7, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '凉卤';
update meal_category set description = '汤、羹、煲汤、带汤炖品',
  basis = '干锅、炒菜、面点不放。示例：番茄蛋汤、酸辣汤、当归鸡汤、排骨汤、豆腐羹',
  icon = '🥣', sort = 8, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '汤羹';
update meal_category set description = '饺子、包子、饼、面条、馒头、花卷等面食，以及米饭、炒饭、粥、煲仔饭等主食',
  basis = '含肉面点也归面点，不归荤菜。示例：水饺、煎饺、葱油饼、牛肉面、包子、花卷、黄金炒饭、排骨煲仔饭',
  icon = '🍜', sort = 9, status = '0', del_flag = '0', update_time = sysdate()
where dept_id = 0 and name = '面点';

-- ----------------------------
-- 3、迁移中间结果：每条业务只保留一个主类
-- ----------------------------
-- 用普通表而不是临时表：MySQL 临时表在同一语句里不能既作写入目标又被子查询读取（ERROR 1137）。
drop table if exists tmp_meal_category_pick;
create table tmp_meal_category_pick (
  biz_type tinyint not null,
  biz_id bigint not null,
  new_name varchar(64) not null,
  primary key (biz_type, biz_id)
) engine=memory;

delete from tmp_meal_category_pick;

-- 3.1 已知公共菜谱逐条写死（与 meal.sql 完全一致，优先级最高）
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, t.new_name
from meal_dish d
join (
            select '洋芋拌汤' as dish_name, '岷县' as new_name
  union all select '面片', '岷县'
  union all select '锅包肉', '荤菜'
  union all select '小炒黄牛肉', '荤菜'
  union all select '板栗鸡', '荤菜'
  union all select '牛排', '荤菜'
  union all select '红烧肉', '荤菜'
  union all select '煮猪蹄', '荤菜'
  union all select '火锅', '荤菜'
  union all select '回锅肉', '荤菜'
  union all select '梅菜扣肉', '荤菜'
  union all select '黄焖鸡', '荤菜'
  union all select '可乐鸡翅', '荤菜'
  union all select '大盘鸡', '荤菜'
  union all select '莴笋炒肉片', '半荤'
  union all select '青椒肉丝', '半荤'
  union all select '蒜苔炒肉', '半荤'
  union all select '酸辣白菜', '素菜'
  union all select '红烧茄子', '素菜'
  union all select '炒洋芋片', '素菜'
  union all select '炒洋芋条', '素菜'
  union all select '淋汁上海青', '素菜'
  union all select '清炒油麦菜', '素菜'
  union all select '酸辣土豆丝', '素菜'
  union all select '清蒸鲈鱼', '水产'
  union all select '玉米炒虾仁', '水产'
  union all select '辣炒花甲', '水产'
  union all select '青椒炒鸡蛋', '蛋豆'
  union all select '西红柿炒鸡蛋', '蛋豆'
  union all select '韭菜炒鸡蛋', '蛋豆'
  union all select '苦瓜炒蛋', '蛋豆'
  union all select '西葫芦炒蛋', '蛋豆'
  union all select '香干炒肉丝', '蛋豆'
  union all select '凉拌黄瓜', '凉卤'
  union all select '西红柿鸡蛋汤', '汤羹'
  union all select '紫菜蛋花汤', '汤羹'
  union all select '排骨煲仔饭', '面点'
  union all select '猪肉大葱饺子', '面点'
  union all select '洋芋饺子', '面点'
  union all select '牛肉饺子', '面点'
  union all select '绿辣子夹馍', '面点'
  union all select '黄金炒饭', '面点'
) t on t.dish_name = d.name
where d.del_flag = '0';

-- 3.2 其余菜品按菜名关键词兜底，顺序与固定判定顺序一致：
--     岷县 → 面点（面食/主食，含酸汤水饺等带汤面食）→ 汤羹 → 凉卤 → 水产 → 蛋豆 → 半荤 → 荤菜 → 素菜
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '岷县'
from meal_dish d
where d.del_flag = '0' and d.name like '%岷县%'
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '面点'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%饺%' or d.name like '%包子%' or d.name like '%面包%' or d.name like '%饼%'
       or d.name like '%面条%' or d.name like '%馒头%' or d.name like '%花卷%' or d.name like '%馍%'
       or d.name like '%炒饭%' or d.name like '%粥%' or d.name like '%煲仔饭%'
       or d.name like '%馄饨%' or d.name like '%烧麦%')
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '汤羹'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%汤%' or d.name like '%羹%')
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '凉卤'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%凉拌%' or d.name like '%卤%' or d.name like '%口水鸡%'
       or d.name like '%夫妻肺片%' or d.name like '%拍黄瓜%' or d.name like '%白切鸡%')
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '水产'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%鱼%' or d.name like '%虾%' or d.name like '%蟹%' or d.name like '%贝%'
       or d.name like '%蛙%' or d.name like '%花甲%')
  and d.name not like '%鱼香%'
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '蛋豆'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%蛋%' or d.name like '%豆腐%' or d.name like '%香干%' or d.name like '%豆干%'
       or d.name like '%腐竹%' or d.name like '%千张%')
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '半荤'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%肉丝%' or d.name like '%肉片%' or d.name like '%肉末%')
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '荤菜'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%肉%' or d.name like '%鸡%' or d.name like '%牛%' or d.name like '%羊%'
       or d.name like '%排骨%' or d.name like '%猪蹄%' or d.name like '%鸭%')
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 1, d.dish_id, '素菜'
from meal_dish d
where d.del_flag = '0'
  and (d.name like '%菜%' or d.name like '%瓜%' or d.name like '%茄%' or d.name like '%土豆%'
       or d.name like '%洋芋%' or d.name like '%豆腐%' or d.name like '%菌%' or d.name like '%菇%')
  and not exists (select 1 from tmp_meal_category_pick p where p.biz_type = 1 and p.biz_id = d.dish_id);

-- 3.3 提案按同一套菜名规则兜底
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '岷县'
from meal_proposal p
where p.del_flag = '0' and p.name like '%岷县%'
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '面点'
from meal_proposal p
where p.del_flag = '0'
  and (p.name like '%饺%' or p.name like '%包子%' or p.name like '%面包%' or p.name like '%饼%'
       or p.name like '%面条%' or p.name like '%馒头%' or p.name like '%花卷%' or p.name like '%馍%'
       or p.name like '%炒饭%' or p.name like '%粥%' or p.name like '%煲仔饭%'
       or p.name like '%馄饨%' or p.name like '%烧麦%')
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '汤羹'
from meal_proposal p
where p.del_flag = '0' and (p.name like '%汤%' or p.name like '%羹%')
  -- 面食/主食为主体的带汤菜归面点
  and not (p.name like '%饺%' or p.name like '%包子%' or p.name like '%饼%' or p.name like '%面条%'
           or p.name like '%面片%' or p.name like '%馒头%' or p.name like '%花卷%'
           or p.name like '%炒饭%' or p.name like '%粥%' or p.name like '%煲仔饭%')
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '凉卤'
from meal_proposal p
where p.del_flag = '0' and (p.name like '%凉拌%' or p.name like '%卤%')
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '水产'
from meal_proposal p
where p.del_flag = '0'
  and (p.name like '%鱼%' or p.name like '%虾%' or p.name like '%蟹%' or p.name like '%蛙%')
  and p.name not like '%鱼香%'
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '蛋豆'
from meal_proposal p
where p.del_flag = '0'
  and (p.name like '%蛋%' or p.name like '%豆腐%' or p.name like '%香干%')
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '半荤'
from meal_proposal p
where p.del_flag = '0'
  and (p.name like '%肉丝%' or p.name like '%肉片%' or p.name like '%肉末%')
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '荤菜'
from meal_proposal p
where p.del_flag = '0'
  and (p.name like '%肉%' or p.name like '%鸡%' or p.name like '%牛%' or p.name like '%羊%')
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select 2, p.proposal_id, '素菜'
from meal_proposal p
where p.del_flag = '0'
  and (p.name like '%菜%' or p.name like '%瓜%' or p.name like '%茄%' or p.name like '%土豆%'
       or p.name like '%洋芋%' or p.name like '%菌%' or p.name like '%菇%')
  and not exists (select 1 from tmp_meal_category_pick t where t.biz_type = 2 and t.biz_id = p.proposal_id);

-- 3.4 仍无法判定的，用旧分类名映射兜底（旧做法类分类不带主料信息，已在映射表中排除）
--     为保证结果确定，按「映射优先顺序」取第一条；同一业务命中多个时取最小序号。
drop table if exists tmp_meal_category_legacy;
create table tmp_meal_category_legacy (
  old_name varchar(64) not null,
  new_name varchar(64) not null,
  priority int not null,
  primary key (old_name)
) engine=memory;

delete from tmp_meal_category_legacy;
insert into tmp_meal_category_legacy (old_name, new_name, priority) values
('岷县','岷县',1),
('汤','汤羹',2), ('汤羹','汤羹',2),
('凉拌','凉卤',3), ('卤','凉卤',3), ('凉卤','凉卤',3),
('面点','面点',4),
('鱼','水产',5), ('水产','水产',5),
('蛋','蛋豆',6), ('豆制品','蛋豆',6), ('蛋豆','蛋豆',6),
('荤菜','荤菜',7), ('肉','荤菜',7),
('半荤半素','半荤',8), ('半荤','半荤',8),
('素菜','素菜',9), ('蔬菜','素菜',9);

-- 按 priority 从 1 到 9 逐轮插入，每轮跳过已有结果，等价于「取优先级最高的一条」。
-- 不用 row_number()：本项目 MySQL 是 5.7，不支持窗口函数。
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 1
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 2
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 3
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 4
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 5
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 6
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 7
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 8
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;
insert into tmp_meal_category_pick (biz_type, biz_id, new_name)
select r.biz_type, r.biz_id, l.new_name
from meal_category_ref r
join meal_category c on c.category_id = r.category_id
join tmp_meal_category_legacy l on l.old_name = c.name and l.priority = 9
where not exists (
  select 1 from tmp_meal_category_pick p where p.biz_type = r.biz_type and p.biz_id = r.biz_id
)
group by r.biz_type, r.biz_id, l.new_name;

-- ----------------------------
-- 4、重建关联：清空旧关联，按唯一主类重新写入
-- ----------------------------
delete from meal_category_ref;

insert into meal_category_ref (biz_type, biz_id, category_id)
select p.biz_type, p.biz_id, c.category_id
from tmp_meal_category_pick p
join meal_category c on c.dept_id = 0 and c.del_flag = '0' and c.name = p.new_name;

-- ----------------------------
-- 5、清理旧分类：9 个主类之外全部逻辑删除（含家庭私有分类）
-- ----------------------------
update meal_category
set del_flag = '2', update_time = sysdate()
where del_flag = '0'
  and (dept_id <> 0 or name not in ('岷县','荤菜','半荤','素菜','水产','蛋豆','凉卤','汤羹','面点'));

-- ----------------------------
-- 6、关联表主键收敛为 (biz_type, biz_id)
-- ----------------------------
set @old_pk := (
  select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'meal_category_ref'
    and index_name = 'PRIMARY' and column_name = 'category_id'
);
set @sql := if(@old_pk > 0,
  'alter table meal_category_ref drop primary key, add primary key (biz_type, biz_id)',
  'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

drop table if exists tmp_meal_category_pick;
drop table if exists tmp_meal_category_legacy;

-- ----------------------------
-- 7、收回家庭角色的分类增删改权限
-- ----------------------------
delete from sys_role_menu
where menu_id in (
  select menu_id from (
    select menu_id from sys_menu
    where perms in ('meal:category:add', 'meal:category:edit', 'meal:category:remove')
  ) t
)
and role_id in (select role_id from sys_role where role_key in ('meal_chef', 'meal_manager'));
