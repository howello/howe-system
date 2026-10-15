-- ============================================================
-- share.sql — WebDAV 文件分享短链模块脚本（module-share）
--
-- 内容：三张业务表的建表、后台「分享管理」目录与菜单/按钮权限、share.* 参数配置。
--
-- 三张表：
--   share_source       WebDAV 源配置（密码密文入库，接口脱敏）
--   share_link         分享记录（短码 + 约束 + 缓存状态 + 存储落点）
--   share_access_log   分享访问审计
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < share.sql
-- 依赖：需先执行 base.sql（本脚本会向 sys_menu / sys_config 写入数据）。
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」，
-- 并确认已在 .env（或运行环境变量）配置 SHARE_SECRET_KEY（源密码加解密主密钥）。
-- ============================================================

-- ----------------------------
-- 1、WebDAV 源配置
-- ----------------------------
drop table if exists share_source;
create table share_source (
  source_id       bigint(20)   not null auto_increment  comment '源ID',
  name            varchar(100) not null                 comment '源名称',
  base_url        varchar(500) not null                 comment 'WebDAV 根地址，如 https://dav.jianguoyun.com/dav/',
  username        varchar(200) not null                 comment '账号',
  password_cipher varchar(500) not null                 comment '应用密码（密文，AES-GCM）',
  root_path       varchar(500) default '/'              comment '默认根目录',
  status          char(1)      default '0'              comment '状态（0正常 1停用）',
  create_by       varchar(64)  default ''               comment '创建者',
  create_time     datetime                              comment '创建时间',
  update_by       varchar(64)  default ''               comment '更新者',
  update_time     datetime                              comment '更新时间',
  remark          varchar(500) default null             comment '备注',
  primary key (source_id)
) engine=innodb auto_increment=1 comment = 'WebDAV 源配置表';

-- ----------------------------
-- 2、分享记录
-- ----------------------------
drop table if exists share_link;
create table share_link (
  link_id           bigint(20)    not null auto_increment comment '分享ID',
  code              varchar(16)   not null                comment '短码（随机 Base62）',
  source_id         bigint(20)    not null                comment '源ID',
  file_path         varchar(1000) not null                comment 'WebDAV 源文件路径',
  file_name         varchar(255)  not null                comment '文件名',
  file_ext          varchar(32)   default ''              comment '扩展名（小写，不含点）',
  file_size         bigint(20)    default 0               comment '字节数',
  content_type      varchar(128)  default ''              comment 'MIME 类型',
  storage_type      char(1)       default '0'             comment '存储位置（0本地 1R2）',
  storage_location  varchar(1000) default ''              comment '存储地址（本地相对路径或 R2 对象键）',
  password_hash     varchar(100)  default ''              comment '访问密码 BCrypt 哈希，空表示无密码',
  expire_at         datetime      default null            comment '过期时间，NULL 表示不过期',
  max_views         int(11)       default 1               comment '最大查看次数，<=0 表示不限',
  view_count        int(11)       default 0               comment '已查看次数',
  burn_after_read   char(1)       default '0'             comment '阅后即焚（0否 1是）',
  cache_status      char(1)       default '0'             comment '缓存状态（0待缓存 1缓存中 2就绪 3失败）',
  cache_error       varchar(500)  default ''              comment '缓存失败原因',
  cache_retry_count int(11)       default 0               comment '缓存重试次数',
  status            char(1)       default '0'             comment '链接状态（0正常 1已失效 2已焚毁）',
  last_view_time    datetime      default null            comment '最近访问时间',
  create_by         varchar(64)   default ''              comment '创建者',
  create_time       datetime                              comment '创建时间',
  update_by         varchar(64)   default ''              comment '更新者',
  update_time       datetime                              comment '更新时间',
  remark            varchar(500)  default null            comment '备注',
  primary key (link_id),
  unique key uk_share_code (code),
  key idx_share_cache (status, cache_status)
) engine=innodb auto_increment=1 comment = '分享记录表';

-- ----------------------------
-- 3、分享访问日志
-- ----------------------------
drop table if exists share_access_log;
create table share_access_log (
  log_id      bigint(20)   not null auto_increment comment '日志ID',
  link_id     bigint(20)   not null                comment '分享ID',
  code        varchar(16)  default ''              comment '短码',
  ip          varchar(64)  default ''              comment '访问IP',
  user_agent  varchar(500) default ''              comment 'User-Agent',
  action      varchar(20)  default ''              comment '动作（detail/verify/open/file/download）',
  result      varchar(20)  default ''              comment '结果（success/fail）',
  message     varchar(500) default ''              comment '说明',
  create_time datetime                             comment '访问时间',
  primary key (log_id),
  key idx_share_log_link (link_id)
) engine=innodb auto_increment=1 comment = '分享访问日志表';

-- ----------------------------
-- 4、后台菜单
--
-- 不硬编码 menu_id：sys_menu 的 auto_increment 与既有数据无关，写死会撞车。
-- 用 LAST_INSERT_ID() 逐级捕获新建目录/菜单的 ID。
-- 菜单归超级管理员使用，不新建业务角色（与 ai.sql 一致）。
-- ----------------------------
delete from sys_menu where perms like 'share:%' or (menu_name = '分享管理' and parent_id = 0);

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分享管理', 0, '7', 'share', null, 1, 0, 'M', '0', '0', '', 'link', 'admin', sysdate(), '文件分享短链目录');

select @shareDirId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('WebDAV 源', @shareDirId, '1', 'source', 'share/source/index', 1, 0, 'C', '0', '0', 'share:source:list', 'server', 'admin', sysdate(), 'WebDAV 源管理菜单');

select @shareSourceMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分享链接', @shareDirId, '2', 'link', 'share/link/index', 1, 0, 'C', '0', '0', 'share:link:list', 'clipboard', 'admin', sysdate(), '分享链接管理菜单');

select @shareLinkMenuId := LAST_INSERT_ID();

-- 源管理按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('源查询', @shareSourceMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'share:source:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('源新增', @shareSourceMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'share:source:add', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('源修改', @shareSourceMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'share:source:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('源删除', @shareSourceMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'share:source:remove', '#', 'admin', sysdate(), '');

-- 分享链接按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分享查询', @shareLinkMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'share:link:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分享新增', @shareLinkMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'share:link:add', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分享修改', @shareLinkMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'share:link:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分享删除', @shareLinkMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'share:link:remove', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('重新缓存', @shareLinkMenuId, '5', '#', '', 1, 0, 'F', '0', '0', 'share:link:recache', '#', 'admin', sysdate(), '');

-- ----------------------------
-- 5、参数配置（share.*）
--
-- config_type='Y' 系统内置。键名与 ConfigConstants 中的常量一一对应。
-- 先 delete 再 insert，保证脚本可重复执行。
-- ----------------------------
delete from sys_config where config_key like 'share.%';

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-本地缓存上限（字节）', 'share.cache.localMaxSize', '10485760', 'Y', 'admin', sysdate(),
        '小于该值且扩展名在文档白名单内的文件落本地磁盘，否则落 R2');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-文档白名单扩展名', 'share.cache.docExts', 'md,pdf,txt,doc,docx,xls,xlsx,ppt,pptx', 'Y', 'admin', sysdate(),
        '逗号分隔，不超上限的这类文件落本地磁盘；判定以扩展名优先');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-缓存失败自动重试', 'share.cache.retryEnabled', 'true', 'Y', 'admin', sysdate(),
        '开启后定时任务会把 cache_status=3 且未超重试上限的记录重新入队');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-缓存最大重试次数', 'share.cache.maxRetry', '3', 'Y', 'admin', sysdate(),
        '缓存失败重试上限，超过后不再自动重试');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-路由缓存开关', 'share.route.cacheEnabled', 'true', 'Y', 'admin', sysdate(),
        '是否启用 Redis 路由只读缓存（MySQL 始终是权威）');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-路由缓存 TTL（秒）', 'share.route.ttl', '604800', 'Y', 'admin', sysdate(),
        '分享无过期时间时路由缓存的存活时间');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-密码尝试次数上限', 'share.password.maxAttempts', '5', 'Y', 'admin', sysdate(),
        '同一 IP 在统计窗口内的密码尝试次数上限');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-密码尝试统计窗口（秒）', 'share.password.windowSeconds', '60', 'Y', 'admin', sysdate(),
        '密码尝试次数的统计窗口长度');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-会话票据有效期（秒）', 'share.ticket.ttl', '300', 'Y', 'admin', sysdate(),
        '进入就绪态后下发的 view ticket 有效期，期间可重复读取文件');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-单文件下载上限（字节）', 'share.download.maxSize', '52428800', 'Y', 'admin', sysdate(),
        '从 WebDAV 取文件的大小上限，超过则拒绝缓存');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-访问日志保留天数', 'share.access.log.retainDays', '30', 'Y', 'admin', sysdate(),
        '定时任务清理超过该天数的访问日志');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('文件分享-短链基址', 'share.baseUrl', 'https://s.wyantao.com/s/', 'Y', 'admin', sysdate(),
        '拼短链展示用，末尾带 /，与后端 /s/{code} 路由一致；本地联调可临时改成 http://localhost:9527/s/');
