-- ============================================================
-- pregnancy.sql — 孕期助手模块脚本（module-pregnancy）
--
-- 目标模型：单家庭、单次孕期、11 张业务表。
-- 用户、角色与授权关系使用现有系统表（sys_user / sys_role / sys_user_role）。
--
-- 11 张表：
--   preg_measurement     固定指标测量（一行存主值、次值与受控补充字段）
--   preg_checkup         产检计划（建议窗口、预约、完成与状态）
--   preg_checkup_detail  产检详情（项目 ITEM / 准备事项 PREPARATION）
--   preg_report          检查报告（可关联产检，多图）
--   preg_diet_record     饮食记录（按餐次）
--   preg_todo            待办
--   preg_bag             待产包清单
--   preg_bag_item        待产包物品（含 AI 展示内容与当前任务）
--   preg_expense         统一费用明细（来源类型 + 来源 ID + 槽位唯一）
--   preg_file            私有原图（TEMP / BOUND / ORPHAN / DELETING）
--   preg_task            后台任务（提醒 / 物品内容 / 导出）
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < pregnancy.sql
-- 依赖：需先执行 base.sql（本脚本会向 sys_role / sys_config / sys_menu 写入数据）。
--
-- 本脚本按新装库设计，会重建同名业务表。已有库升级请按发布说明另行处理。
-- ============================================================

-- ----------------------------
-- 1、固定指标测量
-- ----------------------------
drop table if exists preg_measurement;
create table preg_measurement (
  id              bigint unsigned not null auto_increment comment '测量记录ID',
  metric_code     varchar(64)     not null                comment '固定指标编码（代码目录定义）',
  occurred_at     datetime(3)     not null                comment '测量发生时刻（UTC）',
  record_date     date            not null                comment '按业务时区派生的自然日',
  unit_code       varchar(32)     default null            comment '实际单位',
  scene_code      varchar(32)     default null            comment '场景编码（血糖必填）',
  numeric_value   decimal(24,6)   default null            comment '主数值',
  secondary_value decimal(24,6)   default null            comment '次数值（血压舒张压）',
  detail_json     json            default null            comment '受控补充字段（form/level/description/name/dose）',
  note            varchar(2000)   default null            comment '备注',
  report_id       bigint unsigned default null            comment '关联报告ID',
  actor_user_id   bigint          not null                comment '记录人账号ID',
  request_id      varchar(64)     default null            comment '客户端请求ID（幂等）',
  request_hash    char(64)        default null            comment '请求内容摘要（SHA-256）',
  version         int             not null default 1      comment '乐观锁版本',
  del_flag        char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by       varchar(64)     default ''              comment '创建者',
  create_time     datetime(3)     default null            comment '创建时间',
  update_by       varchar(64)     default ''              comment '更新者',
  update_time     datetime(3)     default null            comment '更新时间',
  remark          varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_measure_request (actor_user_id, request_id),
  key idx_preg_measure_day (record_date, occurred_at, id),
  key idx_preg_measure_metric (metric_code, occurred_at, id),
  key idx_preg_measure_report (report_id)
) engine=innodb auto_increment=1 comment = '孕期-固定指标测量';

-- ----------------------------
-- 2、产检计划
-- ----------------------------
drop table if exists preg_checkup;
create table preg_checkup (
  id                 bigint unsigned not null auto_increment comment '产检ID',
  seed_key           varchar(64)     default null            comment '代码初始清单稳定键',
  title              varchar(200)    not null                comment '标题',
  suggest_start_day  int             default null            comment '建议窗口起始孕日',
  suggest_end_day    int             default null            comment '建议窗口结束孕日',
  suggested_date     date            default null            comment '手填建议日期',
  appointment_date   date            default null            comment '实际预约日期',
  appointment_time   time            default null            comment '实际预约时刻，可空',
  hospital           varchar(200)    default null            comment '医院',
  location           varchar(200)    default null            comment '地点',
  completed_date     date            default null            comment '实际完成日期',
  focus              text            default null            comment '本次重点说明',
  status             varchar(16)     not null default 'PLANNED' comment '状态（PLANNED/BOOKED/COMPLETED/CANCELLED）',
  request_id         varchar(64)     default null            comment '客户端请求ID（幂等，仅手工创建）',
  request_hash       char(64)        default null            comment '请求内容摘要',
  version            int             not null default 1      comment '乐观锁版本',
  del_flag           char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by          varchar(64)     default ''              comment '创建者',
  create_time        datetime(3)     default null            comment '创建时间',
  update_by          varchar(64)     default ''              comment '更新者',
  update_time        datetime(3)     default null            comment '更新时间',
  remark             varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_checkup_seed (seed_key),
  unique key uk_preg_checkup_request (request_id),
  key idx_preg_checkup_appt (appointment_date, status),
  key idx_preg_checkup_status (status, id)
) engine=innodb auto_increment=1 comment = '孕期-产检计划';

-- ----------------------------
-- 3、产检详情（项目与准备事项）
-- ----------------------------
drop table if exists preg_checkup_detail;
create table preg_checkup_detail (
  id                bigint unsigned not null auto_increment comment '详情ID',
  checkup_id        bigint unsigned not null                comment '产检ID',
  type              varchar(16)     not null                comment '类型（ITEM项目 PREPARATION准备事项）',
  title             varchar(200)    not null                comment '标题',
  description       text            default null            comment '说明',
  sort_order        int             not null default 0      comment '排序',
  estimated_minutes int             default null            comment '项目预计耗时（分钟）',
  done              tinyint(1)      not null default 0      comment '准备事项是否完成',
  done_at           datetime(3)     default null            comment '完成时刻',
  done_by           bigint          default null            comment '完成人账号ID',
  del_flag          char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_time       datetime(3)     default null            comment '创建时间',
  update_time       datetime(3)     default null            comment '更新时间',
  primary key (id),
  key idx_preg_checkup_detail (checkup_id, type, sort_order, id)
) engine=innodb auto_increment=1 comment = '孕期-产检详情';

-- ----------------------------
-- 4、检查报告
-- ----------------------------
drop table if exists preg_report;
create table preg_report (
  id            bigint unsigned not null auto_increment comment '报告ID',
  name          varchar(200)    not null                comment '报告名称',
  report_date   date            not null                comment '报告日期',
  checkup_id    bigint unsigned default null            comment '关联产检ID',
  note          varchar(2000)   default null            comment '备注',
  actor_user_id bigint          not null                comment '操作人账号ID',
  request_id    varchar(64)     default null            comment '客户端请求ID（幂等）',
  request_hash  char(64)        default null            comment '请求内容摘要',
  version       int             not null default 1      comment '乐观锁版本',
  del_flag      char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by     varchar(64)     default ''              comment '创建者',
  create_time   datetime(3)     default null            comment '创建时间',
  update_by     varchar(64)     default ''              comment '更新者',
  update_time   datetime(3)     default null            comment '更新时间',
  remark        varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_report_request (actor_user_id, request_id),
  key idx_preg_report_date (report_date, id),
  key idx_preg_report_checkup (checkup_id)
) engine=innodb auto_increment=1 comment = '孕期-检查报告';

-- ----------------------------
-- 5、饮食记录
-- ----------------------------
drop table if exists preg_diet_record;
create table preg_diet_record (
  id            bigint unsigned not null auto_increment comment '饮食记录ID',
  meal_type     varchar(16)     not null                comment '餐次（BREAKFAST/LUNCH/DINNER/SNACK）',
  occurred_at   datetime(3)     not null                comment '发生时刻（UTC）',
  record_date   date            not null                comment '按业务时区派生的自然日',
  content       varchar(2000)   not null                comment '内容',
  note          varchar(2000)   default null            comment '备注',
  extra_text    varchar(1000)   default null            comment '能量等补充文字',
  actor_user_id bigint          not null                comment '操作人账号ID',
  request_id    varchar(64)     default null            comment '客户端请求ID（幂等）',
  request_hash  char(64)        default null            comment '请求内容摘要',
  version       int             not null default 1      comment '乐观锁版本',
  del_flag      char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by     varchar(64)     default ''              comment '创建者',
  create_time   datetime(3)     default null            comment '创建时间',
  update_by     varchar(64)     default ''              comment '更新者',
  update_time   datetime(3)     default null            comment '更新时间',
  remark        varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_diet_request (actor_user_id, request_id),
  key idx_preg_diet_day (record_date, occurred_at, id)
) engine=innodb auto_increment=1 comment = '孕期-饮食记录';

-- ----------------------------
-- 6、待办
-- ----------------------------
drop table if exists preg_todo;
create table preg_todo (
  id         bigint unsigned not null auto_increment comment '待办ID',
  title      varchar(200)    not null                comment '标题',
  due_date   date            not null                comment '日期（允许未来）',
  due_time   time            default null            comment '可选时刻',
  note       varchar(2000)   default null            comment '备注',
  done       tinyint(1)      not null default 0      comment '是否完成',
  done_at    datetime(3)     default null            comment '完成时刻',
  done_by    bigint          default null            comment '完成人账号ID',
  request_id varchar(64)     default null            comment '客户端请求ID（幂等）',
  request_hash char(64)      default null            comment '请求内容摘要',
  version    int             not null default 1      comment '乐观锁版本',
  del_flag   char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by  varchar(64)     default ''              comment '创建者',
  create_time datetime(3)    default null            comment '创建时间',
  update_by  varchar(64)     default ''              comment '更新者',
  update_time datetime(3)    default null            comment '更新时间',
  remark     varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_todo_request (request_id),
  key idx_preg_todo_day (due_date, done, id)
) engine=innodb auto_increment=1 comment = '孕期-待办';

-- ----------------------------
-- 7、待产包清单
-- ----------------------------
drop table if exists preg_bag;
create table preg_bag (
  id         bigint unsigned not null auto_increment comment '清单ID',
  seed_key   varchar(64)     default null            comment '默认清单稳定键',
  name       varchar(200)    not null                comment '清单名称',
  note       varchar(1000)   default null            comment '备注',
  sort_order int             not null default 0      comment '排序',
  del_flag   char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_time datetime(3)    default null            comment '创建时间',
  update_time datetime(3)    default null            comment '更新时间',
  primary key (id),
  unique key uk_preg_bag_seed (seed_key),
  key idx_preg_bag_sort (sort_order, id)
) engine=innodb auto_increment=1 comment = '孕期-待产包清单';

-- ----------------------------
-- 8、待产包物品
-- ----------------------------
drop table if exists preg_bag_item;
create table preg_bag_item (
  id                     bigint unsigned not null auto_increment comment '物品ID',
  bag_id                 bigint unsigned not null                comment '清单ID',
  seed_key               varchar(64)     default null            comment '清单内稳定物品键',
  category               varchar(16)     not null                comment '分类（MOTHER/BABY）',
  name                   varchar(200)    not null                comment '名称',
  quantity               varchar(64)     default null            comment '数量（文本，如 2）',
  unit                   varchar(32)     default null            comment '单位',
  purpose                varchar(1000)   default null            comment '用途/使用说明',
  brand                  varchar(200)    default null            comment '品牌',
  channel                varchar(200)    default null            comment '购买渠道',
  purchase_date          date            default null            comment '购买日期',
  sort_order             int             not null default 0      comment '排序',
  prepared               tinyint(1)      not null default 0      comment '是否已准备',
  actor_user_id          bigint          default null            comment '操作人账号ID',
  ai_text                text            default null            comment '已显示的 AI 介绍',
  ai_image_key           varchar(500)    default null            comment '已显示的 AI 配图（R2 键）',
  current_content_task_id bigint unsigned default null           comment '当前内容生成任务ID',
  request_id             varchar(64)     default null            comment '客户端请求ID（幂等）',
  request_hash           char(64)        default null            comment '请求内容摘要',
  version                int             not null default 1      comment '乐观锁版本',
  del_flag               char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by              varchar(64)     default ''              comment '创建者',
  create_time            datetime(3)     default null            comment '创建时间',
  update_by              varchar(64)     default ''              comment '更新者',
  update_time            datetime(3)     default null            comment '更新时间',
  remark                 varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_bag_item_seed (bag_id, seed_key),
  unique key uk_preg_bag_item_request (request_id),
  key idx_preg_bag_item_list (bag_id, category, prepared, sort_order, id)
) engine=innodb auto_increment=1 comment = '孕期-待产包物品';

-- ----------------------------
-- 9、统一费用明细
-- ----------------------------
drop table if exists preg_expense;
create table preg_expense (
  id            bigint unsigned not null auto_increment comment '费用ID',
  source_type   varchar(16)     not null                comment '来源类型（CHECKUP/REPORT/BAG_ITEM）',
  source_id     bigint unsigned not null                comment '来源业务ID',
  source_slot   varchar(32)     not null default 'TOTAL' comment '来源槽位（待产包固定 TOTAL）',
  category_code varchar(32)     not null                comment '费用分类',
  label         varchar(200)    default null            comment '费用名称',
  amount        decimal(12,2)   not null                comment '实付金额',
  spent_date    date            not null                comment '实际支出日期',
  actor_user_id bigint          not null                comment '操作人账号ID',
  request_id    varchar(64)     default null            comment '客户端请求ID（幂等）',
  request_hash  char(64)        default null            comment '请求内容摘要',
  version       int             not null default 1      comment '乐观锁版本',
  del_flag      char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_by     varchar(64)     default ''              comment '创建者',
  create_time   datetime(3)     default null            comment '创建时间',
  update_by     varchar(64)     default ''              comment '更新者',
  update_time   datetime(3)     default null            comment '更新时间',
  remark        varchar(500)    default null            comment '备注',
  primary key (id),
  unique key uk_preg_expense_source (source_type, source_id, source_slot),
  unique key uk_preg_expense_request (actor_user_id, request_id),
  key idx_preg_expense_date (spent_date, category_code, id)
) engine=innodb auto_increment=1 comment = '孕期-统一费用明细';

-- ----------------------------
-- 10、私有原图
-- ----------------------------
drop table if exists preg_file;
create table preg_file (
  id               bigint unsigned not null auto_increment comment '文件ID',
  uploader_user_id bigint          not null                comment '上传人账号ID',
  original_name    varchar(255)    not null                comment '原始文件名（仅用于展示与 manifest）',
  storage_key      varchar(500)    not null                comment '私有存储键（不可变，唯一）',
  mime_type        varchar(100)    not null                comment 'MIME 类型',
  size_bytes       bigint          not null                comment '字节数',
  sha256           char(64)        not null                comment 'SHA-256 校验值',
  width            int             default null            comment '图片宽度',
  height           int             default null            comment '图片高度',
  state            varchar(16)     not null                comment '状态（TEMP/BOUND/ORPHAN/DELETING）',
  owner_type       varchar(16)     default null            comment '所属业务（REPORT/DIET）',
  owner_id         bigint unsigned default null            comment '所属业务ID',
  sort_order       int             not null default 0      comment '业务内展示顺序',
  detached_at      datetime(3)     default null            comment '解绑时刻',
  reclaim_at       datetime(3)     default null            comment '可回收时刻',
  request_id       varchar(64)     default null            comment '上传请求ID（幂等）',
  del_flag         char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_time      datetime(3)     default null            comment '创建时间',
  update_time      datetime(3)     default null            comment '更新时间',
  primary key (id),
  unique key uk_preg_file_storage (storage_key),
  unique key uk_preg_file_request (uploader_user_id, request_id),
  key idx_preg_file_owner (owner_type, owner_id, sort_order, id),
  key idx_preg_file_state (state, reclaim_at)
) engine=innodb auto_increment=1 comment = '孕期-私有原图';

-- ----------------------------
-- 11、后台任务
-- ----------------------------
drop table if exists preg_task;
create table preg_task (
  id                 bigint unsigned not null auto_increment comment '任务ID',
  kind               varchar(16)     not null                comment '类型（REMINDER/ITEM_CONTENT/EXPORT）',
  business_id        bigint unsigned default null            comment '业务ID',
  business_version   int             default null            comment '业务版本（回写校验）',
  requester_user_id  bigint          default null            comment '请求人账号ID',
  dedup_key          varchar(200)    not null                comment '去重键（发生标识 / 导出申请）',
  payload_json       json            default null            comment '受控入参',
  stage_json         json            default null            comment '阶段结果（文本/图片/快照）',
  status             varchar(16)     not null default 'PENDING' comment '状态',
  attempt            int             not null default 0      comment '已尝试次数',
  max_attempts       int             not null default 3      comment '尝试上限',
  next_run_at        datetime(3)     not null                comment '下次执行时刻',
  lease_token        varchar(64)     default null            comment '租约令牌',
  lease_until        datetime(3)     default null            comment '租约到期时刻',
  error_code         varchar(64)     default null            comment '最近错误码',
  result_ref         varchar(500)    default null            comment '结果引用（导出 ZIP 键等）',
  expires_at         datetime(3)     default null            comment '结果到期时刻（导出）',
  sent_at            datetime(3)     default null            comment '提醒实际发送时刻',
  del_flag           char(1)         not null default '0'    comment '删除标记（0存在 2删除）',
  create_time        datetime(3)     default null            comment '创建时间',
  update_time        datetime(3)     default null            comment '更新时间',
  primary key (id),
  unique key uk_preg_task_dedup (dedup_key),
  key idx_preg_task_due (status, next_run_at, id),
  key idx_preg_task_lease (status, lease_until),
  key idx_preg_task_owner (kind, requester_user_id, create_time),
  key idx_preg_task_expire (kind, expires_at)
) engine=innodb auto_increment=1 comment = '孕期-后台任务';

-- ----------------------------
-- 12、孕期角色
-- ----------------------------
insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
                      status, del_flag, create_by, create_time, remark)
select '孕期管理员', 'pregnancy_manager', 20, '1', 1, 1, '0', '0', 'admin', sysdate(),
       '孕期业务全部权限，可管理提醒规则与查看最近发送结果'
from dual
where not exists (select 1 from sys_role where role_key = 'pregnancy_manager');

-- 为内置 admin 账号补齐孕期角色。
insert into sys_user_role (user_id, role_id)
select u.user_id, r.role_id from sys_user u
join sys_role r on r.role_key = 'pregnancy_manager' and r.del_flag = '0'
where u.user_name = 'admin' and u.del_flag = '0'
  and not exists (select 1 from sys_user_role ur where ur.user_id = u.user_id and ur.role_id = r.role_id);

-- ----------------------------
-- 13、参数种子
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark)
select '孕期-末次月经', 'pregnancy.lastMenstrualPeriod', '', 'N', '孕期', 'admin', sysdate(),
       '填写末次月经第一天，格式 YYYY-MM-DD。37 周足月，40 周预产期'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.lastMenstrualPeriod');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark)
select '孕期-提醒规则', 'pregnancy.reminder.rules', '', 'N', '内部', 'admin', sysdate(),
       '受控 JSON：规则列表与 reminderVersion'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.reminder.rules');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark, `sensitive`)
select '孕期-企微机器人凭证', 'pregnancy.wecom.webhookKey', '', 'N', '孕期', 'admin', sysdate(),
       '仅用于孕期提醒。填写企业微信群机器人 Webhook 的 key 参数', 'Y'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.wecom.webhookKey');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark, `sensitive`)
select '孕期-企微通知开关', 'pregnancy.wecom.enabled', '0', 'N', '孕期', 'admin', sysdate(),
       '孕期提醒通道总开关（0 停用，1 启用）', 'N'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.wecom.enabled');

insert into sys_config (config_name, config_key, config_value, config_type, param_type, create_by, create_time, remark, `sensitive`)
select '宝宝 App 发布令牌', 'pregnancy.app.publishToken', '', 'N', '孕期', 'admin', sysdate(),
       '仅用于发布宝宝客户端的 WGT，留空禁用脚本发布', 'Y'
from dual
where not exists (select 1 from sys_config where config_key = 'pregnancy.app.publishToken');
