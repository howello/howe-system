-- 已写入 pregnancy.sql 的「待产包物品」及「参数种子」两节。
-- 本增量执行结束可删除。

set @purchase_date_exists = exists (select 1 from information_schema.columns where table_schema = database()
    and table_name = 'preg_bag_item' and column_name = 'purchase_date');
set @add_purchase_date = if(@purchase_date_exists,
    'select 1',
    'alter table preg_bag_item add column purchase_date date default null comment ''购买日期'' after channel');
prepare preg_purchase_ddl from @add_purchase_date;
execute preg_purchase_ddl;
deallocate prepare preg_purchase_ddl;

start transaction;
update preg_bag_item i join preg_expense e
    on e.source_type = 'BAG_ITEM' and e.source_id = i.id and e.source_slot = 'TOTAL' and e.del_flag = '0'
set i.purchase_date = e.spent_date
where not @purchase_date_exists and i.purchase_date is null and i.del_flag = '0';

-- 仅在新参数缺失时承接原配置，保留原凭证的密文。
insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark, `sensitive`)
select '孕期-企微机器人凭证', 'pregnancy.wecom.webhookKey',
       coalesce((select config_value from sys_config where config_key = 'sys.wecom.webhookKey'
                 order by config_id desc limit 1), ''), 'N', '孕期', 'admin', sysdate(),
       '仅用于孕期提醒。填写企业微信群机器人 Webhook 的 key 参数', 'Y'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.wecom.webhookKey');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark, `sensitive`)
select '孕期-企微通知开关', 'pregnancy.wecom.enabled',
       coalesce((select config_value from sys_config where config_key = 'sys.wecom.enabled'
                 order by config_id desc limit 1), '0'), 'N', '孕期', 'admin', sysdate(),
       '孕期提醒通道总开关（0 停用，1 启用）', 'N'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.wecom.enabled');

-- 两个旧键只由孕期模块读取，防止后台继续维护旧入口。
delete from sys_config where config_key in ('sys.wecom.webhookKey', 'sys.wecom.enabled');
commit;
