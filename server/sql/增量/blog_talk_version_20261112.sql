-- ============================================================
-- 说说数据版本增量（C3）
--
-- 已写入：blog.sql 的「说说数据版本」一节（新库跑 blog.sql 即可，不需要本文件）
-- 适用：已建好、需要说说写工具版本 CAS 的库
-- 执行：mysql -u root -p howe-system < 增量/blog_talk_version_20261112.sql
-- 本增量执行结束可删除。
-- ============================================================

-- 说说新增数据版本；所有修改路径递增，工具按版本 CAS。
set @col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'blog_talk' and column_name = 'data_version');
set @sql := if(@col = 0,
  'alter table blog_talk add column data_version bigint not null default 0 comment ''数据版本，所有修改路径递增'' after status',
  'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;
