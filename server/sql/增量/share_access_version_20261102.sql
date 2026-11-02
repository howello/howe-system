-- 已写入 share.sql 的 share_link 建表；仅用于已有库，本增量执行结束可删除。
set @ddl = if((select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'share_link' and column_name = 'access_version') = 0,
  'alter table share_link add column access_version bigint not null default 1 comment ''访问策略版本''', 'select 1');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;
