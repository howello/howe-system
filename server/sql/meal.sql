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
-- 仅填菜名（其余字段留空，可在管理端补充）。分类归属统一在文末写入 meal_category_ref。
-- 演示/填库用；菜按 (dept_id=0, name) 去重，重复执行不会堆积。
-- ----------------------------
insert into meal_dish (dept_id, name, status, sort, source, del_flag, create_by, create_time, remark)
values
(0, '酸辣白菜',       '0',  1, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '清蒸鲈鱼',       '0',  2, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '红烧茄子',       '0',  3, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '锅包肉',         '0',  4, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '小炒黄牛肉',     '0',  5, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '洋芋拌汤',       '0',  6, '0', '0', 'admin', sysdate(), '公共菜谱（岷县菜）'),
(0, '炒洋芋片',       '0',  7, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '炒洋芋条',       '0',  8, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '面片',           '0',  9, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '板栗鸡',         '0', 10, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '牛排',           '0', 11, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '青椒炒鸡蛋',     '0', 12, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '红烧肉',         '0', 13, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '西红柿炒鸡蛋',   '0', 14, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '西红柿鸡蛋汤',   '0', 15, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '紫菜蛋花汤',     '0', 16, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '淋汁上海青',     '0', 17, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '韭菜炒鸡蛋',     '0', 18, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '煮猪蹄',         '0', 19, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '排骨煲仔饭',     '0', 20, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '猪肉大葱饺子',   '0', 21, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '洋芋饺子',       '0', 22, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '牛肉饺子',       '0', 23, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '绿辣子夹馍',     '0', 24, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '苦瓜炒蛋',       '0', 25, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '火锅',           '0', 26, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '莴笋炒肉片',     '0', 27, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '玉米炒虾仁',     '0', 28, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '青椒肉丝',       '0', 29, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '蒜苔炒肉',       '0', 30, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '回锅肉',         '0', 31, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '梅菜扣肉',       '0', 32, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '黄焖鸡',         '0', 33, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '可乐鸡翅',       '0', 34, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '大盘鸡',         '0', 35, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '清炒油麦菜',     '0', 36, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '酸辣土豆丝',     '0', 37, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '西葫芦炒蛋',     '0', 38, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '凉拌黄瓜',       '0', 39, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '香干炒肉丝',     '0', 40, '0', '0', 'admin', sysdate(), '公共菜谱'),
(0, '黄金炒饭',       '0', 41, '0', '0', 'admin', sysdate(), '公共菜谱');

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
