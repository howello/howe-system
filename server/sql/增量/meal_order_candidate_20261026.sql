-- ============================================================
-- 点餐订单派单 + 抢单 增量脚本
--
-- 已写入：meal.sql 的「3、订单」「3.1、订单候选厨师」「14、参数配置（派单与抢单）」
--         「10、三个家庭角色」各节（新库跑 meal.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 meal.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/meal_order_candidate_20261026.sql
-- 本增量执行结束可删除。
--
-- 内容：
--   1) meal_order 增加派单相关列与截止时间索引
--   2) 新建 meal_order_candidate 候选人表
--   3) 预置两个 sys_config 参数
--   4) 从 meal_manager 收回 meal:order:dispatch / meal:order:finish
--   5) 回填存量待接单订单的候选人与截止时间（不可省略）
--
-- 注意：本机 MySQL 为 5.7.28，不使用窗口函数等 8.0 语法。
-- ============================================================

-- 1) 订单表增加派单相关列
--    用 information_schema 判断列是否已存在，保证脚本可重复执行。
set @db := database();

set @sql := if(
  (select count(*) from information_schema.columns
   where table_schema = @db and table_name = 'meal_order' and column_name = 'assign_mode') = 0,
  'alter table meal_order add column assign_mode char(1) default ''0'' comment ''指派模式（0公开抢单 1指派中 2指派已转公开）'' after cancel_time',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := if(
  (select count(*) from information_schema.columns
   where table_schema = @db and table_name = 'meal_order' and column_name = 'assign_user_id') = 0,
  'alter table meal_order add column assign_user_id bigint(20) comment ''指派的厨师用户ID（下单时确定，不提供改派）'' after assign_mode',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := if(
  (select count(*) from information_schema.columns
   where table_schema = @db and table_name = 'meal_order' and column_name = 'assign_user_name') = 0,
  'alter table meal_order add column assign_user_name varchar(64) default '''' comment ''指派的厨师昵称（冗余快照）'' after assign_user_id',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := if(
  (select count(*) from information_schema.columns
   where table_schema = @db and table_name = 'meal_order' and column_name = 'deadline_time') = 0,
  'alter table meal_order add column deadline_time datetime comment ''接单截止时间'' after assign_user_name',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := if(
  (select count(*) from information_schema.columns
   where table_schema = @db and table_name = 'meal_order' and column_name = 'close_reason') = 0,
  'alter table meal_order add column close_reason char(1) comment ''关闭原因（1用户取消 2超时 3全员拒绝）'' after deadline_time',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := if(
  (select count(*) from information_schema.statistics
   where table_schema = @db and table_name = 'meal_order' and index_name = 'idx_meal_order_deadline') = 0,
  'alter table meal_order add key idx_meal_order_deadline (status, deadline_time)',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 2) 候选人表
create table if not exists meal_order_candidate (
  candidate_id bigint(20)  not null auto_increment comment '候选人ID',
  order_id     bigint(20)  not null                comment '订单ID',
  dept_id      bigint(20)                          comment '所属家庭ID',
  user_id      bigint(20)  not null                comment '候选厨师用户ID',
  user_name    varchar(64) default ''              comment '候选厨师昵称（冗余，免联表）',
  source       char(1)     default '2'             comment '来源（1指派 2公开池 3转公开补入）',
  status       char(1)     default '0'             comment '状态（0待响应 1已接单 2已拒绝 3已失效 4已超时未响应）',
  expire_time  datetime                            comment '响应截止时间（与订单 deadline_time 一致）',
  respond_time datetime                            comment '接单/拒绝/超时判定时间',
  create_time  datetime                            comment '入池时间',
  update_time  datetime                            comment '更新时间',
  primary key (candidate_id),
  unique key uk_meal_candidate_order_user (order_id, user_id),
  key idx_meal_candidate_user (user_id, status),
  key idx_meal_candidate_order (order_id, status)
) engine=innodb auto_increment=1 comment = '点餐-订单候选厨师';

-- 3) 两个参数
delete from sys_config where config_key = 'meal.order.acceptTimeoutMinutes';
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time,
                        remark, `sensitive`, param_type)
values ('点餐-订单接单超时分钟数', 'meal.order.acceptTimeoutMinutes', '180', 'N', 'admin', sysdate(),
        '订单进入待接单后超过该分钟数仍无人接单则关闭为「无人接单」；默认 180 分钟（3 小时）', 'N', '点餐');

delete from sys_config where config_key = 'meal.order.assignRefuseStrategy';
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time,
                        remark, `sensitive`, param_type)
values ('点餐-指派单被拒后策略', 'meal.order.assignRefuseStrategy', '1', 'N', 'admin', sysdate(),
        '指派厨师拒绝或指派超时后的处理：1=转公开池并重置截止时间（默认），2=直接关闭为无人接单', 'N', '点餐');

-- 4) 从家庭管理员收回接单与完成权限
delete from sys_role_menu
where role_id in (select role_id from sys_role where role_key = 'meal_manager')
  and menu_id in (select menu_id from sys_menu
                  where perms in ('meal:order:dispatch', 'meal:order:finish'));

-- 5) 回填存量待接单订单
--    5.1 截止时间：必须从迁移时刻起算，不能按 create_time 推算，
--        否则上线后超时任务会立刻把所有历史待接单订单关闭。
set @timeout := cast(
  coalesce((select config_value from sys_config where config_key = 'meal.order.acceptTimeoutMinutes'), '180')
  as signed);

update meal_order
set deadline_time = date_add(sysdate(), interval @timeout minute)
where status = '0' and del_flag = '0' and deadline_time is null;

--    5.2 指定模式：存量订单一律视为公开单
update meal_order set assign_mode = '0' where assign_mode is null;

--    5.3 候选人：本家庭全部启用中的 meal_chef，来源记为公开池
--        已有候选人记录的组合由唯一键挡住，脚本可重复执行。
insert ignore into meal_order_candidate
  (order_id, dept_id, user_id, user_name, source, status, expire_time, create_time, update_time)
select o.order_id, o.dept_id, u.user_id,
       case when u.nick_name is null or u.nick_name = '' then u.user_name else u.nick_name end,
       '2', '0', o.deadline_time, sysdate(), sysdate()
from meal_order o
inner join sys_user u on u.dept_id = o.dept_id and u.del_flag = '0' and u.status = '0'
inner join sys_user_role ur on ur.user_id = u.user_id
inner join sys_role r on r.role_id = ur.role_id
where o.status = '0' and o.del_flag = '0'
  and r.role_key = 'meal_chef' and r.status = '0' and r.del_flag = '0';

--    5.4 迁移后仍无候选人的待接单订单：这些订单没有任何厨师可接，
--        按「无人接单」关闭，避免一直挂在待接单列表里。
update meal_order o
set o.status = '4', o.close_reason = '3', o.update_time = sysdate()
where o.status = '0' and o.del_flag = '0'
  and not exists (select 1 from meal_order_candidate c where c.order_id = o.order_id);
