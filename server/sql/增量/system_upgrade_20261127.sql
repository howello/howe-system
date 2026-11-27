-- ============================================================
-- 2026-11-27 系统增量升级脚本
--
-- 已写入 ai.sql 的渠道、场景路由和参数章节，book.sql 的节点索引和管理角色章节，
-- 以及 pregnancy.sql 的「12、提醒规则」和「14、参数种子」。
-- 适用：已初始化 AI、读书和孕期模块的 MySQL 8.0+ 库。
-- 执行：mysql --default-character-set=utf8mb4 -u root -p howe-system < 增量/system_upgrade_20261127.sql
-- 与新版后端一起发布。MySQL DDL 会隐式提交，脚本按模块分段。
-- 本增量执行结束可删除。
-- ============================================================

-- 一、AI 服务商、参数和场景路由

start transaction;

-- 停用退役协议的渠道，保留渠道、模型及调用历史。
update ai_channel
set enabled = 0, update_time = sysdate()
where enabled = 1 and provider_code not in ('dashscope', 'openai-compatible', 'anthropic');

-- 在删除参数前，将场景所引用的风格文案写入路由 JSON，保留其他参数及已有文案。
update ai_scene_route route
left join sys_config cfg on cfg.config_key = concat(
    'ai.image.styleSuffix.', json_unquote(json_extract(route.params, '$.styleSuffixKey')))
set route.params = json_remove(
    case
        when json_contains_path(route.params, 'one', '$.styleSuffix') = 0
            and nullif(trim(cfg.config_value), '') is not null
        then json_set(route.params, '$.styleSuffix', trim(cfg.config_value))
        else route.params
    end,
    '$.styleSuffixKey')
where route.capability = 'IMAGE' and json_contains_path(route.params, 'one', '$.styleSuffixKey') = 1;

-- 原地改名，保留主模型、降级链和参数；已有新场景时保留其配置。
update ai_scene_route old_route
left join ai_scene_route new_route
    on new_route.scene = 'app.version.log' and new_route.capability = old_route.capability
set old_route.scene = 'app.version.log'
where old_route.scene = 'meal.app.version.log'
  and old_route.capability = 'CHAT'
  and new_route.id is null;

delete from sys_config where config_key in (
    'ai.default.chat.model',
    'ai.default.vision.model',
    'ai.default.image.model',
    'ai.default.embedding.model',
    'ai.retry.max',
    'ai.retry.backoffMs',
    'ai.timeout.chat',
    'ai.timeout.image',
    'ai.image.styleSuffix.recipe.photo',
    'ai.image.styleSuffix.recipe.illustration',
    'ai.image.styleSuffix.recipe.watercolor',
    'ai.image.styleSuffix.recipe.minimal',
    'ai.image.transfer.retryMax',
    'ai.image.transfer.retryBackoffMs',
    'ai.image.transfer.compensateMaxAttempt',
    'ai.image.transfer.expireMinutes',
    'ai.usage.chat.userDailyLimit',
    'ai.usage.chat.familyDailyLimit',
    'ai.usage.chat.userConcurrency',
    'ai.usage.chat.familyConcurrency',
    'ai.usage.image.userDailyLimit',
    'ai.usage.image.familyDailyLimit',
    'ai.usage.image.userConcurrency',
    'ai.usage.image.familyConcurrency',
    'ai.usage.chat.globalConcurrency',
    'ai.usage.image.globalConcurrency'
);

commit;

-- MySQL 的 DDL 会隐式提交，放在数据迁移事务之后执行。
alter table ai_channel
    modify column provider_code varchar(64) not null comment '固定服务商标识';

drop table if exists ai_provider;

-- 二、读书共享书架索引及锁表清理

SET @book_parent_index_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'book_node' AND INDEX_NAME = 'idx_book_node_parent'
), 'SELECT 1', 'ALTER TABLE book_node ADD INDEX idx_book_node_parent (parent_id, deleted_at, sort_order, id)');
PREPARE book_parent_index_stmt FROM @book_parent_index_sql;
EXECUTE book_parent_index_stmt;
DEALLOCATE PREPARE book_parent_index_stmt;

UPDATE sys_role SET remark = '管理共享书架的后台节点'
WHERE role_key = 'book_manager' AND remark = '管理读书后台节点，可跨账号查看和操作读书节点';

DROP TABLE IF EXISTS book_tree_lock;

-- 三、孕期提醒规则

-- 1、规则表
create table if not exists preg_reminder_rule (
  id            bigint unsigned not null auto_increment comment '规则ID',
  rule_code     varchar(32)     not null                comment '规则编码（与 PregnancyReminderRule 枚举一致）',
  enabled       tinyint(1)      not null default 0      comment '是否启用（1启用 0关闭）',
  remind_time   varchar(5)       not null default '09:00' comment '提醒时刻 HH:mm（业务时区）',
  days_before   int             default null            comment '提前天数（产检提前提醒）',
  weekday       tinyint         default null            comment '星期（0 周日；待产包周提醒）',
  rule_version  int             not null default 1      comment '表级规则版本（整体保存时递增）',
  del_flag      char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by     varchar(64)     default ''              comment '创建者',
  create_time   datetime(3)     default null            comment '创建时间',
  update_by     varchar(64)     default ''              comment '更新者',
  update_time   datetime(3)     default null            comment '更新时间',
  remark        varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_reminder_rule_code (rule_code, del_flag)
) engine=innodb auto_increment=1 comment = '孕期-提醒规则';

start transaction;

-- 2、迁移合法的旧 JSON；将缺失值和 JSON null 读为 SQL NULL。
set @old_rules := (
    select case when json_valid(config_value) then config_value end
    from sys_config
    where config_key = 'pregnancy.reminder.rules'
    limit 1
);

insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select j.code, coalesce(j.enabled, 0), coalesce(j.remind_time, '09:00'),
       j.days_before, j.weekday, coalesce(j.rule_version, 1), 'admin', sysdate()
from json_table(coalesce(@old_rules, json_object()), '$' columns (
    rule_version int path '$.reminderVersion' null on empty null on error,
    nested path '$.rules[*]' columns (
        code varchar(32) path '$.code' null on empty null on error,
        enabled tinyint path '$.enabled' null on empty null on error,
        remind_time varchar(5) path '$.time' null on empty null on error,
        days_before int path '$.daysBefore' null on empty null on error,
        weekday tinyint path '$.weekday' null on empty null on error
    )
)) j
where j.code is not null
  and not exists (
      select 1 from preg_reminder_rule r
      where r.rule_code = convert(j.code using utf8mb4) collate utf8mb4_general_ci and r.del_flag = '0'
  );

-- 3、补齐缺失的规则种子（枚举默认值）
insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select 'CHECKUP_BEFORE', 1, '20:00', 1, null, 1, 'admin', sysdate() from dual
where not exists (select 1 from preg_reminder_rule where rule_code = 'CHECKUP_BEFORE' and del_flag = '0');
insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select 'CHECKUP_TODAY', 1, '07:30', 0, null, 1, 'admin', sysdate() from dual
where not exists (select 1 from preg_reminder_rule where rule_code = 'CHECKUP_TODAY' and del_flag = '0');
insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select 'DAILY_RECORD', 0, '21:00', 0, null, 1, 'admin', sysdate() from dual
where not exists (select 1 from preg_reminder_rule where rule_code = 'DAILY_RECORD' and del_flag = '0');
insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select 'BAG_WEEKLY', 0, '10:00', 0, 0, 1, 'admin', sysdate() from dual
where not exists (select 1 from preg_reminder_rule where rule_code = 'BAG_WEEKLY' and del_flag = '0');
insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select 'FOLIC_ACID', 1, '20:00', null, null, 1, 'admin', sysdate() from dual
where not exists (select 1 from preg_reminder_rule where rule_code = 'FOLIC_ACID' and del_flag = '0');
insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select 'DAILY_GROWTH', 1, '09:00', null, null, 1, 'admin', sysdate() from dual
where not exists (select 1 from preg_reminder_rule where rule_code = 'DAILY_GROWTH' and del_flag = '0');
insert into preg_reminder_rule (rule_code, enabled, remind_time, days_before, weekday, rule_version, create_by, create_time)
select 'WEEKLY_GROWTH', 1, '10:00', null, null, 1, 'admin', sysdate() from dual
where not exists (select 1 from preg_reminder_rule where rule_code = 'WEEKLY_GROWTH' and del_flag = '0');

-- 4、删除 sys_config 旧记录（规则已改由业务表维护）
delete from sys_config where config_key = 'pregnancy.reminder.rules';

commit;
