-- 已写入 pregnancy.sql 的「待办」一节。已有库升级专用，本增量执行结束可删除。
-- 如尚未升级每日重复提醒，请先执行 pregnancy_daily_todo_reminder_20261123.sql。

set @ddl = if(exists (
    select 1 from information_schema.columns where table_schema = database()
    and table_name = 'preg_todo' and column_name = 'checkup_id'
), 'select 1', 'alter table preg_todo add column checkup_id bigint unsigned default null comment ''默认产检待办来源''');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @ddl = if(exists (
    select 1 from information_schema.columns where table_schema = database()
    and table_name = 'preg_todo' and column_name = 'checkup_occurrence_date'
), 'select 1', 'alter table preg_todo add column checkup_occurrence_date date default null comment ''默认待办原定日期''');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @ddl = if(exists (
    select 1 from information_schema.statistics where table_schema = database()
    and table_name = 'preg_todo' and index_name = 'uk_preg_todo_checkup_occurrence'
), 'select 1', 'alter table preg_todo add unique key uk_preg_todo_checkup_occurrence (checkup_id, checkup_occurrence_date)');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;
