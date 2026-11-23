-- App 发版机器人配置。新库执行 base.sql 后安装，已有库可直接执行，重复执行保留配置。
create table if not exists app_release_notify_config (
  config_id   bigint       not null auto_increment comment '配置ID',
  app_key     varchar(64)  not null                comment '应用标识',
  label       varchar(80)  not null default ''     comment '群备注',
  webhook_key varchar(1024) not null               comment '加密的群机器人 Webhook Key',
  enabled     tinyint      not null default 0      comment '是否启用（0停用 1启用）',
  primary key (config_id),
  key idx_app_release_notify_app (app_key, enabled)
) engine=innodb default charset=utf8mb4 comment='App 发版通知机器人配置';

-- 旧孕期配置仍只用于孕期提醒，保留开关和凭证，只更新参数说明。
update sys_config
set remark = '用于孕期提醒。填写企业微信群机器人 Webhook 的 key 参数'
where config_key = 'pregnancy.wecom.webhookKey'
  and remark like '%宝宝 App 发版%';

update sys_config
set remark = '孕期提醒的企微通道总开关（0 停用，1 启用）'
where config_key = 'pregnancy.wecom.enabled'
  and remark like '%宝宝 App 发版%';
