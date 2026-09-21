-- ----------------------------------------------------------------------------
-- AI 调用网关 初始化脚本
--
-- 库：howe-system，字符集 utf8mb4
-- 执行：mysql -u root -p howe-system < ai_20260918.sql
--
-- 建六张表：
--   ai_provider     服务商（协议 + 默认接入点），只读基础数据，由本脚本初始化
--   ai_channel      渠道（服务商 + 一组密钥），密钥加密存储
--   ai_model        模型（渠道下的具体模型，按能力类型区分）
--   ai_scene_route  场景路由（场景 → 主模型 + 有序降级链 + 参数覆盖）
--   ai_call_log     调用记录（每次尝试一条）
--   ai_image_asset  生成图资产（便于后续清理对象存储与统计）
--
-- 同时写入 ai.* 参数配置、四个后台菜单与按钮权限。
--
-- 幂等：建表用 drop + create；参数与菜单先 delete 再 insert，可重复执行。
-- 执行后请到「系统管理 > 参数设置」确认 ai.enabled 仍为关闭状态，
-- 并在环境变量中配置 AI_SECRET_KEY（渠道密钥的主密钥）后再录入渠道。
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 1. 服务商
-- ----------------------------------------------------------------------------
drop table if exists ai_provider;
create table ai_provider (
  id                bigint(20)      not null auto_increment    comment '主键ID',
  code              varchar(64)     not null                   comment '服务商标识，全局唯一',
  name              varchar(128)    not null                   comment '展示名',
  protocol          varchar(32)     not null                   comment '适配器协议：dashscope / openai / raw-http',
  default_base_url  varchar(255)    default null               comment '默认接入点，可被渠道覆盖',
  supports          json            default null               comment '支持的能力列表，如 ["chat","vision","image","embedding"]',
  enabled           tinyint(1)      not null default 1         comment '是否启用（0停用 1启用）',
  sort              int(11)         not null default 1         comment '排序，越小越靠前',
  remark            varchar(500)    default null               comment '备注',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (id),
  unique key uk_ai_provider_code (code)                        comment '服务商标识唯一'
) engine=innodb auto_increment=1 comment = 'AI 服务商表（协议与默认接入点）';

-- ----------------------------------------------------------------------------
-- 2. 渠道
-- ----------------------------------------------------------------------------
drop table if exists ai_channel;
create table ai_channel (
  id                bigint(20)      not null auto_increment    comment '主键ID',
  provider_code     varchar(64)     not null                   comment '关联 ai_provider.code',
  name              varchar(128)    not null                   comment '渠道名称，如「百炼-主账号」',
  api_key           varchar(512)    default null               comment '接口密钥，AES-GCM 加密后入库，绝不存明文',
  base_url          varchar(255)    default null               comment '接入点，覆盖服务商默认值',
  extra             json            default null               comment '扩展配置（代理、region、组织ID等）',
  priority          int(11)         not null default 1         comment '降级顺序，越小越先',
  enabled           tinyint(1)      not null default 1         comment '是否启用（0停用 1启用）',
  health_status     varchar(16)     not null default 'UNKNOWN' comment '健康状态：UNKNOWN / UP / DOWN',
  last_check_at     datetime        default null               comment '最近一次连通性检测时间',
  remark            varchar(500)    default null               comment '备注',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (id),
  key idx_ai_channel_provider (provider_code, enabled)         comment '按服务商与启停筛选',
  key idx_ai_channel_priority (priority)                       comment '按降级顺序排序'
) engine=innodb auto_increment=1 comment = 'AI 渠道表（服务商下的一组密钥）';

-- ----------------------------------------------------------------------------
-- 3. 模型
-- ----------------------------------------------------------------------------
drop table if exists ai_model;
create table ai_model (
  id                bigint(20)      not null auto_increment    comment '主键ID',
  channel_id        bigint(20)      not null                   comment '归属渠道ID',
  capability        varchar(16)     not null                   comment '能力类型：CHAT / VISION / IMAGE / EMBEDDING',
  model_name        varchar(128)    not null                   comment '传给厂商的模型名，如 qwen-plus',
  display_name      varchar(128)    default null               comment '展示名',
  max_tokens        int(11)         default null               comment '最大输出 token 数',
  extra             json            default null               comment '扩展配置（支持的尺寸列表、默认尺寸、dimensions等）',
  enabled           tinyint(1)      not null default 1         comment '是否启用（0停用 1启用）',
  remark            varchar(500)    default null               comment '备注',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (id),
  key idx_ai_model_channel (channel_id, enabled)               comment '按渠道与启停筛选',
  key idx_ai_model_capability (capability, enabled)            comment '按能力类型筛选'
) engine=innodb auto_increment=1 comment = 'AI 模型表（渠道下的具体模型）';

-- ----------------------------------------------------------------------------
-- 4. 场景路由
-- ----------------------------------------------------------------------------
drop table if exists ai_scene_route;
create table ai_scene_route (
  id                  bigint(20)    not null auto_increment    comment '主键ID',
  scene               varchar(128)  not null                   comment '场景标识，如 blog.recipe.cover',
  capability          varchar(16)   not null                   comment '能力类型：CHAT / VISION / IMAGE / EMBEDDING',
  primary_model_id    bigint(20)    not null                   comment '主模型ID',
  fallback_model_ids  json          default null               comment '有序降级链，元素为模型ID，如 [3,7]',
  params              json          default null               comment '场景级参数覆盖，如 {"temperature":0.7,"size":"1024*1024","styleSuffixKey":"recipe.photo"}',
  enabled             tinyint(1)    not null default 1         comment '是否启用（0停用 1启用）',
  remark              varchar(500)  default null               comment '备注',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  primary key (id),
  unique key uk_ai_scene_route (scene, capability)             comment '同一场景与能力只允许一条路由'
) engine=innodb auto_increment=1 comment = 'AI 场景路由表（场景到模型的映射与降级链）';

-- ----------------------------------------------------------------------------
-- 5. 调用记录
-- ----------------------------------------------------------------------------
drop table if exists ai_call_log;
create table ai_call_log (
  id                bigint(20)      not null auto_increment    comment '主键ID',
  trace_id          varchar(64)     default null               comment '链路ID，一次业务调用一个，降级产生的多条记录共用',
  scene             varchar(128)    default null               comment '场景标识',
  capability        varchar(16)     default null               comment '能力类型',
  provider_code     varchar(64)     default null               comment '实际生效的服务商标识',
  channel_id        bigint(20)      default null               comment '实际生效的渠道ID',
  model_id          bigint(20)      default null               comment '实际生效的模型ID',
  attempt           int(11)         not null default 1         comment '第几次尝试，降级会加一',
  input_tokens      int(11)         default null               comment '输入 token 数',
  output_tokens     int(11)         default null               comment '输出 token 数',
  image_count       int(11)         default null               comment '生成图片张数',
  elapsed_ms        int(11)         default null               comment '耗时（毫秒）',
  status            varchar(16)     not null                   comment '状态：SUCCESS / FAILED',
  error_code        varchar(32)     default null               comment '错误码',
  request_digest    varchar(512)    default null               comment '脱敏后的入参摘要，不含完整 prompt 与密钥',
  operator          varchar(64)     default null               comment '触发人',
  create_time       datetime                                   comment '创建时间',
  primary key (id),
  key idx_ai_call_log_scene (scene, create_time)               comment '按场景与时间筛选',
  key idx_ai_call_log_status (status, create_time)             comment '按状态与时间筛选',
  key idx_ai_call_log_trace (trace_id)                         comment '按链路ID聚合一次业务调用',
  key idx_ai_call_log_model (model_id)                         comment '按模型统计用量'
) engine=innodb auto_increment=1 comment = 'AI 调用记录表（每次尝试一条）';

-- ----------------------------------------------------------------------------
-- 6. 生成图资产
-- ----------------------------------------------------------------------------
drop table if exists ai_image_asset;
create table ai_image_asset (
  id                bigint(20)      not null auto_increment    comment '主键ID',
  call_log_id       bigint(20)      default null               comment '关联的调用记录ID',
  object_key        varchar(512)    default null               comment '对象存储中的对象键',
  url               varchar(512)    default null               comment '永久访问地址',
  width             int(11)         default null               comment '宽度（像素）',
  height            int(11)         default null               comment '高度（像素）',
  size_bytes        bigint(20)      default null               comment '文件字节数',
  create_time       datetime                                   comment '创建时间',
  primary key (id),
  key idx_ai_image_asset_call_log (call_log_id)                comment '按调用记录查询'
) engine=innodb auto_increment=1 comment = 'AI 生成图资产表';

-- ----------------------------------------------------------------------------
-- 7. 服务商基础数据（对应代码里的三个协议适配器，只初始化一次）
-- ----------------------------------------------------------------------------
delete from ai_provider where code in ('dashscope', 'openai-compatible', 'raw-http');

insert into ai_provider (code, name, protocol, default_base_url, supports, enabled, sort, remark, create_by, create_time)
values ('dashscope', '阿里云百炼', 'dashscope', 'https://dashscope.aliyuncs.com',
        '["chat","vision","image","embedding"]', 1, 1,
        '通义千问（对话/图片理解）、通义万相（文生图）、text-embedding', 'admin', sysdate());

insert into ai_provider (code, name, protocol, default_base_url, supports, enabled, sort, remark, create_by, create_time)
values ('openai-compatible', 'OpenAI 兼容端点', 'openai', 'https://api.openai.com',
        '["chat","vision","image","embedding"]', 1, 2,
        '任何 OpenAI 兼容协议的服务：DeepSeek、月之暗面、智谱、火山方舟、自建 vLLM；接入点请在渠道上覆盖', 'admin', sysdate());

insert into ai_provider (code, name, protocol, default_base_url, supports, enabled, sort, remark, create_by, create_time)
values ('raw-http', '原生异步文生图', 'raw-http', null,
        '["image"]', 1, 3,
        'Spring AI 未覆盖的异步文生图接口，自行实现提交与轮询；接入点必须在渠道上指定', 'admin', sysdate());

-- ----------------------------------------------------------------------------
-- 8. 参数配置（ai.*）
-- ----------------------------------------------------------------------------
delete from sys_config where config_key in (
  'ai.enabled',
  'ai.default.chat.model',
  'ai.default.vision.model',
  'ai.default.image.model',
  'ai.default.embedding.model',
  'ai.fallback.enabled',
  'ai.retry.max',
  'ai.retry.backoffMs',
  'ai.timeout.chat',
  'ai.timeout.image',
  'ai.image.maxCount',
  'ai.image.styleSuffix.recipe.photo',
  'ai.image.styleSuffix.recipe.illustration',
  'ai.image.styleSuffix.recipe.watercolor',
  'ai.image.styleSuffix.recipe.minimal',
  'ai.log.retentionDays'
);

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-总开关', 'ai.enabled', 'false', 'Y', 'admin', sysdate(),
        '关闭时所有 AI 调用直接抛 AI_DISABLED，不发起外部请求');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-全局默认对话模型', 'ai.default.chat.model', '', 'Y', 'admin', sysdate(),
        '填 ai_model 的主键ID；场景未配路由时用它兜底');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-全局默认视觉模型', 'ai.default.vision.model', '', 'Y', 'admin', sysdate(),
        '填 ai_model 的主键ID');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-全局默认文生图模型', 'ai.default.image.model', '', 'Y', 'admin', sysdate(),
        '填 ai_model 的主键ID');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-全局默认向量模型', 'ai.default.embedding.model', '', 'Y', 'admin', sysdate(),
        '填 ai_model 的主键ID');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-是否启用降级链', 'ai.fallback.enabled', 'true', 'Y', 'admin', sysdate(),
        '关闭后场景路由只走主模型，不做故障切换');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-同模型重试次数', 'ai.retry.max', '1', 'Y', 'admin', sysdate(),
        '只对超时、厂商5xx、限流等瞬时错误生效；重试与降级分开计数');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-重试退避基数', 'ai.retry.backoffMs', '500', 'Y', 'admin', sysdate(),
        '第 n 次重试等待 n 倍基数，单位毫秒');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-对话超时', 'ai.timeout.chat', '60000', 'Y', 'admin', sysdate(),
        '单位毫秒');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-文生图超时', 'ai.timeout.image', '120000', 'Y', 'admin', sysdate(),
        '单位毫秒；异步厂商的轮询总时长也受它约束');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-单次最多生成张数', 'ai.image.maxCount', '4', 'Y', 'admin', sysdate(),
        '超过该值的请求直接抛 BAD_REQUEST');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-风格后缀-美食摄影', 'ai.image.styleSuffix.recipe.photo',
        '中式美食摄影，俯拍四十五度，白色或浅木色桌面，自然光，摆盘精致，热气腾腾，浅景深，高清细节，无文字，无水印，正方形构图。',
        'Y', 'admin', sysdate(), '菜谱封面的写实风格后缀，拼在业务提示词之后');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-风格后缀-插画', 'ai.image.styleSuffix.recipe.illustration',
        '扁平化矢量插画，暖色调，色块干净，构图居中，背景简洁，无文字，无水印，正方形构图。',
        'Y', 'admin', sysdate(), '菜谱封面的插画风格后缀');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-风格后缀-水彩', 'ai.image.styleSuffix.recipe.watercolor',
        '清新水彩手绘，晕染自然，留白充足，纸张纹理，柔和光线，无文字，无水印，正方形构图。',
        'Y', 'admin', sysdate(), '菜谱封面的水彩风格后缀');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-风格后缀-极简', 'ai.image.styleSuffix.recipe.minimal',
        '极简静物，纯色背景，单一光源，大量留白，构图克制，无文字，无水印，正方形构图。',
        'Y', 'admin', sysdate(), '菜谱封面的极简风格后缀');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('AI-调用记录保留天数', 'ai.log.retentionDays', '30', 'Y', 'admin', sysdate(),
        '超过天数的调用记录可清理，单位天');

-- ----------------------------------------------------------------------------
-- 9. 菜单与按钮权限
-- 不硬编码 menu_id：sys_menu 的 auto_increment 与既有数据无关，写死会撞车。
-- 先按名称捕获或新建「AI 管理」目录，再用 LAST_INSERT_ID() 逐级捕获新建菜单的 ID。
-- ----------------------------------------------------------------------------
delete from sys_menu where perms like 'ai:%' or (menu_name = 'AI 管理' and parent_id = 0);

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('AI 管理', 0, '5', 'ai', null, 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), 'AI 调用网关目录');

select @aiDirId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('AI 渠道管理', @aiDirId, '1', 'channel', 'ai/channel/index', 1, 0, 'C', '0', '0', 'ai:channel:list', 'server', 'admin', sysdate(), '渠道管理菜单');

select @aiChannelMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('AI 模型管理', @aiDirId, '2', 'model', 'ai/model/index', 1, 0, 'C', '0', '0', 'ai:model:list', 'component', 'admin', sysdate(), '模型管理菜单');

select @aiModelMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('AI 场景路由', @aiDirId, '3', 'route', 'ai/route/index', 1, 0, 'C', '0', '0', 'ai:route:list', 'tree-table', 'admin', sysdate(), '场景路由菜单');

select @aiRouteMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('AI 调用记录', @aiDirId, '4', 'calllog', 'ai/calllog/index', 1, 0, 'C', '0', '0', 'ai:calllog:list', 'log', 'admin', sysdate(), '调用记录菜单');

select @aiCallLogMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('AI 对话测试', @aiDirId, '5', 'playground', 'ai/playground/index', 1, 0, 'C', '0', '0', 'ai:playground:test', 'message', 'admin', sysdate(), '接口调试台：按渠道与模型直接调用，验证四种能力');

-- 渠道按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('渠道查询', @aiChannelMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'ai:channel:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('渠道新增', @aiChannelMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'ai:channel:add', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('渠道修改', @aiChannelMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'ai:channel:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('渠道删除', @aiChannelMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'ai:channel:remove', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('渠道连通性测试', @aiChannelMenuId, '5', '#', '', 1, 0, 'F', '0', '0', 'ai:channel:test', '#', 'admin', sysdate(), '');

-- 模型按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('模型查询', @aiModelMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'ai:model:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('模型新增', @aiModelMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'ai:model:add', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('模型修改', @aiModelMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'ai:model:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('模型删除', @aiModelMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'ai:model:remove', '#', 'admin', sysdate(), '');

-- 场景路由按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('路由编辑', @aiRouteMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'ai:route:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('路由删除', @aiRouteMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'ai:route:remove', '#', 'admin', sysdate(), '');

-- 调用记录按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('记录查询', @aiCallLogMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'ai:calllog:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('用量统计', @aiCallLogMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'ai:calllog:stats', '#', 'admin', sysdate(), '');

