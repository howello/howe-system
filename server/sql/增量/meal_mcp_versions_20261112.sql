-- ============================================================
-- 点餐 MCP 写工具所需的数据版本与候选轮次（C4）
--
-- 适用：已安装 module-meal 的存量库；新库仍需按增量顺序执行本脚本。
-- 执行：mysql -u root -p howe-system < 增量/meal_mcp_versions_20261112.sql
-- 本增量执行结束可删除。
--
-- 说明：
--   data_version 是聚合级乐观锁，任何影响确认依据的变更都递增；
--   candidate_round 只用于识别「同为待接单但已转公开」这类轮次变化。
--   两个列都有非空默认值，存量行自动归零，不需要数据回填。
-- ============================================================

-- 订单聚合版本与候选轮次
set @col := (select count(*) from information_schema.columns
             where table_schema = database() and table_name = 'meal_order' and column_name = 'data_version');
set @sql := if(@col = 0,
    'alter table meal_order add column data_version bigint not null default 0 comment ''订单数据版本，所有修改路径递增'' after close_reason',
    'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
             where table_schema = database() and table_name = 'meal_order' and column_name = 'candidate_round');
set @sql := if(@col = 0,
    'alter table meal_order add column candidate_round bigint not null default 0 comment ''候选轮次，指派转公开时递增'' after data_version',
    'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 候选行版本与轮次
set @col := (select count(*) from information_schema.columns
             where table_schema = database() and table_name = 'meal_order_candidate' and column_name = 'data_version');
set @sql := if(@col = 0,
    'alter table meal_order_candidate add column data_version bigint not null default 0 comment ''候选数据版本'' after respond_time',
    'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col := (select count(*) from information_schema.columns
             where table_schema = database() and table_name = 'meal_order_candidate' and column_name = 'candidate_round');
set @sql := if(@col = 0,
    'alter table meal_order_candidate add column candidate_round bigint not null default 0 comment ''候选轮次'' after data_version',
    'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 厨房候选判断与 countPending 都按 (order_id, candidate_round, status) 过滤
set @idx := (select count(*) from information_schema.statistics
             where table_schema = database() and table_name = 'meal_order_candidate'
               and index_name = 'idx_meal_candidate_round');
set @sql := if(@idx = 0,
    'alter table meal_order_candidate add index idx_meal_candidate_round (order_id, candidate_round, status)',
    'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 提案版本
set @col := (select count(*) from information_schema.columns
             where table_schema = database() and table_name = 'meal_proposal' and column_name = 'data_version');
set @sql := if(@col = 0,
    'alter table meal_proposal add column data_version bigint not null default 0 comment ''提案数据版本，所有修改路径递增'' after dish_id',
    'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
