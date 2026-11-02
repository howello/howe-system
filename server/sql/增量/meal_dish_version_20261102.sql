-- 已写入 meal.sql 的 meal_dish 建表；本增量用于已有库，执行结束可删除。
set @ddl = if((select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'meal_dish' and column_name = 'data_version') = 0,
  'alter table meal_dish add column data_version bigint not null default 0 comment ''菜品数据版本''', 'select 1');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;
