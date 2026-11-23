-- ============================================================
-- 孕期待办每日提醒增量脚本
--
-- 已写入：pregnancy.sql 的「待办」和「后台任务」。
-- 适用：已有孕期库。保留待办、通知开关及机器人凭证的现有值。
-- 执行：mysql -u root -p howe-system < 增量/pregnancy_daily_todo_reminder_20261123.sql
-- 本增量执行结束可删除。
-- ============================================================

set @preg_todo_repeat_daily_ddl = if(
    exists (select 1 from information_schema.columns
            where table_schema = database() and table_name = 'preg_todo' and column_name = 'repeat_daily'),
    'select 1',
    'alter table preg_todo add column repeat_daily tinyint(1) not null default 0 comment ''从待办日期起每天提醒'' after due_time'
);
prepare preg_todo_repeat_daily_stmt from @preg_todo_repeat_daily_ddl;
execute preg_todo_repeat_daily_stmt;
deallocate prepare preg_todo_repeat_daily_stmt;
