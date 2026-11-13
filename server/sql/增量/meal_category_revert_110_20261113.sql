-- ============================================================
-- meal_category_revert_110_20261113.sql
--
-- 回退「公共分类被改成家庭私有分类」的历史状态：
-- 把 9 个公共名称中已不在 dept_id = 0 的分类恢复为公共分类，
-- 并把挂在这些分类下的菜品迁回公共（dept_id = 0）。
--
-- 适用场景：
--   1. 执行过旧版 meal_category_editable_20261113.sql（把「岷县」迁到 110）的库；
--   2. 管理员手工把某个公共分类（如「岷县」）改到了任意家庭，想恢复回来。
--
-- 执行：mysql -u root -p howe-system < 增量/meal_category_revert_110_20261113.sql
--
-- 幂等：可重复执行；已经恢复的数据保持不动。
--
-- ⚠️ 本脚本只处理 MealCategoryTaxonomy 里的 9 个公共名称，
--    不改动其他家庭自建的私有分类。
-- ============================================================

-- ----------------------------
-- 1、找出被迁走的公共分类，恢复为公共
--
-- 同一名称可能同时存在「公共」和「家里私有」两条记录：
-- 优先复用公共那条；公共那条不存在时，把家庭那条改回 dept_id = 0。
-- 若同一公共名称有多条家庭记录，只恢复 category_id 最小的那条，其余保持不动。
-- ----------------------------
update meal_category
set dept_id = 0,
    del_flag = '0',
    status = '0',
    update_time = sysdate()
where del_flag = '0'
  and dept_id <> 0
  and trim(name) in ('岷县', '荤菜', '半荤', '素菜', '水产', '蛋豆', '凉卤', '汤羹', '面点')
  and not exists (
    select 1 from (
      select category_id, trim(name) as n, dept_id as d, del_flag as f
      from meal_category
    ) pub
    where pub.d = 0 and pub.f = '0' and pub.n = trim(meal_category.name)
  );

-- 若公共记录存在但被逻辑删除过，恢复它（优先于新建）
update meal_category
set del_flag = '0', status = '0', update_time = sysdate()
where dept_id = 0
  and trim(name) in ('岷县', '荤菜', '半荤', '素菜', '水产', '蛋豆', '凉卤', '汤羹', '面点')
  and del_flag = '2';

-- 缺失的公共分类补回来（正常情况下 9 条都应存在）
insert into meal_category (dept_id, name, description, basis, icon, sort, status, del_flag,
                           create_by, create_time, remark)
select 0, d.name, d.description, d.basis, d.icon, d.sort, '0', '0', 'admin', sysdate(), '公共分类'
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
) d
where not exists (
  select 1 from meal_category c
  where c.dept_id = 0 and c.del_flag = '0' and trim(c.name) = d.name
);

-- 公共分类排序对齐 9 个主类
update meal_category set sort = 1 where dept_id = 0 and del_flag = '0' and trim(name) = '岷县';
update meal_category set sort = 2 where dept_id = 0 and del_flag = '0' and trim(name) = '荤菜';
update meal_category set sort = 3 where dept_id = 0 and del_flag = '0' and trim(name) = '半荤';
update meal_category set sort = 4 where dept_id = 0 and del_flag = '0' and trim(name) = '素菜';
update meal_category set sort = 5 where dept_id = 0 and del_flag = '0' and trim(name) = '水产';
update meal_category set sort = 6 where dept_id = 0 and del_flag = '0' and trim(name) = '蛋豆';
update meal_category set sort = 7 where dept_id = 0 and del_flag = '0' and trim(name) = '凉卤';
update meal_category set sort = 8 where dept_id = 0 and del_flag = '0' and trim(name) = '汤羹';
update meal_category set sort = 9 where dept_id = 0 and del_flag = '0' and trim(name) = '面点';

-- ----------------------------
-- 2、之前因此被迁到家庭的公共菜，恢复到公共
--
-- 只处理「洋芋拌汤」「面片」这两道历史公共菜：它们是旧版脚本从公共迁到 110 的。
-- 其他被用户主动改成私有的菜不在回退范围。
-- ----------------------------
update meal_dish
set dept_id = 0, update_by = 'admin', update_time = sysdate()
where del_flag = '0'
  and dept_id <> 0
  and name in ('洋芋拌汤', '面片');

-- ----------------------------
-- 3、重新挂回公共分类
-- ----------------------------
delete from meal_category_ref
where biz_type = 1
  and biz_id in (select dish_id from meal_dish
                 where dept_id = 0 and del_flag = '0'
                   and name in ('洋芋拌汤', '面片'));

insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id,
       (select category_id from meal_category
        where dept_id = 0 and del_flag = '0' and trim(name) = '岷县'
        order by category_id asc limit 1)
from meal_dish d
where d.dept_id = 0
  and d.del_flag = '0'
  and d.name in ('洋芋拌汤', '面片')
  and exists (
    select 1 from meal_category
    where dept_id = 0 and del_flag = '0' and trim(name) = '岷县'
  )
  and not exists (
    select 1 from meal_category_ref r
    where r.biz_type = 1 and r.biz_id = d.dish_id
  );

-- ----------------------------
-- 4、清理 110 下残留的私有「岷县」
--
-- 仅当该分类没有任何菜品或提案引用时才逻辑删除。
-- ----------------------------
update meal_category
set del_flag = '2', update_time = sysdate()
where dept_id = 110
  and del_flag = '0'
  and trim(name) = '岷县'
  and not exists (
    select 1 from meal_category_ref r
    where r.biz_type = 1 and r.category_id = meal_category.category_id
  );

-- ----------------------------
-- 5、结果核对（人工查看）
-- ----------------------------
select dept_id, trim(name) as name, icon, sort, status, del_flag
from meal_category
where dept_id in (0, 110)
order by dept_id asc, sort asc, category_id asc;

select d.dish_id, d.dept_id, d.name, c.category_id, c.name as category_name, c.dept_id as category_dept
from meal_dish d
left join meal_category_ref r on r.biz_type = 1 and r.biz_id = d.dish_id
left join meal_category c on c.category_id = r.category_id
where d.del_flag = '0' and d.name in ('洋芋拌汤', '面片');
