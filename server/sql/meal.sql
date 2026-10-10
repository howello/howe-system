-- ============================================================
-- meal.sql — 家庭点餐模块脚本（module-meal）
--
-- 内容：七张业务表的建表、后台菜单与三个家庭角色，以及公共菜谱初始/扩展 seed。
--       由原 meal_20260922 / meal_notify_20260929 / meal_seed_20260928 合并而来，
--       其中 accept_user_id 已并入 meal_order 建表（原为独立 ALTER 增量）。
--
-- 七张表：
--   meal_category     菜品分类，dept_id=0 为平台公共分类，否则为该家庭私有分类
--   meal_dish         菜品，dept_id=0 为平台公共菜谱，否则为该家庭私有菜
--   meal_order        订单主表，只有下单时间（没有预约用餐时间）
--   meal_order_item   订单明细，存菜名/封面快照，菜品后续改名或删除都不影响历史订单
--   meal_review       评价，一单一评（order_id 唯一约束）
--   meal_proposal     新菜提案，审核通过时自动往 meal_dish 落一条（source=1）
--   meal_category_ref 菜品/提案与分类的多对多关联，biz_type 区分（1=菜品 2=提案）
--
-- 菜品与分类承载「公共数据 + 家庭私有数据」，因此不走 @DataScope（数据权限只会
-- 拼出本部门，会把 dept_id=0 的公共数据一起过滤掉），隔离条件在 Mapper XML 里手写。
-- 订单 / 评价 / 提案是纯家庭私有数据，list 查询走 @DataScope。
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < meal.sql
-- 依赖：需先执行 base.sql（本脚本会向 sys_menu / sys_role / sys_role_menu 写入数据）。
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」，
-- 并给家庭成员账号分配「点餐员 / 厨师 / 家庭管理员」角色。
-- ============================================================

-- ----------------------------
-- 1、菜品分类
-- ----------------------------
drop table if exists meal_category;
create table meal_category (
  category_id  bigint(20)   not null auto_increment    comment '分类ID',
  dept_id      bigint(20)   default 0                  comment '所属家庭ID（0=公共分类）',
  name         varchar(64)  not null                   comment '分类名称',
  icon         varchar(500) default ''                 comment '分类图标',
  sort         int(4)       default 0                  comment '排序',
  status       char(1)      default '0'                comment '状态（0正常 1停用）',
  del_flag     char(1)      default '0'                comment '删除标记（0存在 2删除）',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime                                comment '创建时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500) default null               comment '备注',
  primary key (category_id),
  key idx_meal_category_dept (dept_id, status)
) engine=innodb auto_increment=1 comment = '点餐-菜品分类';

-- ----------------------------
-- 2、菜品
-- ----------------------------
drop table if exists meal_dish;
create table meal_dish (
  dish_id      bigint(20)   not null auto_increment    comment '菜品ID',
  dept_id      bigint(20)   default 0                  comment '所属家庭ID（0=公共菜谱）',
  name         varchar(128) not null                   comment '菜名',
  cover        varchar(500) default ''                 comment '封面图地址',
  description  varchar(500) default ''                 comment '一句话简介',
  tags         varchar(500) default ''                 comment '标签，逗号分隔',
  duration     varchar(64)  default ''                 comment '耗时，如 90 分钟',
  level        varchar(32)  default ''                 comment '难度',
  serve        varchar(64)  default ''                 comment '份量',
  kcal         varchar(64)  default ''                 comment '热量',
  ingredients  json                                    comment '用料清单 [{name, amount}]',
  steps        json                                    comment '做法步骤 ["...", "..."]',
  tips         json                                    comment '小贴士 ["..."]',
  status       char(1)      default '0'                comment '状态（0上架 1下架）',
  sort         int(4)       default 0                  comment '排序',
  source       char(1)      default '0'                comment '来源（0自建 1新菜提案通过）',
  del_flag     char(1)      default '0'                comment '删除标记（0存在 2删除）',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime                                comment '创建时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500) default null               comment '备注',
  primary key (dish_id),
  key idx_meal_dish_dept (dept_id, status)
) engine=innodb auto_increment=1 comment = '点餐-菜品';

-- ----------------------------
-- 3、订单主表
-- ----------------------------
drop table if exists meal_order;
create table meal_order (
  order_id     bigint(20)   not null auto_increment    comment '订单ID',
  order_no     varchar(32)  not null                   comment '订单号（如 M20260922001）',
  dept_id      bigint(20)                              comment '所属家庭ID',
  user_id      bigint(20)                              comment '点餐人ID',
  user_name    varchar(64)  default ''                 comment '点餐人昵称（冗余，列表直接展示）',
  status       char(1)      default '0'                comment '状态（0待接单 1制作中 2已完成 3已取消）',
  total_count  int(11)      default 0                  comment '菜品总份数',
  order_remark varchar(500) default ''                 comment '整体备注（口味要求等）',
  accept_by      varchar(64)  default ''                 comment '接单人',
  accept_user_id bigint(20)                              comment '接单厨师用户ID（供评价通知定位接收人）',
  accept_time    datetime                                comment '接单时间',
  finish_by    varchar(64)  default ''                 comment '完成人',
  finish_time  datetime                                comment '完成时间',
  cancel_time  datetime                                comment '取消时间',
  del_flag     char(1)      default '0'                comment '删除标记（0存在 2删除）',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime                                comment '下单时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500) default null               comment '备注（BaseEntity 通用字段）',
  primary key (order_id),
  unique key uk_meal_order_no (order_no),
  key idx_meal_order_dept (dept_id, status, create_time),
  key idx_meal_order_user (user_id, status, create_time)
) engine=innodb auto_increment=1 comment = '点餐-订单';

-- ----------------------------
-- 4、订单明细（菜品快照）
-- ----------------------------
drop table if exists meal_order_item;
create table meal_order_item (
  item_id      bigint(20)   not null auto_increment    comment '明细ID',
  order_id     bigint(20)   not null                   comment '订单ID',
  dish_id      bigint(20)                              comment '菜品ID',
  dish_name    varchar(128) not null                   comment '菜名快照',
  dish_cover   varchar(500) default ''                 comment '封面快照',
  count        int(11)      default 1                  comment '份数',
  remark       varchar(255) default ''                 comment '单项备注（如不要辣）',
  primary key (item_id),
  key idx_meal_order_item_order (order_id)
) engine=innodb auto_increment=1 comment = '点餐-订单明细';

-- ----------------------------
-- 5、评价（一单一评）
-- ----------------------------
drop table if exists meal_review;
create table meal_review (
  review_id    bigint(20)    not null auto_increment   comment '评价ID',
  order_id     bigint(20)                              comment '订单ID',
  dept_id      bigint(20)                              comment '所属家庭ID',
  user_id      bigint(20)                              comment '评价人ID',
  user_name    varchar(64)   default ''                comment '评价人昵称',
  score        tinyint(4)    default 5                 comment '总体评分（1-5）',
  content      varchar(1000) default ''                comment '评价内容',
  images       json                                    comment '图片地址列表',
  anonymous    char(1)       default '0'               comment '是否匿名（0否 1是）',
  del_flag     char(1)       default '0'               comment '删除标记（0存在 2删除）',
  create_by    varchar(64)   default ''                comment '创建者',
  create_time  datetime                                comment '创建时间',
  update_by    varchar(64)   default ''                comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500)  default null              comment '备注',
  primary key (review_id),
  unique key uk_meal_review_order (order_id),
  key idx_meal_review_dept (dept_id, create_time)
) engine=innodb auto_increment=1 comment = '点餐-评价';

-- ----------------------------
-- 6、新菜提案
-- ----------------------------
drop table if exists meal_proposal;
create table meal_proposal (
  proposal_id   bigint(20)   not null auto_increment   comment '提案ID',
  dept_id       bigint(20)                             comment '所属家庭ID',
  user_id       bigint(20)                             comment '提交人ID',
  user_name     varchar(64)  default ''                comment '提交人昵称',
  name          varchar(128) not null                  comment '菜名',
  description   varchar(500) default ''                comment '菜品介绍',
  image         varchar(500) default ''                comment '参考图',
  reason        varchar(500) default ''                comment '想吃的理由',
  status        char(1)      default '0'               comment '状态（0待审核 1已通过 2已驳回）',
  audit_by      varchar(64)  default ''                comment '审核人',
  audit_time    datetime                               comment '审核时间',
  audit_remark  varchar(500) default ''                comment '审核意见',
  dish_id       bigint(20)                             comment '通过后生成的菜品ID',
  del_flag      char(1)      default '0'               comment '删除标记（0存在 2删除）',
  create_by     varchar(64)  default ''                comment '创建者',
  create_time   datetime                               comment '创建时间',
  update_by     varchar(64)  default ''                comment '更新者',
  update_time   datetime                               comment '更新时间',
  remark        varchar(500) default null              comment '备注',
  primary key (proposal_id),
  key idx_meal_proposal_dept (dept_id, status),
  key idx_meal_proposal_user (user_id, status)
) engine=innodb auto_increment=1 comment = '点餐-新菜提案';

-- ----------------------------
-- 7、菜品分类关联（菜品/提案共用一张多态关联表）
--
-- 一道菜 / 一条提案都可挂多个分类，用 biz_type 区分业务：1=菜品(meal_dish) 2=提案(meal_proposal)。
-- 因此 meal_dish / meal_proposal 均不再有 category_id 列，分类归属统一存本表。
-- ----------------------------
drop table if exists meal_category_ref;
create table meal_category_ref (
  biz_type     tinyint(4)   not null                   comment '业务类型（1=菜品 2=提案）',
  biz_id       bigint(20)   not null                   comment '业务ID（dish_id 或 proposal_id）',
  category_id  bigint(20)   not null                   comment '分类ID',
  primary key (biz_type, biz_id, category_id),
  key idx_meal_category_ref_category (category_id)
) engine=innodb comment = '点餐-分类关联（菜品/提案共用）';

-- ----------------------------
-- 8、后台菜单
--
-- 不硬编码 menu_id：sys_menu 的 auto_increment 与既有数据无关，写死会撞车。
-- 先按名称捕获或新建「点餐管理」目录，再用 LAST_INSERT_ID() 逐级捕获新建菜单的 ID。
-- 「家庭成员」不建菜单：家庭=部门、成员=用户，直接用「系统管理 > 部门管理 / 用户管理」维护。
-- ----------------------------
delete from sys_menu where perms like 'meal:%' or (menu_name = '点餐管理' and parent_id = 0);

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('点餐管理', 0, '6', 'meal', null, 1, 0, 'M', '0', '0', '', 'shopping', 'admin', sysdate(), '家庭点餐目录');

select @mealDirId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('菜品管理', @mealDirId, '1', 'dish', 'meal/dish/index', 1, 0, 'C', '0', '0', 'meal:dish:list', 'component', 'admin', sysdate(), '菜品管理菜单');

select @mealDishMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分类管理', @mealDirId, '2', 'category', 'meal/category/index', 1, 0, 'C', '0', '0', 'meal:category:list', 'tree', 'admin', sysdate(), '分类管理菜单');

select @mealCategoryMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('订单管理', @mealDirId, '3', 'order', 'meal/order/index', 1, 0, 'C', '0', '0', 'meal:order:list', 'list', 'admin', sysdate(), '订单管理菜单');

select @mealOrderMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('评价管理', @mealDirId, '4', 'review', 'meal/review/index', 1, 0, 'C', '0', '0', 'meal:review:list', 'star', 'admin', sysdate(), '评价管理菜单');

select @mealReviewMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('新菜审核', @mealDirId, '5', 'proposal', 'meal/proposal/index', 1, 0, 'C', '0', '0', 'meal:proposal:list', 'edit', 'admin', sysdate(), '新菜审核菜单');

select @mealProposalMenuId := LAST_INSERT_ID();

-- 菜品按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('菜品查询', @mealDishMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'meal:dish:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('菜品新增', @mealDishMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'meal:dish:add', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('菜品修改', @mealDishMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'meal:dish:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('菜品删除', @mealDishMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'meal:dish:remove', '#', 'admin', sysdate(), '');

-- 分类按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分类查询', @mealCategoryMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'meal:category:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分类新增', @mealCategoryMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'meal:category:add', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分类修改', @mealCategoryMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'meal:category:edit', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('分类删除', @mealCategoryMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'meal:category:remove', '#', 'admin', sysdate(), '');

-- 订单按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('订单查询', @mealOrderMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'meal:order:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('订单删除', @mealOrderMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'meal:order:remove', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('订单接单', @mealOrderMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'meal:order:dispatch', '#', 'admin', sysdate(), '厨师接单权限，点餐端厨师工作台也用它');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('订单完成', @mealOrderMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'meal:order:finish', '#', 'admin', sysdate(), '厨师标记完成权限，点餐端厨师工作台也用它');

-- 评价按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('评价查询', @mealReviewMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'meal:review:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('评价删除', @mealReviewMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'meal:review:remove', '#', 'admin', sysdate(), '');

-- 提案按钮
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('提案查询', @mealProposalMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'meal:proposal:query', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('提案审核', @mealProposalMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'meal:proposal:audit', '#', 'admin', sysdate(), '通过时自动生成菜品');
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values ('提案删除', @mealProposalMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'meal:proposal:remove', '#', 'admin', sysdate(), '');

-- ----------------------------
-- 9、三个家庭角色
--
-- data_scope 一律用 '3'（本部门数据权限）：订单/评价/提案的 list 查询靠它隔离到本家庭。
-- role_key 是点餐端判断「是不是厨师」的依据（/getInfo 返回的 roles 里带 meal_chef）。
-- ----------------------------
delete from sys_role_menu where role_id in (select role_id from sys_role where role_key in ('meal_eater', 'meal_chef', 'meal_manager'));
delete from sys_role where role_key in ('meal_eater', 'meal_chef', 'meal_manager');

insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
values ('点餐员', 'meal_eater', 10, '3', 1, 1, '0', '0', 'admin', sysdate(), '家庭成员默认角色：浏览菜单、下单、评价、提交新菜');

select @eaterRoleId := LAST_INSERT_ID();

insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
values ('厨师', 'meal_chef', 11, '3', 1, 1, '0', '0', 'admin', sysdate(), '接单、标记完成，并维护本家庭的菜品与分类');

select @chefRoleId := LAST_INSERT_ID();

insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
values ('家庭管理员', 'meal_manager', 12, '3', 1, 1, '0', '0', 'admin', sysdate(), '家庭全部权限：新菜审核、菜品分类订单评价管理');

select @managerRoleId := LAST_INSERT_ID();

-- 点餐员不配后台菜单：只在家里手机端点餐，不进管理端。
-- 厨师：菜品与分类的完整维护权 + 订单的接单与完成。
insert into sys_role_menu (role_id, menu_id) values (@chefRoleId, @mealDirId);
insert into sys_role_menu (role_id, menu_id)
select @chefRoleId, menu_id from sys_menu
where perms in ('meal:dish:list', 'meal:dish:query', 'meal:dish:add', 'meal:dish:edit', 'meal:dish:remove',
                'meal:category:list', 'meal:category:query', 'meal:category:add', 'meal:category:edit', 'meal:category:remove',
                'meal:order:list', 'meal:order:query', 'meal:order:dispatch', 'meal:order:finish');

-- 家庭管理员：点餐管理目录下的全部菜单与按钮。
insert into sys_role_menu (role_id, menu_id) values (@managerRoleId, @mealDirId);
insert into sys_role_menu (role_id, menu_id)
select @managerRoleId, menu_id from sys_menu where perms like 'meal:%';

-- ----------------------------
-- 10、公共菜谱分类（dept_id = 0，所有家庭可见）
--
-- 19 个公共分类，按「类型（荤菜/半荤半素/素菜）+ 食材（肉/鱼/蛋/蔬菜/豆制品）+
-- 做法（炒/炖/炸/煮/蒸/烤/凉拌/卤/汤）+ 热菜 + 地方口味（岷县）」组织。
-- 分类是多对多：一道菜同时属于多个分类，归属见 11 节的 meal_category_ref。
-- ----------------------------
insert into meal_category (dept_id, name, icon, sort, status, del_flag, create_by, create_time, remark)
values
(0, '热菜',     '🔥',  1, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '岷县',     '🏔️',  2, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '荤菜',     '🍖',  3, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '半荤半素', '🥘',  4, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '素菜',     '🥬',  5, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '肉',       '🥩',  6, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '鱼',       '🐟',  7, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '蛋',       '🥚',  8, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '蔬菜',     '🥦',  9, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '豆制品',   '🫘', 10, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '炒',       '🍳', 11, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '炖',       '🍲', 12, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '炸',       '🍤', 13, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '煮',       '🍜', 14, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '蒸',       '♨️', 15, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '烤',       '🍢', 16, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '凉拌',     '🥗', 17, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '卤',       '🍗', 18, '0', '0', 'admin', sysdate(), '公共分类'),
(0, '汤',       '🥣', 19, '0', '0', 'admin', sysdate(), '公共分类');

-- ----------------------------
-- 11、公共菜谱（dept_id = 0，所有家庭可见）
--
-- 42 道完整菜谱（含封面/简介/标签/耗时/难度/份量/热量/用料/做法/小贴士）。
-- 分类归属统一在文末写入 meal_category_ref；本脚本 drop 重建，重复执行即重置为初始数据。
-- ----------------------------
insert into meal_dish (dept_id, name, cover, description, tags, duration, level, serve, kcal, ingredients, steps, tips, status, sort, source, del_flag, create_by, create_time, update_by, update_time, remark)
values
(0,'酸辣白菜','https://img.wyantao.com/img/ai/2026/10/09/ab89c178bcd5405f8e931d0721561261.png','酸辣开胃的家常白菜菜肴，口感脆嫩，酸爽微辣，下饭解腻。','家常菜,快手菜,酸辣口味,素菜','15分钟','简单','2人份','每份约120千卡','[{"name": "白菜", "amount": "500克"}, {"name": "干辣椒", "amount": "3个"}, {"name": "蒜", "amount": "3瓣"}, {"name": "香醋", "amount": "2汤匙"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "白糖", "amount": "1茶匙"}, {"name": "盐", "amount": "适量"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "淀粉", "amount": "半茶匙（可选）"}]','["白菜洗净，菜帮和菜叶分开切片；蒜切末，干辣椒剪段备用。", "碗中加入香醋、生抽、白糖、盐和少量淀粉，调成酸辣汁备用。", "锅中倒油烧热，放入干辣椒和蒜末炒出香味。", "先加入白菜帮大火翻炒1分钟，再加入白菜叶继续翻炒至变软。", "倒入调好的酸辣汁，快速翻炒均匀，使白菜入味。", "炒至汤汁略收即可关火，装盘食用。"]','["白菜帮较厚，先炒能保证菜帮和菜叶熟度一致。", "喜欢更酸辣的口感可增加醋和辣椒用量。", "全程大火快炒可保持白菜爽脆口感。"]','0',1,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'清蒸鲈鱼','https://img.wyantao.com/img/ai/2026/10/09/3f28f9f6cda94650899fb2de6d5623b1.png','鲜嫩鲈鱼搭配葱姜清蒸，突出鱼肉原汁原味，家常宴客皆宜。','家常菜,蒸菜,清淡,海鲜','20分钟','简单','2人份','180千卡/份','[{"name": "鲈鱼", "amount": "1条（约600克）"}, {"name": "姜", "amount": "10克"}, {"name": "葱", "amount": "2根"}, {"name": "蒸鱼豉油", "amount": "2汤匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "食用油", "amount": "1汤匙"}, {"name": "盐", "amount": "2克"}]','["鲈鱼去鳞去内脏洗净，在鱼身两侧划几刀，加入盐和料酒腌制10分钟。", "姜切片，葱一部分切段一部分切丝，将姜片和葱段放入鱼腹及鱼身下方。", "锅中水烧开后放入鲈鱼，大火蒸8至10分钟，至鱼肉熟透。", "取出蒸好的鲈鱼，倒掉盘中腥水，去掉姜片和葱段。", "铺上葱丝，淋入蒸鱼豉油，烧热食用油后浇在葱丝和鱼身上即可。"]','["蒸鱼要水开后入锅，鱼肉更嫩。", "蒸制时间根据鱼的大小调整，避免过久影响口感。", "蒸鱼豉油含盐，腌鱼时盐不宜放多。"]','0',2,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'红烧茄子','https://img.wyantao.com/img/ai/2026/10/09/bfee07d3455f4ae3b2bb65ff9afa3aff.png','家常经典茄子做法，酱香浓郁，软糯入味，下饭美味。','家常菜,下饭菜,红烧,素菜','30分钟','简单','2人份','180千卡/份','[{"name": "茄子", "amount": "500克"}, {"name": "大蒜", "amount": "3瓣"}, {"name": "葱", "amount": "1根"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "半汤匙"}, {"name": "蚝油", "amount": "1汤匙"}, {"name": "白糖", "amount": "1茶匙"}, {"name": "盐", "amount": "适量"}, {"name": "淀粉", "amount": "1汤匙"}, {"name": "食用油", "amount": "适量"}]','["茄子洗净切成滚刀块，撒少许盐腌10分钟，挤去多余水分。", "茄子表面撒少量淀粉拌匀，锅中放油，将茄子煎至表面微黄变软，盛出备用。", "锅留少许底油，放入蒜末炒香，加入生抽、老抽、蚝油、白糖和适量清水调成红烧汁。", "倒入煎好的茄子，小火焖煮5分钟，让茄子充分吸收酱汁。", "大火收汁，撒上葱花即可出锅。"]','["茄子提前腌制可减少吸油，口感更软嫩。", "煎茄子时不要频繁翻动，避免碎裂。", "喜欢更浓郁口味可加入少量豆瓣酱提香。"]','0',3,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'锅包肉','https://img.wyantao.com/img/ai/2026/10/09/14f21f97b48749a3a20e2e505d2ca294.png','东北经典酸甜菜，外酥里嫩，裹上糖醋汁风味浓郁。','东北菜,家常菜,酸甜口,下饭菜','40分钟','中等','2人份','620千卡/份','[{"name": "猪里脊肉", "amount": "300克"}, {"name": "土豆淀粉", "amount": "100克"}, {"name": "胡萝卜", "amount": "30克"}, {"name": "大葱", "amount": "30克"}, {"name": "姜丝", "amount": "10克"}, {"name": "蒜片", "amount": "10克"}, {"name": "食用油", "amount": "适量"}, {"name": "盐", "amount": "3克"}, {"name": "料酒", "amount": "10毫升"}, {"name": "白糖", "amount": "25克"}, {"name": "白醋", "amount": "30毫升"}, {"name": "生抽", "amount": "10毫升"}]','["猪里脊切成薄片，用盐和料酒抓匀，腌制10分钟。", "土豆淀粉加适量清水调成浓稠糊状，放入肉片均匀挂糊。", "锅中倒油烧至六成热，下肉片炸至定型后捞出。", "升高油温，将肉片复炸至金黄酥脆，捞出控油。", "锅中留少量油，加入姜丝、蒜片、葱丝和胡萝卜丝炒香。", "加入白糖、白醋、生抽调成酸甜汁，快速翻炒至融合。", "倒入炸好的肉片，大火快速翻匀，让肉片均匀裹上酱汁即可。"]','["淀粉糊要浓稠，炸出的肉片才更酥脆。", "复炸能提升外皮脆度，避免回软。", "裹汁时间不要过长，保持锅包肉口感。"]','0',4,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'小炒黄牛肉','https://img.wyantao.com/img/ai/2026/10/09/df80ba96a0884a6ea443e5cd6d998f49.png','湘味经典家常小炒，牛肉嫩滑，香辣开胃，下饭十足。','湘菜,家常菜,下饭菜,香辣','30分钟','中等','2人份','480千卡/份','[{"name": "黄牛肉", "amount": "300克"}, {"name": "小米椒", "amount": "8个"}, {"name": "青椒", "amount": "2个"}, {"name": "香菜", "amount": "1小把"}, {"name": "蒜", "amount": "4瓣"}, {"name": "姜", "amount": "10克"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "蚝油", "amount": "1汤匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "淀粉", "amount": "1茶匙"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "盐", "amount": "适量"}, {"name": "白糖", "amount": "少许"}]','["黄牛肉逆着纹理切薄片，加入料酒、生抽、淀粉和少许油抓匀，腌制10分钟。", "小米椒、青椒切段，姜蒜切末，香菜洗净切段备用。", "锅烧热后放油，倒入牛肉快速滑炒至变色，盛出备用。", "锅中留少许底油，加入姜蒜、小米椒和青椒炒出香味。", "倒回牛肉大火翻炒，加入蚝油、少许盐和白糖调味。", "放入香菜段快速翻匀即可出锅。"]','["牛肉要逆纹切，口感更嫩。", "炒牛肉时保持大火，时间不宜过长。", "腌肉时加入少许油可帮助锁住水分。"]','0',5,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'洋芋拌汤','https://img.wyantao.com/img/ai/2026/10/09/4c613f524e2e46e5b7c9cabba9136065.png','陕西家常面食，土豆与面糊同煮，汤稠味香，暖胃易做。','家常菜,陕西风味,快手汤食,暖胃','30分钟','简单','2人份','每份约320千卡','[{"name": "土豆", "amount": "200克"}, {"name": "面粉", "amount": "80克"}, {"name": "西红柿", "amount": "1个（约150克）"}, {"name": "小葱", "amount": "2根"}, {"name": "蒜", "amount": "2瓣"}, {"name": "食用油", "amount": "15克"}, {"name": "盐", "amount": "4克"}, {"name": "生抽", "amount": "10毫升"}, {"name": "醋", "amount": "5毫升"}, {"name": "清水", "amount": "800毫升"}]','["土豆去皮切小丁，西红柿切块，小葱切葱花，蒜切末备用。", "面粉加少量清水搅拌成均匀稠面糊，静置几分钟。", "锅中倒油，放入蒜末和葱白炒香，加入西红柿翻炒出汁。", "加入土豆丁翻炒片刻，倒入清水煮开，煮至土豆变软。", "将面糊慢慢倒入锅中，同时用筷子或勺子搅拌，防止结块。", "继续煮5分钟至汤变浓稠，加入盐、生抽和醋调味。", "撒入葱花后关火，盛出即可食用。"]','["面糊要慢慢加入并持续搅拌，汤口感更细腻。", "土豆切小块更容易煮熟，喜欢浓稠口感可适当增加面粉。", "出锅前加少许醋可提升风味。"]','0',6,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱（岷县菜）'),
(0,'炒洋芋片','https://img.wyantao.com/img/ai/2026/10/09/efdb970c16d54ef7b6c9edcec2fb219d.png','家常快手炒洋芋片，土豆片爽脆入味，简单下饭。','家常菜,快手菜,素菜,下饭菜','20分钟','简单','2人份','180千卡/份','[{"name": "土豆", "amount": "2个（约400克）"}, {"name": "青椒", "amount": "1个"}, {"name": "大蒜", "amount": "3瓣"}, {"name": "食用油", "amount": "20克"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "醋", "amount": "1汤匙"}, {"name": "盐", "amount": "3克"}, {"name": "白糖", "amount": "2克"}]','["土豆去皮洗净，切成薄片，用清水浸泡10分钟，洗去多余淀粉后沥干。", "青椒切片，大蒜切末备用。", "锅中倒油烧热，放入蒜末炒香。", "加入土豆片大火翻炒，炒至断生。", "加入青椒、生抽、醋、盐和白糖，继续翻炒2分钟。", "土豆片熟透且保持微脆时即可出锅。"]','["土豆片提前浸泡可减少淀粉，炒出来更爽脆。", "喜欢酸辣口味可加入干辣椒或适量辣椒油。"]','0',7,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'炒洋芋条','https://img.wyantao.com/img/ai/2026/10/09/38e4bb8d6e8c40bb93dc029ae017b698.png','家常炒洋芋条，外香内软，简单快手，适合作为下饭菜。','家常菜,快手菜,素菜,下饭菜','20分钟','简单','2人份','260千卡/份','[{"name": "土豆", "amount": "300克"}, {"name": "食用油", "amount": "20克"}, {"name": "大蒜", "amount": "3瓣"}, {"name": "干辣椒", "amount": "3个"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "盐", "amount": "2克"}, {"name": "鸡精", "amount": "1克"}, {"name": "葱花", "amount": "适量"}]','["土豆去皮洗净，切成粗细均匀的条状，用清水浸泡10分钟去除部分淀粉。", "锅中烧水，放入土豆条焯水1至2分钟，捞出沥干备用。", "热锅倒油，放入蒜片和干辣椒炒香。", "加入土豆条翻炒至表面微黄，倒入生抽调味。", "加入盐和鸡精继续翻炒均匀，撒上葱花即可出锅。"]','["土豆条焯水后更容易炒熟，也能减少粘锅。", "喜欢焦香口感可多炒一会儿，让表面略微金黄。"]','0',8,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'面片','https://img.wyantao.com/img/ai/2026/10/09/8edf5e2e60004a8e842df927762348ef.png','家常面片汤，面片筋道，搭配蔬菜肉类，暖胃又易做。','家常菜,面食,汤菜,快手,北方风味','30分钟','简单','2人份','420千卡/每份','[{"name": "面粉", "amount": "200克"}, {"name": "清水", "amount": "100克"}, {"name": "西红柿", "amount": "1个（约150克）"}, {"name": "鸡蛋", "amount": "2个"}, {"name": "青菜", "amount": "100克"}, {"name": "葱", "amount": "10克"}, {"name": "食用油", "amount": "15克"}, {"name": "盐", "amount": "5克"}, {"name": "生抽", "amount": "10克"}, {"name": "香油", "amount": "5克"}]','["面粉中分次加入清水，揉成光滑面团，盖好醒面20分钟。", "将面团擀薄，切成小块或用手揪成大小均匀的面片。", "西红柿切块，青菜洗净切段，葱切末备用。", "锅中加油烧热，放葱炒香，加入西红柿翻炒出汁，倒入适量清水煮开。", "放入面片煮至浮起熟透，加入青菜和鸡蛋液搅散。", "加入盐、生抽调味，淋入香油即可出锅。"]','["面团稍硬一些，做出的面片更有嚼劲。", "面片下锅后要及时搅动，避免粘连。", "可根据口味加入肉末、虾皮或其他蔬菜。"]','0',9,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'板栗鸡','https://img.wyantao.com/img/ai/2026/10/09/736c8901dfd64175af91987f706182a6.png','板栗香甜软糯，鸡肉鲜嫩入味，是一道家常滋补下饭菜。','家常菜,下饭菜,秋季菜,荤菜','约50分钟','中等','2人份','520千卡/份','[{"name": "鸡腿肉", "amount": "500克"}, {"name": "板栗仁", "amount": "200克"}, {"name": "生姜", "amount": "10克"}, {"name": "大葱", "amount": "1根"}, {"name": "蒜瓣", "amount": "4瓣"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "1汤匙"}, {"name": "料酒", "amount": "2汤匙"}, {"name": "冰糖", "amount": "10克"}, {"name": "食用油", "amount": "适量"}, {"name": "盐", "amount": "适量"}, {"name": "清水", "amount": "适量"}]','["鸡腿肉洗净切块，加入料酒和少许盐腌制10分钟；板栗仁洗净备用。", "锅中倒油烧热，放入鸡块煸炒至表面微黄，加入姜片、葱段、蒜瓣炒香。", "加入生抽、老抽和冰糖翻炒均匀，使鸡肉上色。", "倒入清水没过鸡肉，大火煮开后转小火炖煮25分钟。", "加入板栗仁继续炖煮15分钟，至鸡肉软烂、板栗入味。", "根据口味加盐调味，大火收汁后即可装盘。"]','["使用鸡腿肉口感更嫩，也可换成整鸡块。", "板栗提前煮熟或去皮更省时。", "收汁时注意翻动，避免板栗粘锅。"]','0',10,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'牛排','https://img.wyantao.com/img/ai/2026/10/09/68304189cf8a475a90da7d2d49e2bf80.png','香煎牛排，外焦里嫩，搭配黑胡椒调味，适合家庭快速制作。','西餐,家常菜,煎制,高蛋白','30分钟','简单','2人份','520千卡/份','[{"name": "牛排", "amount": "2块（约400克）"}, {"name": "黑胡椒碎", "amount": "适量"}, {"name": "海盐", "amount": "适量"}, {"name": "橄榄油", "amount": "1汤匙"}, {"name": "黄油", "amount": "20克"}, {"name": "大蒜", "amount": "3瓣"}, {"name": "迷迭香", "amount": "1小枝"}, {"name": "西兰花", "amount": "100克"}, {"name": "小番茄", "amount": "6个"}]','["牛排提前20分钟从冰箱取出回温，用厨房纸吸干表面水分，两面撒上海盐和黑胡椒碎。", "平底锅烧热，加入橄榄油，放入牛排，中大火煎至一面上色后翻面。", "加入黄油、大蒜和迷迭香，小火继续煎制，并用融化的黄油反复淋在牛排表面。", "根据个人喜好煎至三分熟、五分熟或全熟，取出静置5分钟。", "西兰花焯水后与小番茄搭配装盘，将牛排切片或整块摆盘即可。"]','["牛排下锅前擦干水分，更容易形成漂亮焦香表面。", "煎好后静置几分钟能让肉汁更均匀，口感更嫩。", "不要频繁翻动牛排，以免影响上色。"]','0',11,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'青椒炒鸡蛋','https://img.wyantao.com/img/ai/2026/10/09/d2d9c45944b74ff8aecc2794395948c7.png','青椒清香爽脆，搭配嫩滑鸡蛋，家常快手下饭菜。','家常菜,快手菜,下饭菜,炒菜','15分钟','简单','2人份','260千卡/份','[{"name": "鸡蛋", "amount": "3个"}, {"name": "青椒", "amount": "200克"}, {"name": "食用油", "amount": "20毫升"}, {"name": "盐", "amount": "3克"}, {"name": "生抽", "amount": "10毫升"}, {"name": "蒜", "amount": "2瓣"}]','["青椒洗净去籽切丝，蒜切片；鸡蛋打入碗中，加少许盐搅散。", "锅烧热后加入一半食用油，倒入蛋液炒至凝固嫩熟，盛出备用。", "锅中加入剩余食用油，放入蒜片炒香，加入青椒丝翻炒至断生。", "加入炒好的鸡蛋，放入盐和生抽，快速翻炒均匀即可出锅。"]','["鸡蛋不要炒得太老，保持嫩滑口感。", "青椒炒至断生即可，能保留清香和脆感。", "喜欢微辣口感可选用辣味青椒。"]','0',12,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'红烧肉','https://img.wyantao.com/img/ai/2026/10/09/86f4b789c58f45ca81d281ccdef3e7b9.png','经典家常菜，肥而不腻，酱香浓郁，入口软糯。','家常菜,下饭菜,红烧,肉类','约90分钟','中等','2人份','每份约680千卡','[{"name": "五花肉", "amount": "500克"}, {"name": "冰糖", "amount": "30克"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "1汤匙"}, {"name": "料酒", "amount": "2汤匙"}, {"name": "姜", "amount": "5片"}, {"name": "葱", "amount": "2根"}, {"name": "八角", "amount": "2个"}, {"name": "桂皮", "amount": "1小段"}, {"name": "食用油", "amount": "1汤匙"}, {"name": "盐", "amount": "适量"}, {"name": "清水", "amount": "适量"}]','["五花肉洗净切成约3厘米见方的块，冷水下锅焯水，捞出沥干。", "锅中放少量油，加入冰糖小火炒至融化并呈焦糖色。", "倒入五花肉翻炒上色，加入姜片、葱段、八角和桂皮炒香。", "加入料酒、生抽、老抽翻炒均匀，倒入适量热水没过肉块。", "大火烧开后转小火炖约60分钟，至肉质软烂入味。", "开大火收浓汤汁，根据口味加盐调味，盛出即可。"]','["五花肉先焯水可去腥，炖煮时用热水口感更嫩。", "炒糖色需小火操作，避免冰糖炒糊发苦。", "收汁时勤观察，保留少量浓稠汤汁更下饭。"]','0',13,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'西红柿炒鸡蛋','https://img.wyantao.com/img/ai/2026/10/09/92e50a0fa0ff46cd889299ff8ce17235.png','经典家常快手菜，酸甜开胃，鸡蛋嫩滑，番茄鲜香。','家常菜,快手菜,下饭菜','15分钟','简单','2人份','每份约220千卡','[{"name": "西红柿", "amount": "2个（约400克）"}, {"name": "鸡蛋", "amount": "3个"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "盐", "amount": "3克"}, {"name": "白糖", "amount": "5克"}, {"name": "葱花", "amount": "适量"}]','["西红柿洗净切块，鸡蛋打入碗中，加少许盐搅散备用。", "锅烧热后加入1汤匙食用油，倒入鸡蛋液，炒至凝固后盛出。", "锅中再加1汤匙食用油，放入西红柿块翻炒至出汁。", "加入盐和白糖调味，倒回炒好的鸡蛋，翻炒均匀。", "撒上葱花即可出锅装盘。"]','["鸡蛋炒至刚凝固即可盛出，口感更嫩。", "西红柿炒出汁后再放鸡蛋，更容易入味。", "喜欢汤汁丰富的口感可适当延长西红柿炒制时间。"]','0',14,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'西红柿鸡蛋汤','https://img.wyantao.com/img/ai/2026/10/09/fb6224b881164f429da33003eb35e63f.png','经典家常汤品，酸甜鲜美，口感清爽，营养简单易做。','家常菜,快手汤,清淡,补充蛋白质','15分钟','简单','2人份','每份约120千卡','[{"name": "西红柿", "amount": "2个（约300克）"}, {"name": "鸡蛋", "amount": "2个"}, {"name": "小葱", "amount": "1根"}, {"name": "食用油", "amount": "1汤匙"}, {"name": "盐", "amount": "适量（约3克）"}, {"name": "白胡椒粉", "amount": "少许"}, {"name": "香油", "amount": "几滴"}, {"name": "清水", "amount": "600毫升"}]','["西红柿洗净切块，鸡蛋打入碗中搅散，小葱切葱花备用。", "锅中加入食用油，放入西红柿翻炒至出汁。", "加入清水，大火煮开后转中火煮3至5分钟，让汤汁更鲜。", "将鸡蛋液慢慢倒入锅中，用筷子轻轻搅散成蛋花。", "加入盐、白胡椒粉调味，关火后淋入香油，撒上葱花即可。"]','["西红柿先炒出汁，汤味会更浓郁。", "蛋液倒入时保持小火，蛋花更嫩。", "可根据口味加入少量生抽或香菜提鲜。"]','0',15,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'紫菜蛋花汤','https://img.wyantao.com/img/ai/2026/10/09/9d47dbe25fd54265b489b06ae339c872.png','清淡鲜美的家常汤品，紫菜与蛋花相结合，营养快手。','家常菜,快手汤,清淡,补水','10分钟','简单','2人份','每份80千卡','[{"name": "紫菜", "amount": "5克"}, {"name": "鸡蛋", "amount": "2个"}, {"name": "清水", "amount": "500毫升"}, {"name": "小葱", "amount": "1根"}, {"name": "香油", "amount": "5毫升"}, {"name": "盐", "amount": "3克"}, {"name": "生抽", "amount": "5毫升"}, {"name": "白胡椒粉", "amount": "1克"}]','["紫菜提前用清水稍微冲洗，沥干备用；鸡蛋打入碗中搅散，小葱切葱花。", "锅中加入清水烧开，放入紫菜煮约1分钟。", "加入盐、生抽和白胡椒粉调味，保持汤水微沸。", "将蛋液沿锅边缓慢倒入，同时用筷子轻轻搅动形成蛋花。", "关火后加入香油，撒上葱花即可盛出。"]','["蛋液要慢慢倒入，蛋花会更加细嫩。", "紫菜不宜久煮，避免影响口感和鲜味。", "可根据口味加入虾皮增加鲜味。"]','0',16,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'淋汁上海青','https://img.wyantao.com/img/ai/2026/10/09/70afa7d75e0b4f3fbfd73148878cd0d7.png','上海青鲜嫩爽口，淋上调味汁，清淡鲜香，适合家常快手小菜。','家常菜,素菜,快手菜,清淡口味','15分钟','简单','2人份','80千卡/每份','[{"name": "上海青", "amount": "300克"}, {"name": "蒜末", "amount": "10克"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "蚝油", "amount": "1汤匙"}, {"name": "白糖", "amount": "少许"}, {"name": "香油", "amount": "1茶匙"}, {"name": "食用油", "amount": "1汤匙"}, {"name": "清水", "amount": "2汤匙"}]','["上海青去掉老叶，洗净后从根部切开，沥干水分。", "锅中加足量清水，放少许盐和油，水开后放入上海青焯烫1分钟左右，捞出沥水摆盘。", "碗中加入生抽、蚝油、白糖、清水和香油，搅拌成调味汁。", "锅中倒入食用油烧热，放入蒜末炒香，加入调味汁煮开。", "将热好的调味汁均匀淋在上海青上即可食用。"]','["焯青菜时加少量油可保持颜色翠绿。", "上海青不要焯太久，避免口感变软。", "调味汁可根据口味增加辣椒或减少蚝油。"]','0',17,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'韭菜炒鸡蛋','https://img.wyantao.com/img/ai/2026/10/09/9f341de4f2394bf7a3f48148af32daab.png','经典家常快手菜，韭菜清香搭配嫩滑鸡蛋，鲜香下饭。','家常菜,快手菜,下饭菜,炒菜','10分钟','简单','2人份','180千卡/份','[{"name": "韭菜", "amount": "200克"}, {"name": "鸡蛋", "amount": "3个"}, {"name": "食用油", "amount": "30克"}, {"name": "盐", "amount": "3克"}, {"name": "料酒", "amount": "5毫升"}]','["韭菜择洗干净，沥干水分后切成约3厘米长的小段。", "鸡蛋打入碗中，加入少许盐和料酒搅匀备用。", "锅中倒入一半食用油烧热，倒入蛋液，炒至凝固后盛出。", "锅中加入剩余食用油，放入韭菜大火快速翻炒至变软。", "倒入炒好的鸡蛋，加盐调味，翻炒均匀即可出锅。"]','["韭菜易出水，清洗后要充分沥干。", "鸡蛋炒至刚凝固即可，口感更嫩。", "全程大火快炒可保持韭菜香味。"]','0',18,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'煮猪蹄','https://img.wyantao.com/img/ai/2026/10/09/8da3fbdf04c648deae5a38f30f20347c.png','猪蹄炖煮入味，肉质软糯，汤汁浓香，是家常滋补菜肴。','家常菜,炖菜,下饭菜,滋补','约120分钟','中等','2人份','约680千卡/份','[{"name": "猪蹄", "amount": "1000克"}, {"name": "生姜", "amount": "20克"}, {"name": "大葱", "amount": "1根"}, {"name": "料酒", "amount": "2汤匙"}, {"name": "生抽", "amount": "3汤匙"}, {"name": "老抽", "amount": "1汤匙"}, {"name": "冰糖", "amount": "20克"}, {"name": "八角", "amount": "2个"}, {"name": "桂皮", "amount": "1小块"}, {"name": "香叶", "amount": "2片"}, {"name": "盐", "amount": "适量"}, {"name": "食用油", "amount": "适量"}]','["猪蹄剁成小块，洗净后放入冷水锅中，加入姜片和料酒，大火煮开后撇去浮沫，捞出冲洗干净。", "锅中加入少许油，放入冰糖小火炒至融化呈浅褐色，加入猪蹄翻炒上色。", "加入生姜、大葱、八角、桂皮、香叶炒出香味，再加入生抽、老抽翻炒均匀。", "倒入足量热水没过猪蹄，大火煮开后转小火炖煮约90分钟。", "猪蹄软烂后加入盐调味，继续炖煮10分钟，待汤汁浓稠即可出锅。"]','["猪蹄焯水时冷水下锅，可更好去除腥味。", "炖煮时加入热水，避免猪蹄肉质发紧。", "喜欢软糯口感可适当延长炖煮时间。"]','0',19,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'排骨煲仔饭','https://img.wyantao.com/img/ai/2026/10/09/f9417ccf17e4418f9a47f9e27ff3ae3f.png','经典广式家常煲仔饭，米饭焦香，排骨鲜嫩入味，荤素搭配。','粤菜,家常菜,煲仔饭,一锅饭,下饭菜','约60分钟','中等','2人份','每份约680千卡','[{"name": "大米", "amount": "200克"}, {"name": "猪肋排", "amount": "300克"}, {"name": "青菜", "amount": "100克"}, {"name": "生姜", "amount": "10克"}, {"name": "蒜", "amount": "3瓣"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "半汤匙"}, {"name": "蚝油", "amount": "1汤匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "白糖", "amount": "1茶匙"}, {"name": "淀粉", "amount": "1茶匙"}, {"name": "食用油", "amount": "适量"}, {"name": "香葱", "amount": "1根"}]','["大米淘洗干净，浸泡30分钟后沥干备用。", "排骨剁成小块，加入姜片、蒜末、生抽、老抽、蚝油、料酒、白糖和淀粉，腌制30分钟。", "砂锅底部刷一层油，放入大米和适量清水，大火煮开后转小火焖煮。", "米饭表面出现小孔、七八成熟时，将腌好的排骨均匀铺在米饭上，继续小火焖15至20分钟。", "青菜焯水后放入砂锅，沿锅边淋少许食用油，继续焖2分钟。", "关火后撒上葱花，淋入少量调好的煲仔饭酱汁即可食用。"]','["大米提前浸泡可使米饭更软糯，锅巴更香。", "排骨尽量选带点肥肉的肋排，口感更嫩。", "焖饭时保持小火，避免底部焦糊。"]','0',20,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'猪肉大葱饺子','https://img.wyantao.com/img/ai/2026/10/09/b661b69143504930b5e369ef48ff832e.png','经典家常饺子，猪肉鲜香搭配大葱清甜，皮薄馅嫩，适合日常制作。','家常菜,面食,饺子,猪肉,传统口味','约60分钟','中等','2人份','每份约520千卡','[{"name": "饺子皮", "amount": "约40张"}, {"name": "猪肉馅", "amount": "300克"}, {"name": "大葱", "amount": "150克"}, {"name": "生姜", "amount": "10克"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "1茶匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "香油", "amount": "1汤匙"}, {"name": "食用油", "amount": "1汤匙"}, {"name": "盐", "amount": "适量"}, {"name": "白胡椒粉", "amount": "少许"}, {"name": "清水", "amount": "适量"}]','["大葱洗净切碎，生姜切末备用。", "猪肉馅加入盐、生抽、老抽、料酒、白胡椒粉和姜末，顺一个方向搅拌上劲。", "分次加入少量清水搅拌，让肉馅吸水变得鲜嫩。", "加入大葱碎、食用油和香油拌匀，调成饺子馅。", "取一张饺子皮，放入适量馅料，对折捏紧边缘包成饺子。", "锅中烧开水，放入饺子轻轻推动，水沸后加入少量冷水，重复2次。", "待饺子全部浮起、皮熟透后捞出即可食用。"]','["猪肉建议选三分肥七分瘦，口感更香嫩。", "大葱最后加入可减少出水，保持馅料鲜味。", "包好的饺子可撒少量面粉防止粘连。"]','0',21,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'洋芋饺子','https://img.wyantao.com/img/ai/2026/10/09/2fa2b5bb5fa74fbea094cc7a086dde14.png','以土豆泥为馅制作的家常饺子，口感软糯，香味浓郁。','家常菜,面食,饺子,土豆,北方风味','约60分钟','中等','2人份','约420千卡/份','[{"name": "土豆", "amount": "300克"}, {"name": "中筋面粉", "amount": "300克"}, {"name": "清水", "amount": "150毫升"}, {"name": "猪肉末", "amount": "100克"}, {"name": "大葱", "amount": "20克"}, {"name": "生姜", "amount": "5克"}, {"name": "食用油", "amount": "15毫升"}, {"name": "生抽", "amount": "10毫升"}, {"name": "盐", "amount": "3克"}, {"name": "十三香", "amount": "1克"}]','["面粉加入清水揉成光滑面团，盖上保鲜膜醒面30分钟。", "土豆去皮切块蒸熟，压成细腻土豆泥备用。", "猪肉末加入葱姜末、生抽、盐、十三香和食用油搅拌均匀。", "将土豆泥与肉馅混合拌匀，调成饺子馅。", "醒好的面团揉匀，分成小剂子，擀成饺子皮。", "包入适量馅料，捏紧边缘制成饺子。", "锅中烧开水，下入饺子煮至浮起，再煮3分钟即可捞出。"]','["土豆蒸熟后尽量压细，口感会更绵软。", "饺子馅不要调得过湿，包制时更容易成型。", "煮饺子时加少量盐可减少破皮。"]','0',22,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'牛肉饺子','https://img.wyantao.com/img/ai/2026/10/09/96dae2489a5b4075b16749c2ba101b38.png','鲜嫩多汁的家常牛肉馅饺子，牛肉香浓，皮薄馅足，适合日常餐桌。','家常菜,主食,面食,饺子','约90分钟','中等','2人份','约520千卡/每份','[{"name": "牛肉馅", "amount": "300克"}, {"name": "饺子皮", "amount": "40张"}, {"name": "大葱", "amount": "50克"}, {"name": "生姜", "amount": "10克"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "半汤匙"}, {"name": "蚝油", "amount": "1汤匙"}, {"name": "香油", "amount": "1汤匙"}, {"name": "食用油", "amount": "1汤匙"}, {"name": "盐", "amount": "3克"}, {"name": "花椒水", "amount": "50毫升"}]','["生姜切末，大葱切碎备用；花椒水提前浸泡后过滤取水。", "牛肉馅加入盐、生抽、老抽、蚝油、姜末，分次加入花椒水搅拌上劲。", "加入大葱碎和香油、食用油拌匀，静置腌制15分钟入味。", "取一张饺子皮，放入适量牛肉馅，对折捏紧边缘包成饺子。", "锅中烧开水，放入饺子轻轻推动，煮至饺子浮起后再煮3分钟即可。", "捞出饺子，搭配醋、辣椒油或蒜汁食用。"]','["牛肉馅加入花椒水可去腥并增加鲜嫩口感。", "搅拌肉馅时朝一个方向搅打，更容易成团。", "包好的饺子可冷冻保存，食用时无需解冻直接煮。"]','0',23,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'绿辣子夹馍','https://img.wyantao.com/img/ai/2026/10/09/332efa199d164426982035e09fefa6d5.png','陕西家常风味小吃，香辣青椒夹入热馍，椒香浓郁，简单下饭。','家常菜,陕西小吃,香辣,快手早餐','20分钟','简单','2人份','420千卡/份','[{"name": "青辣椒", "amount": "200克"}, {"name": "馍（白吉馍或普通面馍）", "amount": "4个"}, {"name": "蒜", "amount": "10克"}, {"name": "食用油", "amount": "20毫升"}, {"name": "生抽", "amount": "10毫升"}, {"name": "香醋", "amount": "5毫升"}, {"name": "盐", "amount": "3克"}, {"name": "白糖", "amount": "2克"}]','["青辣椒洗净去蒂，擦干水分；蒜切末备用。", "锅烧热后少放油，放入青辣椒小火煸炒至表皮起皱，香味散出。", "将炒软的辣椒捞出切碎，加入蒜末、盐、生抽、香醋和白糖拌匀。", "馍从中间剖开，夹入拌好的绿辣子即可食用。", "如喜欢更香，可将夹好的馍放回锅中小火加热片刻。"]','["辣椒选择皮薄肉厚的品种，煸炒后口感更香。", "炒辣椒时不要大火，避免外焦内生。", "馍最好趁热夹食，香味和口感更佳。"]','0',24,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'苦瓜炒蛋','https://img.wyantao.com/img/ai/2026/10/09/58ccd30b616549da82f10a049d5262e4.png','苦瓜与鸡蛋搭配快炒，清香爽口，营养家常。','家常菜,快手菜,清热爽口,下饭菜','15分钟','简单','2人份','180千卡/份','[{"name": "苦瓜", "amount": "1根（约250克）"}, {"name": "鸡蛋", "amount": "3个"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "盐", "amount": "适量（约3克）"}, {"name": "料酒", "amount": "1茶匙"}, {"name": "白糖", "amount": "少许"}, {"name": "葱花", "amount": "适量"}]','["苦瓜洗净后对半剖开，去掉瓜瓤和籽，切成薄片，加少许盐腌制5分钟后挤去水分。", "鸡蛋打入碗中，加入料酒和少许盐搅匀备用。", "锅中烧热油，倒入蛋液炒至凝固成块，盛出备用。", "锅中补少许油，放入苦瓜片翻炒至颜色变亮、断生。", "加入炒好的鸡蛋，调入少许盐和白糖，翻炒均匀后撒葱花即可出锅。"]','["苦瓜加盐腌制可减少苦味，喜欢清苦口感可省略。", "鸡蛋不要炒得过老，保持嫩滑口感更好。", "炒苦瓜时大火快炒，能保持脆嫩口感。"]','0',25,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'火锅','https://img.wyantao.com/img/ai/2026/10/09/324f158c8a784287b4918c6f31034a7c.png','家庭版火锅，汤底鲜香，食材丰富，可自由搭配，适合多人共享。','家常菜,涮煮,聚餐,暖锅','60分钟','简单','4人份','每份约650千卡','[{"name": "火锅底料", "amount": "1包（约150克）"}, {"name": "清水或高汤", "amount": "1500毫升"}, {"name": "牛肉片", "amount": "300克"}, {"name": "羊肉片", "amount": "300克"}, {"name": "鱼丸", "amount": "200克"}, {"name": "豆腐", "amount": "300克"}, {"name": "金针菇", "amount": "200克"}, {"name": "生菜", "amount": "300克"}, {"name": "土豆", "amount": "2个（约300克）"}, {"name": "粉条", "amount": "150克"}, {"name": "葱", "amount": "2根"}, {"name": "姜", "amount": "5片"}, {"name": "蒜", "amount": "5瓣"}, {"name": "香油", "amount": "适量"}, {"name": "蘸料", "amount": "适量"}]','["将牛肉片、羊肉片、鱼丸、豆腐和蔬菜等食材分别清洗处理，切成适合涮煮的大小。", "锅中加入清水或高汤，放入火锅底料、葱、姜、蒜，大火煮开后转小火煮10分钟出香味。", "先放入耐煮的土豆、豆腐、鱼丸和粉条煮熟，再根据个人喜好加入肉片和蔬菜。", "食材煮熟后捞出，搭配蘸料食用，边煮边吃即可。"]','["食材可根据喜好替换，建议荤素搭配更均衡。", "火锅底料已有咸味，调味时可适量调整。", "肉片不要久煮，避免口感变老。"]','0',26,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'莴笋炒肉片','https://img.wyantao.com/img/ai/2026/10/09/116461ec45f94228b690898355ff8b9c.png','莴笋清脆爽口，搭配嫩滑肉片，家常快炒下饭美味。','家常菜,快手菜,荤素搭配,下饭菜','20分钟','简单','2人份','每份约320千卡','[{"name": "莴笋", "amount": "300克"}, {"name": "猪里脊肉", "amount": "200克"}, {"name": "蒜", "amount": "3瓣"}, {"name": "生姜", "amount": "5克"}, {"name": "食用油", "amount": "20毫升"}, {"name": "生抽", "amount": "15毫升"}, {"name": "料酒", "amount": "10毫升"}, {"name": "淀粉", "amount": "5克"}, {"name": "盐", "amount": "3克"}, {"name": "白胡椒粉", "amount": "1克"}]','["莴笋去皮洗净，切成薄片；猪里脊肉切薄片，加入料酒、生抽、淀粉和白胡椒粉腌制10分钟。", "蒜切片，姜切丝备用。锅中倒油烧热，放入肉片快速翻炒至变色，盛出备用。", "锅中留底油，放入姜蒜炒香，加入莴笋片大火翻炒2分钟。", "倒回炒好的肉片，加入盐调味，继续翻炒1至2分钟即可出锅。"]','["肉片提前腌制可使口感更嫩滑。", "莴笋不要炒太久，保持脆嫩口感。", "喜欢清爽口味可减少生抽用量。"]','0',27,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'玉米炒虾仁','https://img.wyantao.com/img/ai/2026/10/09/4f15b5de13164a8a9a8dfb00227ca175.png','鲜嫩虾仁搭配香甜玉米，清爽快手的家常小炒。','家常菜,快手菜,清淡,海鲜','20分钟','简单','2人份','每份260千卡','[{"name": "虾仁", "amount": "200克"}, {"name": "甜玉米粒", "amount": "150克"}, {"name": "胡萝卜", "amount": "50克"}, {"name": "黄瓜", "amount": "50克"}, {"name": "葱", "amount": "1根"}, {"name": "姜", "amount": "3片"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "盐", "amount": "3克"}, {"name": "白胡椒粉", "amount": "1克"}, {"name": "食用油", "amount": "15毫升"}, {"name": "淀粉", "amount": "5克"}]','["虾仁洗净沥干，用料酒、白胡椒粉和淀粉腌制10分钟。", "胡萝卜、黄瓜切小丁，葱切段，玉米粒提前沥干备用。", "锅中倒油烧热，放入葱姜炒香，加入虾仁翻炒至变色。", "加入玉米粒、胡萝卜丁翻炒2分钟，再加入黄瓜丁继续翻炒。", "加入盐调味，翻炒均匀后即可出锅。"]','["虾仁炒至刚变红即可，避免口感变老。", "冷冻玉米粒可提前焯水，炒制时更易熟。", "腌虾时加入少量淀粉可让虾仁更嫩滑。"]','0',28,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'青椒肉丝','https://img.wyantao.com/img/ai/2026/10/09/50db507dea9f46b2bbdb9a45fd52ff29.png','经典家常川味小炒，肉丝嫩滑，青椒清香爽脆，下饭开胃。','家常菜,川菜,下饭菜,快手炒菜','20分钟','简单','2人份','320千卡/份','[{"name": "猪里脊肉", "amount": "200克"}, {"name": "青椒", "amount": "3个"}, {"name": "姜", "amount": "3片"}, {"name": "蒜", "amount": "2瓣"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "淀粉", "amount": "1茶匙"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "盐", "amount": "适量"}, {"name": "白糖", "amount": "少许"}]','["猪里脊洗净切细丝，加入料酒、生抽、淀粉和少许油拌匀，腌制10分钟。", "青椒洗净去籽切丝，姜切丝，蒜切片备用。", "热锅倒油，放入肉丝快速滑炒至变色，盛出备用。", "锅中留少许油，加入姜蒜炒香，放入青椒丝翻炒至断生。", "倒入炒好的肉丝，加入盐和少许白糖调味，快速翻炒均匀即可出锅。"]','["肉丝提前腌制可使口感更嫩。", "炒肉时火力要大，避免久炒变柴。", "青椒炒至断生即可，保留清脆口感。"]','0',29,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'蒜苔炒肉','https://img.wyantao.com/img/ai/2026/10/09/aee8c1bd6d4c4f49b347b3abca2753f1.png','蒜苔清香爽脆，搭配嫩滑肉片，家常快炒下饭美味。','家常菜,快手菜,下饭菜,荤素搭配','20分钟','简单','2人份','每份约320千卡','[{"name": "蒜苔", "amount": "300克"}, {"name": "猪里脊肉", "amount": "150克"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "淀粉", "amount": "1茶匙"}, {"name": "食用油", "amount": "适量"}, {"name": "盐", "amount": "3克"}, {"name": "白糖", "amount": "1克"}, {"name": "姜", "amount": "3片"}]','["蒜苔洗净切成约4厘米小段，猪里脊肉切薄片。", "肉片加入生抽、料酒和淀粉抓匀，腌制10分钟。", "锅中烧热油，放入姜片炒香，加入肉片快速翻炒至变色。", "倒入蒜苔大火翻炒2至3分钟，使其保持脆嫩口感。", "加入盐和白糖调味，翻炒均匀后即可出锅。"]','["肉片提前腌制可使口感更嫩。", "蒜苔不要炒太久，保持清脆更好吃。", "喜欢辣味可加入少量干辣椒一起炒。"]','0',30,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'回锅肉','https://img.wyantao.com/img/ai/2026/10/09/d56be0c5246b4e94b529f6434bc1b8d8.png','经典川菜家常菜，咸香微辣，肥瘦相间，口感丰富下饭。','川菜,家常菜,下饭菜,微辣','40分钟','中等','2人份','650千卡/份','[{"name": "五花肉", "amount": "400克"}, {"name": "青蒜", "amount": "100克"}, {"name": "青椒", "amount": "1个"}, {"name": "红椒", "amount": "1个"}, {"name": "郫县豆瓣酱", "amount": "1汤匙"}, {"name": "甜面酱", "amount": "1茶匙"}, {"name": "生姜", "amount": "3片"}, {"name": "大蒜", "amount": "3瓣"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "白糖", "amount": "半茶匙"}, {"name": "食用油", "amount": "适量"}]','["五花肉洗净，冷水下锅，加入姜片和料酒，煮至八成熟，捞出晾凉后切成薄片。", "青蒜切段，青椒和红椒切片备用。", "锅中少放油，倒入五花肉片，小火煸炒至表面微黄、油脂析出。", "加入郫县豆瓣酱炒出红油，再加入甜面酱、生抽和白糖翻炒均匀。", "放入青椒、红椒和青蒜梗翻炒片刻，再加入青蒜叶快速翻匀即可出锅。"]','["五花肉煮至八成熟更容易切片，也能保持嫩滑口感。", "豆瓣酱本身有咸味，调味时可根据口味减少盐的添加。", "炒肉时用小火慢煸，更容易炒出香味和油脂。"]','0',31,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'梅菜扣肉','https://img.wyantao.com/img/ai/2026/10/09/4d6718e6165549f49eab3628e517e8a7.png','经典粤菜家常做法，肥而不腻，梅菜吸收肉香，咸香下饭。','家常菜,蒸菜,粤菜,下饭菜','约2小时','中等','4人份','650千卡/份','[{"name": "五花肉", "amount": "800克"}, {"name": "梅干菜", "amount": "100克"}, {"name": "生姜", "amount": "10克"}, {"name": "大蒜", "amount": "5瓣"}, {"name": "葱", "amount": "2根"}, {"name": "料酒", "amount": "2汤匙"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "1汤匙"}, {"name": "白糖", "amount": "1茶匙"}, {"name": "蚝油", "amount": "1汤匙"}, {"name": "食用油", "amount": "适量"}]','["梅干菜提前浸泡30分钟，反复清洗去除泥沙，挤干水分备用。", "五花肉洗净，冷水下锅，加入姜片、葱段和料酒，煮至七成熟，捞出擦干表面水分。", "在肉皮表面抹上少量老抽，晾干后放入热油中煎至肉皮金黄起泡，捞出切成厚片。", "锅中留少许油，放入姜蒜末炒香，加入梅干菜翻炒，调入生抽、蚝油和白糖炒匀。", "将五花肉肉皮朝下码入碗中，铺上炒好的梅干菜，加入少量煮肉汤汁。", "放入蒸锅，大火烧开后转中小火蒸约1小时，至肉质软烂。", "取出后倒扣在盘中即可食用。"]','["梅干菜要充分清洗，否则容易有沙粒。", "炸肉皮时注意擦干水分，避免油溅。", "蒸制时间越长，五花肉口感越软糯。"]','0',32,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'黄焖鸡','https://img.wyantao.com/img/ai/2026/10/09/72a5ce67aa4c4ca2be0083585b6b9997.png','经典家常鸡肉菜，鸡腿肉软嫩入味，汤汁浓郁鲜香。','家常菜,下饭菜,鸡肉,焖煮','约50分钟','简单','2人份','520千卡/每份','[{"name": "鸡腿肉", "amount": "500克"}, {"name": "香菇", "amount": "6朵"}, {"name": "土豆", "amount": "1个（约200克）"}, {"name": "青椒", "amount": "1个"}, {"name": "姜", "amount": "10克"}, {"name": "蒜", "amount": "5瓣"}, {"name": "干辣椒", "amount": "2个"}, {"name": "食用油", "amount": "15克"}, {"name": "料酒", "amount": "15毫升"}, {"name": "生抽", "amount": "20毫升"}, {"name": "老抽", "amount": "5毫升"}, {"name": "蚝油", "amount": "10克"}, {"name": "冰糖", "amount": "10克"}, {"name": "盐", "amount": "3克"}, {"name": "清水", "amount": "500毫升"}]','["鸡腿肉剁成小块，洗净沥干；香菇泡发切块，土豆去皮切滚刀块，青椒切片备用。", "锅中放油，加入姜蒜、干辣椒炒香，倒入鸡块翻炒至表面微黄。", "加入料酒、生抽、老抽、蚝油和冰糖翻炒均匀，使鸡肉上色入味。", "加入清水和香菇，大火煮开后转小火焖煮约25分钟。", "放入土豆块继续焖煮15分钟，至鸡肉软烂、土豆熟透。", "加入青椒和盐调味，大火收浓汤汁即可装盘。"]','["鸡腿肉带皮炖煮口感更嫩，建议不要焯水以保留香味。", "土豆不要切太小，避免长时间焖煮后碎掉。", "汤汁留一些拌饭更香，可根据口味调整浓稠度。"]','0',33,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'可乐鸡翅','https://img.wyantao.com/img/ai/2026/10/09/4e2caf9e2d784bbfa49af334365d2a41.png','经典家常鸡翅做法，甜咸入味，色泽红亮，老少皆宜。','家常菜,荤菜,下饭菜,简单易做','40分钟','初级','2人份','每份520千卡','[{"name": "鸡翅中", "amount": "500克"}, {"name": "可乐", "amount": "330毫升"}, {"name": "生姜", "amount": "5片"}, {"name": "小葱", "amount": "2根"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "半汤匙"}, {"name": "食用油", "amount": "适量"}, {"name": "盐", "amount": "少许"}]','["鸡翅洗净，在表面划两刀方便入味，加入料酒和姜片腌制10分钟。", "锅中加水，放入鸡翅焯水，煮出浮沫后捞出洗净备用。", "热锅倒少量油，放入鸡翅煎至两面微黄。", "加入姜片、生抽、老抽翻炒均匀，使鸡翅上色。", "倒入可乐没过鸡翅，大火煮开后转小火焖煮20分钟左右。", "待汤汁浓稠时加入少许盐调味，大火收汁，撒上葱花即可。"]','["鸡翅划口或提前腌制能让味道更入里。", "可乐已有甜味，盐和老抽不要放太多。", "收汁时注意翻动，避免汤汁烧干粘锅。"]','0',34,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'大盘鸡','https://img.wyantao.com/img/ai/2026/10/09/718bcba283324a7998706151bb59ad0a.png','新疆经典家常菜，鸡肉软嫩，土豆绵香，味浓下饭。','新疆菜,家常菜,炖菜,下饭菜','约60分钟','中等','4人份','每份约680千卡','[{"name": "鸡腿肉", "amount": "800克"}, {"name": "土豆", "amount": "2个（约400克）"}, {"name": "洋葱", "amount": "1个（约150克）"}, {"name": "青椒", "amount": "2个"}, {"name": "红椒", "amount": "1个"}, {"name": "宽面条", "amount": "300克"}, {"name": "大葱", "amount": "1根"}, {"name": "生姜", "amount": "10克"}, {"name": "大蒜", "amount": "6瓣"}, {"name": "干辣椒", "amount": "10个"}, {"name": "八角", "amount": "2个"}, {"name": "花椒", "amount": "1小勺"}, {"name": "郫县豆瓣酱", "amount": "1汤匙"}, {"name": "生抽", "amount": "2汤匙"}, {"name": "老抽", "amount": "1汤匙"}, {"name": "白糖", "amount": "1茶匙"}, {"name": "食用油", "amount": "3汤匙"}, {"name": "盐", "amount": "适量"}]','["鸡腿肉剁成块，用清水洗净沥干；土豆去皮切滚刀块，洋葱、青红椒切块备用。", "锅中倒油烧热，加入白糖炒至微黄，放入鸡块翻炒至表面上色。", "加入姜片、蒜瓣、大葱、干辣椒、花椒、八角和豆瓣酱炒出香味。", "加入生抽、老抽翻炒均匀，倒入适量热水没过鸡肉，大火煮开后转小火炖约30分钟。", "加入土豆块继续炖15分钟，至鸡肉熟透、土豆软糯。", "放入洋葱和青红椒翻炒几分钟，加盐调味。", "另起锅煮熟宽面条，盛入盘中，浇上大盘鸡即可食用。"]','["鸡腿肉比鸡胸肉更嫩，适合炖煮。", "炒糖色时火候不要太大，避免发苦。", "面条可根据喜好增减，蘸汤汁更入味。"]','0',35,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'清炒油麦菜','https://img.wyantao.com/img/ai/2026/10/09/d0e97398e1974e579fc06d06644be758.png','清爽脆嫩的家常快手素菜，保留油麦菜鲜香与爽脆口感。','家常菜,素菜,快手菜,清炒','10分钟','简单','2人份','80千卡/份','[{"name": "油麦菜", "amount": "500克"}, {"name": "蒜", "amount": "3瓣"}, {"name": "食用油", "amount": "15克"}, {"name": "盐", "amount": "3克"}, {"name": "鸡精", "amount": "1克（可选）"}]','["油麦菜洗净，沥干水分，切成约5厘米长的段；蒜切末备用。", "锅烧热后加入食用油，放入蒜末小火炒出香味。", "倒入油麦菜，大火快速翻炒至叶片变软。", "加入盐和鸡精调味，继续翻炒均匀后即可出锅。"]','["油麦菜下锅前尽量沥干水分，避免炒出过多汤汁。", "全程大火快炒可保持油麦菜翠绿和爽脆口感。"]','0',36,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'酸辣土豆丝','https://img.wyantao.com/img/ai/2026/10/09/d9003f8a846b4ff389d9e66b2a2f1141.png','经典家常小炒，酸辣开胃，土豆丝爽脆入味。','家常菜,酸辣味,快手菜,素菜','20分钟','简单','2人份','180千卡/份','[{"name": "土豆", "amount": "2个（约400克）"}, {"name": "干辣椒", "amount": "3个"}, {"name": "蒜", "amount": "2瓣"}, {"name": "白醋", "amount": "2汤匙"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "盐", "amount": "1茶匙"}, {"name": "白糖", "amount": "少许"}, {"name": "花椒", "amount": "10粒"}, {"name": "葱", "amount": "1根"}]','["土豆去皮洗净，切成细丝，放入清水中浸泡10分钟，洗去多余淀粉后沥干。", "蒜切片，干辣椒剪段，葱切葱花备用。", "锅中烧水，加入少许盐，将土豆丝快速焯水约30秒，捞出过凉水沥干。", "热锅倒油，放入花椒、干辣椒和蒜片炒香。", "加入土豆丝大火翻炒，放入盐、白糖和白醋调味，快速翻炒均匀。", "撒入葱花后关火，装盘即可。"]','["土豆丝切得越细越容易保持爽脆口感。", "焯水时间不宜过长，避免炒后变软。", "白醋可根据个人口味调整，喜欢更酸可适量增加。"]','0',37,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'西葫芦炒蛋','https://img.wyantao.com/img/ai/2026/10/09/98538d1f65f747d8a694b7cb19b62268.png','清爽家常快手菜，西葫芦搭配鸡蛋，鲜嫩爽口，营养简单。','家常菜,快手菜,清淡,炒菜','15分钟','简单','2人份','每份约180千卡','[{"name": "西葫芦", "amount": "1根（约300克）"}, {"name": "鸡蛋", "amount": "3个"}, {"name": "食用油", "amount": "20克"}, {"name": "大蒜", "amount": "2瓣"}, {"name": "盐", "amount": "3克"}, {"name": "生抽", "amount": "1汤匙（约10毫升）"}, {"name": "白胡椒粉", "amount": "少许"}]','["西葫芦洗净切薄片，大蒜切末；鸡蛋打入碗中，加少许盐搅散。", "锅中放油烧热，倒入蛋液炒至凝固，盛出备用。", "锅中补少许油，放入蒜末炒香，加入西葫芦片翻炒至稍微软化。", "加入生抽、盐和白胡椒粉调味，倒回炒好的鸡蛋快速翻匀。", "炒至西葫芦熟透即可关火，装盘食用。"]','["西葫芦不要炒太久，保持微脆口感更好。", "鸡蛋先炒后放可避免炒老，成品更嫩。"]','0',38,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'凉拌黄瓜','https://img.wyantao.com/img/ai/2026/10/09/d807eab70b0c43bea04c9510aeccca1b.png','清爽脆嫩的家常凉拌菜，酸辣开胃，制作简单快捷。','凉菜,家常菜,快手菜,酸辣味','10分钟','简单','2人份','80千卡/份','[{"name": "黄瓜", "amount": "2根（约400克）"}, {"name": "大蒜", "amount": "3瓣"}, {"name": "小米辣", "amount": "2个"}, {"name": "香醋", "amount": "2汤匙"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "香油", "amount": "1茶匙"}, {"name": "白糖", "amount": "1茶匙"}, {"name": "盐", "amount": "适量"}, {"name": "熟白芝麻", "amount": "少许"}]','["黄瓜洗净，用刀拍裂后切成小段，放入碗中备用。", "加入少许盐拌匀，腌制5分钟后倒掉多余水分。", "大蒜切末，小米辣切圈，与黄瓜混合。", "加入生抽、香醋、白糖、香油和熟白芝麻，充分拌匀。", "静置几分钟入味后即可装盘食用。"]','["黄瓜拍碎比直接切更容易入味。", "腌黄瓜后倒掉水分，口感更脆爽。", "喜欢更香的味道可加入少许花生碎。"]','0',39,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'香干炒肉丝','https://img.wyantao.com/img/ai/2026/10/09/e9ddece37c9e4ebc9b978759e588be08.png','香干搭配嫩滑肉丝快炒，咸香下饭，家常味十足。','家常菜,湘菜风味,下饭菜,快手菜','20分钟','简单','2人份','每份约420千卡','[{"name": "香干", "amount": "200克"}, {"name": "猪里脊肉", "amount": "150克"}, {"name": "青椒", "amount": "1个（约80克）"}, {"name": "红椒", "amount": "半个（约50克）"}, {"name": "蒜", "amount": "3瓣"}, {"name": "生姜", "amount": "5克"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "老抽", "amount": "半汤匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "淀粉", "amount": "1茶匙"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "盐", "amount": "适量"}]','["猪里脊肉切成细丝，加入料酒、生抽和淀粉抓匀，腌制10分钟。", "香干切成细条，青椒和红椒洗净切丝，蒜和姜切末备用。", "锅烧热后加入食用油，放入肉丝快速滑炒至变色，盛出备用。", "锅中留底油，加入姜蒜炒香，放入香干翻炒1分钟。", "加入青椒和红椒丝翻炒至断生，再倒入炒好的肉丝。", "加入生抽、老抽和适量盐调味，翻炒均匀后即可出锅。"]','["肉丝提前腌制可以保持嫩滑口感。", "香干本身有咸味，加盐时需根据口味调整。", "大火快炒能保持青椒爽脆和菜品香气。"]','0',40,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'黄金炒饭','https://img.wyantao.com/img/ai/2026/10/09/516586369c784e60b683a7e5b6f33c55.png','粒粒分明的黄金炒饭，米饭裹满蛋香，色泽金黄，简单快手。','家常菜,炒饭,快手餐,蛋香','15分钟','简单','2人份','每份约520千卡','[{"name": "米饭", "amount": "300克（隔夜饭更佳）"}, {"name": "鸡蛋", "amount": "3个"}, {"name": "胡萝卜", "amount": "50克"}, {"name": "玉米粒", "amount": "50克"}, {"name": "青豆", "amount": "30克"}, {"name": "小葱", "amount": "2根"}, {"name": "食用油", "amount": "20毫升"}, {"name": "盐", "amount": "3克"}, {"name": "生抽", "amount": "10毫升"}]','["将米饭提前打散，胡萝卜切小丁，小葱切葱花备用。", "鸡蛋打入碗中搅匀，锅中放少许油，炒至嫩熟后盛出。", "锅中加入食用油，放入胡萝卜丁、玉米粒和青豆翻炒至断生。", "加入米饭大火翻炒，使米粒充分散开，炒至水分减少。", "倒入炒好的鸡蛋，加入盐和生抽调味，继续翻炒均匀。", "撒入葱花翻匀即可出锅。"]','["使用冷藏隔夜米饭，炒出的饭粒更松散。", "炒饭时保持大火快速翻炒，避免米饭发黏。", "鸡蛋提前炒熟再加入，可保持口感嫩滑。"]','0',41,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱'),
(0,'辣炒花甲','https://img.wyantao.com/img/ai/2026/10/09/e98024522bc64b669c806370a36a321e.png','辣炒花甲是一道鲜辣开胃的家常海鲜菜，花甲肉质嫩滑，裹满香辣酱汁，配啤酒或米饭都过瘾。','家常菜,海鲜,辣,快炒,下酒菜','20分钟','初级','2人份','280千卡','[{"name": "花甲", "amount": "500克"}, {"name": "小米辣", "amount": "3-4个"}, {"name": "干辣椒", "amount": "5-6个"}, {"name": "大蒜", "amount": "4瓣"}, {"name": "生姜", "amount": "1小块"}, {"name": "小葱", "amount": "2根"}, {"name": "生抽", "amount": "1汤匙"}, {"name": "蚝油", "amount": "1汤匙"}, {"name": "料酒", "amount": "1汤匙"}, {"name": "白糖", "amount": "1茶匙"}, {"name": "食用油", "amount": "2汤匙"}, {"name": "盐", "amount": "适量"}]','["花甲放入淡盐水中浸泡1-2小时，中途换水一次，让其吐净泥沙，然后搓洗干净沥干。", "小米辣切圈，干辣椒剪段，大蒜切末，生姜切丝，小葱切段备用。", "锅中烧开水，加入少许料酒，倒入花甲焯烫至开口，捞出沥干；未开口的丢弃。", "热锅倒油，中小火下姜丝、蒜末、干辣椒段和小米辣圈，炒出香味。", "转大火，倒入花甲快速翻炒，沿锅边淋入料酒去腥。", "加入生抽、蚝油、白糖和少许盐，继续翻炒均匀，让花甲裹上酱汁。", "最后撒入葱段，翻炒几下即可出锅。"]','["花甲吐沙是关键，可在水中加几滴香油促进吐沙。", "焯水时间不宜过长，开口即可捞出，避免肉质变老。", "全程大火快炒，保持花甲鲜嫩，酱汁均匀包裹。"]','0',42,'0','0','admin',sysdate(),'admin',sysdate(),'公共菜谱');

-- ================= 菜品↔分类关联（biz_type=1 表示菜品） =================
-- 同类标签的菜合并成一条语句；重复执行按主键去重。

-- 热菜 / 半荤半素 / 蛋 / 蔬菜 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d
join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name in ('青椒炒鸡蛋','西红柿炒鸡蛋','韭菜炒鸡蛋','苦瓜炒蛋','西葫芦炒蛋')
  and c.name in ('热菜','半荤半素','蛋','蔬菜','炒');

-- 素菜 / 蔬菜 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d
join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name in ('酸辣白菜','炒洋芋片','炒洋芋条','淋汁上海青','清炒油麦菜','酸辣土豆丝')
  and c.name in ('素菜','蔬菜','炒');

-- 热菜 / 荤菜 / 肉 / 炖
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d
join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name in ('板栗鸡','红烧肉','黄焖鸡','可乐鸡翅','大盘鸡')
  and c.name in ('热菜','荤菜','肉','炖');

-- 热菜 / 荤菜 / 肉 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d
join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name in ('小炒黄牛肉','回锅肉')
  and c.name in ('热菜','荤菜','肉','炒');

-- 荤菜 / 肉 / 煮
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d
join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name in ('排骨煲仔饭','猪肉大葱饺子','牛肉饺子')
  and c.name in ('荤菜','肉','煮');

-- 热菜 / 半荤半素 / 肉 / 蔬菜 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d
join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name in ('莴笋炒肉片','青椒肉丝','蒜苔炒肉')
  and c.name in ('热菜','半荤半素','肉','蔬菜','炒');

-- 热菜 / 荤菜 / 鱼 / 蒸
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '清蒸鲈鱼'
  and c.name in ('热菜','荤菜','鱼','蒸');

-- 热菜 / 素菜 / 蔬菜 / 炖
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '红烧茄子'
  and c.name in ('热菜','素菜','蔬菜','炖');

-- 热菜 / 荤菜 / 肉 / 炸
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '锅包肉'
  and c.name in ('热菜','荤菜','肉','炸');

-- 岷县 / 汤 / 素菜 / 蔬菜
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '洋芋拌汤'
  and c.name in ('岷县','汤','素菜','蔬菜');

-- 煮
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '面片'
  and c.name in ('岷县','煮');

-- 热菜 / 荤菜 / 肉 / 烤
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '牛排'
  and c.name in ('热菜','荤菜','肉','烤');

-- 汤 / 蛋 / 蔬菜
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '西红柿鸡蛋汤'
  and c.name in ('汤','蛋','蔬菜');

-- 汤 / 蛋
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '紫菜蛋花汤'
  and c.name in ('汤','蛋');

-- 热菜 / 荤菜 / 肉 / 煮
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '煮猪蹄'
  and c.name in ('热菜','荤菜','肉','煮');

-- 素菜 / 蔬菜 / 煮
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '洋芋饺子'
  and c.name in ('素菜','蔬菜','煮');

-- 素菜 / 蔬菜
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '绿辣子夹馍'
  and c.name in ('素菜','蔬菜');

-- 热菜 / 煮
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '火锅'
  and c.name in ('热菜','煮');

-- 热菜 / 半荤半素 / 蔬菜 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '玉米炒虾仁'
  and c.name in ('热菜','半荤半素','蔬菜','炒');

-- 热菜 / 荤菜 / 肉 / 蒸
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '梅菜扣肉'
  and c.name in ('热菜','荤菜','肉','蒸');

-- 素菜 / 蔬菜 / 凉拌
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '凉拌黄瓜'
  and c.name in ('素菜','蔬菜','凉拌');

-- 热菜 / 半荤半素 / 豆制品 / 肉 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '香干炒肉丝'
  and c.name in ('热菜','半荤半素','豆制品','肉','炒');

-- 蛋 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '黄金炒饭'
  and c.name in ('蛋','炒');

-- 热菜 / 荤菜 / 炒
insert into meal_category_ref (biz_type, biz_id, category_id)
select 1, d.dish_id, c.category_id
from meal_dish d join meal_category c on c.dept_id = 0 and c.del_flag = '0'
where d.dept_id = 0 and d.name = '辣炒花甲'
  and c.name in ('热菜','荤菜','炒');
