-- ============================================================
-- 管理端 13 项优化 增量脚本
--
-- 已写入：
--   ai.sql    「4. 场景路由」建表（scene_name）
--             「5. 调用记录」建表（first_token_ms）
--             「7. 服务商基础数据」（三项服务商：阿里百炼 / OpenAI / Anthropic）
--             「8. 业务场景路由种子」（六条 meal 场景名称）
--   share.sql 「2. 分享记录」（source_type / remote_url / local_upload_id / 可空 source_id、file_path）
--             「2.1 本地上传暂存」（share_upload 表）
--             「菜单」分享链接 route_name = ShareLink
--   blog.sql  「友链管理菜单」route_name = BlogLink
--   base.sql  「菜单」日志管理迁入系统监控（parent_id=2, order_num=2）
--   meal.sql  「菜品按钮权限」meal:dish:owner
--             「参数配置」meal.app.packageMaxSize 与 share.upload.dir
-- 适用：只给已建好、不打算重跑上述模块脚本的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/admin_thirteen_optimizations_20261104.sql
-- 本增量执行结束可删除。
--
-- 注意：MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，列/索引是否存在先查 information_schema
--       再用预处理语句执行；脚本可重复执行。
-- ============================================================


-- ----------------------------------------------------------------------------
-- 1. ai_scene_route.scene_name
-- ----------------------------------------------------------------------------
set @sql := (
  select if(count(*) = 0,
    'alter table ai_scene_route add column scene_name varchar(100) not null default '''' comment ''场景展示名称，如 菜品封面生成；与 scene 分开，改名不影响路由'' after scene',
    'select ''ai_scene_route.scene_name 已存在''')
  from information_schema.columns
  where table_schema = database() and table_name = 'ai_scene_route' and column_name = 'scene_name'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 回填既有场景名称：先按已知六条 meal 场景映射，其余用 scene 自身兜底
update ai_scene_route set scene_name = case scene
    when 'meal.dish.generate' then '菜品文本补全'
    when 'meal.dish.search'   then '菜品智能搜索'
    when 'meal.dish.audit'    then '新菜提案自动审核'
    when 'meal.dish.update'   then '菜品做法更新'
    when 'meal.dish.cover'    then '菜品封面生成'
    when 'meal.app.version.log' then 'App 发版日志润色'
    else scene
  end
where scene_name is null or scene_name = '';

-- 收紧为 not null（幂等：已经 not null 时这句是空操作）
alter table ai_scene_route modify column scene_name varchar(100) not null comment '场景展示名称，如 菜品封面生成；与 scene 分开，改名不影响路由';


-- ----------------------------------------------------------------------------
-- 2. ai_call_log.first_token_ms
-- ----------------------------------------------------------------------------
set @sql := (
  select if(count(*) = 0,
    'alter table ai_call_log add column first_token_ms int(11) default null comment ''首字延迟（毫秒），仅产生正文的流式调用有值'' after elapsed_ms',
    'select ''ai_call_log.first_token_ms 已存在''')
  from information_schema.columns
  where table_schema = database() and table_name = 'ai_call_log' and column_name = 'first_token_ms'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;


-- ----------------------------------------------------------------------------
-- 3. 服务商：三项配置入口（保留 code，历史渠道外键不动）
-- ----------------------------------------------------------------------------
-- 阿里百炼 / OpenAI：按 code 更新展示名与协议，保留渠道、模型、日志
update ai_provider
set name = '阿里百炼', protocol = 'dashscope', default_base_url = 'https://dashscope.aliyuncs.com',
    supports = '["chat","vision","image","embedding"]', enabled = 1, sort = 1,
    remark = '通义千问（对话/图片理解）、通义万相（文生图）、text-embedding'
where code = 'dashscope';

update ai_provider
set name = 'OpenAI', protocol = 'openai', default_base_url = 'https://api.openai.com',
    supports = '["chat","vision","image","embedding"]', enabled = 1, sort = 2,
    remark = 'OpenAI 及任何 OpenAI 兼容协议的服务：DeepSeek、月之暗面、智谱、火山方舟、自建 vLLM；接入点可在渠道上覆盖。保留 code openai-compatible 以兼容既有渠道'
where code = 'openai-compatible';

-- Anthropic：新增（仅对话与图片理解）
insert into ai_provider (code, name, protocol, default_base_url, supports, enabled, sort, remark, create_by, create_time)
select 'anthropic', 'Anthropic', 'anthropic', 'https://api.anthropic.com',
       '["chat","vision"]', 1, 3,
       'Anthropic Messages API；只支持对话与图片理解，不支持文生图与向量。API 版本 2026-10-21 起不再接受 temperature/top_p/top_k',
       'admin', sysdate()
from dual
where not exists (select 1 from ai_provider p where p.code = 'anthropic');

-- 旧 raw-http：不再进入配置入口。停用服务商与关联渠道，历史数据保留供审计。
-- 若仍有场景路由 / 降级链 / 全局默认引用这些模型，下面的预检会列出，需管理员显式替换。
select '待替换的 raw-http 引用（非空即需人工处理）' as check_item,
       r.scene, r.capability, m.model_name
from ai_scene_route r
join ai_model m on m.id = r.primary_model_id
join ai_channel c on c.id = m.channel_id
join ai_provider p on p.code = c.provider_code
where p.protocol = 'raw-http';

update ai_provider set enabled = 0, remark = concat(ifnull(remark, ''), '；已停用：不再进入新配置入口，仅保留历史审计')
where protocol = 'raw-http';

update ai_channel c
join ai_provider p on p.code = c.provider_code
set c.enabled = 0
where p.protocol = 'raw-http';


-- ----------------------------------------------------------------------------
-- 4. 分享：来源扩展
-- ----------------------------------------------------------------------------
set @sql := (
  select if(count(*) = 0,
    'alter table share_link add column source_type varchar(16) not null default ''WEBDAV'' comment ''来源类型：WEBDAV / LOCAL / REMOTE'' after code',
    'select ''share_link.source_type 已存在''')
  from information_schema.columns
  where table_schema = database() and table_name = 'share_link' and column_name = 'source_type'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := (
  select if(count(*) = 0,
    'alter table share_link add column remote_url varchar(2048) default null comment ''远程链接（仅 REMOTE，匿名接口不返回）'' after file_path',
    'select ''share_link.remote_url 已存在''')
  from information_schema.columns
  where table_schema = database() and table_name = 'share_link' and column_name = 'remote_url'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := (
  select if(count(*) = 0,
    'alter table share_link add column local_upload_id bigint(20) default null comment ''本地上传暂存ID（仅 LOCAL）'' after remote_url',
    'select ''share_link.local_upload_id 已存在''')
  from information_schema.columns
  where table_schema = database() and table_name = 'share_link' and column_name = 'local_upload_id'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- source_id / file_path 改为可空（仅 WEBDAV 要求非空，由 Service 校验）
alter table share_link
  modify column source_id bigint(20) default null comment 'WebDAV 源ID（仅 WEBDAV）',
  modify column file_path varchar(1000) default null comment 'WebDAV 源文件路径（仅 WEBDAV）';

-- 旧记录默认 WEBDAV（default 已覆盖新增行，这里补齐历史行）
update share_link set source_type = 'WEBDAV' where source_type is null or source_type = '';

set @sql := (
  select if(count(*) = 0,
    'alter table share_link add unique key uk_share_local_upload (local_upload_id)',
    'select ''uk_share_local_upload 已存在''')
  from information_schema.statistics
  where table_schema = database() and table_name = 'share_link' and index_name = 'uk_share_local_upload'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql := (
  select if(count(*) = 0,
    'alter table share_link add key idx_share_source (source_id)',
    'select ''idx_share_source 已存在''')
  from information_schema.statistics
  where table_schema = database() and table_name = 'share_link' and index_name = 'idx_share_source'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 来源类型列在列表/详情都会返回，但基数低，不单独建索引


-- ----------------------------------------------------------------------------
-- 5. 本地上传暂存表
-- ----------------------------------------------------------------------------
create table if not exists share_upload (
  upload_id        bigint(20)    not null auto_increment comment '上传ID',
  token_hash       char(64)      not null                comment '上传令牌 SHA-256',
  owner_user_id    bigint(20)    not null                comment '上传者用户ID',
  file_name        varchar(255)  default ''              comment '原始文件名',
  file_size        bigint(20)    default 0               comment '字节数',
  content_type     varchar(128)  default ''              comment 'MIME 类型',
  file_ext         varchar(32)   default ''              comment '小写扩展名',
  staging_location varchar(1000) default ''              comment '暂存相对路径',
  state            varchar(16)   default 'TEMP'          comment '状态：TEMP待认领 / CLAIMED已认领 / BOUND已绑定 / EXPIRED已过期',
  link_id          bigint(20)    default null            comment '绑定的分享ID',
  expire_at        datetime      default null            comment '过期时间',
  create_time      datetime                              comment '创建时间',
  update_time      datetime                              comment '更新时间',
  primary key (upload_id),
  unique key uk_share_upload_token (token_hash),
  key idx_share_upload_expire (state, expire_at)
) engine=innodb auto_increment=1 comment = '分享本地上传暂存表';


-- ----------------------------------------------------------------------------
-- 6. 路由名修复：友链 / 分享链接都叫 Link，后注册的会覆盖先注册的（友链 404）
--    按 component 定位，避开菜单 ID（环境数据，不可硬编码）
-- ----------------------------------------------------------------------------
update sys_menu set route_name = 'BlogLink'
where component = 'blog/link/index' and (route_name is null or route_name = '' or route_name = 'Link');

update sys_menu set route_name = 'ShareLink'
where component = 'share/link/index' and (route_name is null or route_name = '' or route_name = 'Link');

-- 兜底：component 被改过时按「名称 + path」再认一次
update sys_menu set route_name = 'BlogLink'
where menu_name = '友链管理' and path = 'link' and menu_type = 'C' and (route_name is null or route_name = '' or route_name = 'Link');

update sys_menu set route_name = 'ShareLink'
where menu_name = '分享链接' and path = 'link' and menu_type = 'C' and (route_name is null or route_name = '' or route_name = 'Link');


-- ----------------------------------------------------------------------------
-- 7. 日志管理迁入系统监控
--    只改 parent_id / order_num，保留 menu_id、子菜单与角色授权；可重复执行。
-- ----------------------------------------------------------------------------
set @monitorId := (select menu_id from sys_menu
                   where menu_name = '系统监控' and parent_id = 0 and menu_type = 'M'
                   limit 1);
set @systemId := (select menu_id from sys_menu
                  where menu_name = '系统管理' and parent_id = 0 and menu_type = 'M'
                  limit 1);

-- 同时识别「原位置（系统管理下）」与「目标位置（系统监控下）」，第二次执行不会误判缺失
set @logId := (select menu_id from sys_menu
               where menu_name = '日志管理' and path = 'log' and menu_type = 'M'
                 and parent_id = @monitorId
               limit 1);
set @logId := ifnull(@logId, (select menu_id from sys_menu
                              where menu_name = '日志管理' and path = 'log' and menu_type = 'M'
                                and parent_id = @systemId
                              limit 1));

update sys_menu set parent_id = @monitorId, order_num = 2
where menu_id = @logId and @monitorId is not null;

-- 系统监控其余子菜单的顺序对齐目标结构（在线用户 1、日志 2、数据监控 3、服务监控 4、缓存监控 5、缓存列表 6）
update sys_menu set order_num = 1 where parent_id = @monitorId and menu_name = '在线用户';
update sys_menu set order_num = 3 where parent_id = @monitorId and menu_name = '数据监控';
update sys_menu set order_num = 4 where parent_id = @monitorId and menu_name = '服务监控';
update sys_menu set order_num = 5 where parent_id = @monitorId and menu_name = '缓存监控';
update sys_menu set order_num = 6 where parent_id = @monitorId and menu_name = '缓存列表';
-- 数据监控保持停用，这里不碰 status


-- ----------------------------------------------------------------------------
-- 8. 菜品归属调整权限与相关参数
-- ----------------------------------------------------------------------------
-- 按钮权限按 perms 查重；父菜单按名称 + 路径捕获，不硬编码 menu_id
set @dishMenuId := (select menu_id from sys_menu
                    where component = 'meal/dish/index' and menu_type = 'C'
                    limit 1);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '菜品归属调整', @dishMenuId, '9', '#', '', '', '', 1, 0, 'F', '0', '0',
       'meal:dish:owner', '#', 'admin', sysdate(), '仅平台管理员可见：在公共菜谱与家庭之间迁移菜品归属'
from dual
where @dishMenuId is not null
  and not exists (select 1 from sys_menu where perms = 'meal:dish:owner');

-- 参数：远程资源包上限、分享上传暂存目录（已存在则不覆盖）
insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark)
select 'App 远程资源包大小上限（字节）', 'meal.app.packageMaxSize', '52428800', 'N', '点餐', 'admin', sysdate(),
       '版本管理录入远程 wgt 链接时，有界下载校验的上限；默认 50 MiB'
from dual
where not exists (select 1 from sys_config where config_key = 'meal.app.packageMaxSize');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark)
select '分享本地上传暂存目录', 'share.upload.dir', '', 'N', '文件分享', 'admin', sysdate(),
       '留空则用系统临时目录下的 share-upload；多实例部署必须指向共享卷，否则其他实例读不到暂存字节'
from dual
where not exists (select 1 from sys_config where config_key = 'share.upload.dir');


-- ----------------------------------------------------------------------------
-- 9. 执行核对（可选，人工确认）
-- ----------------------------------------------------------------------------
select 'scene_name' as check_item, count(*) as total,
       sum(case when scene_name is null or scene_name = '' then 1 else 0 end) as empty_count
from ai_scene_route;

select 'first_token_ms' as check_item, count(*) as total from ai_call_log where first_token_ms is not null;

select 'providers' as check_item, code, name, protocol, enabled from ai_provider order by sort;

select 'share_source_type' as check_item, source_type, count(*) as total from share_link group by source_type;

select 'menu_route_name' as check_item, menu_id, menu_name, parent_id, order_num, route_name
from sys_menu
where (component = 'blog/link/index' or component = 'share/link/index' or menu_name = '日志管理')
order by menu_id;
