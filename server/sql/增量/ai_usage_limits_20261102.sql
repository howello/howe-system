-- 已写入 ai.sql 的 AI 受理次数和并发上限；用于已有库，执行结束可删除。
-- 幂等补充缺失参数，不覆盖已有自定义限额。
-- AI 受理次数和并发上限：按上海自然日，失败的已受理调用也计数，拒绝不扣次数。
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '用户每日文本AI受理次数', 'ai.usage.chat.userDailyLimit', '100', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.chat.userDailyLimit');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '家庭每日文本AI受理次数', 'ai.usage.chat.familyDailyLimit', '500', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.chat.familyDailyLimit');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '用户文本AI并发数', 'ai.usage.chat.userConcurrency', '2', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.chat.userConcurrency');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '家庭文本AI并发数', 'ai.usage.chat.familyConcurrency', '4', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.chat.familyConcurrency');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '用户每日图片AI受理次数', 'ai.usage.image.userDailyLimit', '20', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.image.userDailyLimit');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '家庭每日图片AI受理次数', 'ai.usage.image.familyDailyLimit', '100', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.image.familyDailyLimit');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '用户图片AI并发数', 'ai.usage.image.userConcurrency', '2', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.image.userConcurrency');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '家庭图片AI并发数', 'ai.usage.image.familyConcurrency', '4', 'N', 'admin', sysdate(), '受理次数限额，不等同于人民币费用预算；正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.image.familyConcurrency');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '全局文本AI并发数', 'ai.usage.chat.globalConcurrency', '8', 'N', 'admin', sysdate(), '全局并发保护，正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.chat.globalConcurrency');
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, `sensitive`, param_type)
select '全局图片AI并发数', 'ai.usage.image.globalConcurrency', '4', 'N', 'admin', sysdate(), '全局并发保护，正整数', 'N', 'AI'
where not exists (select 1 from sys_config where config_key = 'ai.usage.image.globalConcurrency');
