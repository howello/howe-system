-- ============================================================
-- meal.sql — 家庭点餐模块脚本（module-meal）
--
-- 内容：六张业务表的建表、后台菜单与三个家庭角色，以及公共菜谱初始/扩展 seed。
--       由原 meal_20260922 / meal_notify_20260929 / meal_seed_20260928 合并而来，
--       其中 accept_user_id 已并入 meal_order 建表（原为独立 ALTER 增量）。
--
-- 六张表：
--   meal_category    菜品分类，dept_id=0 为平台公共分类，否则为该家庭私有分类
--   meal_dish        菜品，dept_id=0 为平台公共菜谱，否则为该家庭私有菜
--   meal_order       订单主表，只有下单时间（没有预约用餐时间）
--   meal_order_item  订单明细，存菜名/封面快照，菜品后续改名或删除都不影响历史订单
--   meal_review      评价，一单一评（order_id 唯一约束）
--   meal_proposal    新菜提案，审核通过时自动往 meal_dish 落一条（source=1）
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
  category_id  bigint(20)                              comment '分类ID',
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
  key idx_meal_dish_dept (dept_id, category_id, status)
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
  category_id   bigint(20)                             comment '期望分类',
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
-- 7、后台菜单
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
-- 8、三个家庭角色
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
-- 9、公共菜谱初始数据（dept_id = 0，所有家庭可见）
--
-- 仅为让新家庭一建起来就有菜可点、便于验证「公共菜谱对所有家庭可见」这条行为。
-- 不需要可以直接删：delete from meal_dish where dept_id = 0; delete from meal_category where dept_id = 0;
-- ----------------------------
insert into meal_category (dept_id, name, icon, sort, status, del_flag, create_by, create_time, remark)
values (0, '热菜', '', 1, '0', '0', 'admin', sysdate(), '公共分类');

select @cHot := LAST_INSERT_ID();

insert into meal_category (dept_id, name, icon, sort, status, del_flag, create_by, create_time, remark)
values (0, '凉菜', '', 2, '0', '0', 'admin', sysdate(), '公共分类');

select @cCold := LAST_INSERT_ID();

insert into meal_category (dept_id, name, icon, sort, status, del_flag, create_by, create_time, remark)
values (0, '汤羹', '', 3, '0', '0', 'admin', sysdate(), '公共分类');

select @cSoup := LAST_INSERT_ID();

insert into meal_category (dept_id, name, icon, sort, status, del_flag, create_by, create_time, remark)
values (0, '主食', '', 4, '0', '0', 'admin', sysdate(), '公共分类');

select @cStaple := LAST_INSERT_ID();

insert into meal_dish (dept_id, category_id, name, cover, description, tags, duration, level, serve, kcal, ingredients, steps, tips, status, sort, source, del_flag, create_by, create_time, remark)
values (0, @cHot, '红烧肉', '', '肥而不腻，入口即化', '家常,下饭', '90 分钟', '中等', '3 人份', '520 千卡',
        '[{"name":"带皮五花肉","amount":"600 g"},{"name":"冰糖","amount":"25 g"},{"name":"生抽","amount":"2 勺"},{"name":"姜片","amount":"3 片"},{"name":"八角","amount":"2 颗"}]',
        '["五花肉切 3 cm 见方，冷水下锅加姜片焯 3 分钟，捞出冲净浮沫。","锅里少许油，下冰糖小火炒到琥珀色，倒入肉块快速翻炒上色。","加姜片、八角、生抽炒香，倒热水没过肉面，小火炖 60 分钟。","大火收汁到汤汁浓稠挂在肉上即可。"]',
        '["炒糖色全程小火，糖一变色立刻下肉，否则会发苦。"]',
        '0', 1, '0', '0', 'admin', sysdate(), '公共菜谱');

insert into meal_dish (dept_id, category_id, name, cover, description, tags, duration, level, serve, kcal, ingredients, steps, tips, status, sort, source, del_flag, create_by, create_time, remark)
values (0, @cHot, '宫保鸡丁', '', '酸甜微辣，下饭神器', '下饭,快手', '30 分钟', '简单', '2 人份', '430 千卡',
        '[{"name":"鸡腿肉","amount":"400 g"},{"name":"花生米","amount":"80 g"},{"name":"干辣椒","amount":"8 个"},{"name":"花椒","amount":"1 小勺"}]',
        '["鸡腿肉切丁，加料酒、生抽、淀粉抓匀腌 15 分钟。","碗里调汁：生抽、香醋、白糖、淀粉、清水各适量。","热油下干辣椒与花椒爆香，下鸡丁炒到变色。","倒入调好的汁快速翻炒，最后加花生米炒匀出锅。"]',
        '["花生米最后放，早放会回软。"]',
        '0', 2, '0', '0', 'admin', sysdate(), '公共菜谱');

insert into meal_dish (dept_id, category_id, name, cover, description, tags, duration, level, serve, kcal, ingredients, steps, tips, status, sort, source, del_flag, create_by, create_time, remark)
values (0, @cSoup, '番茄牛腩汤', '', '酸甜浓郁，暖胃', '汤羹,炖菜', '120 分钟', '中等', '4 人份', '380 千卡',
        '[{"name":"牛腩","amount":"800 g"},{"name":"番茄","amount":"4 个"},{"name":"洋葱","amount":"1 个"}]',
        '["牛腩切块冷水下锅焯水，撇净浮沫捞出。","番茄划十字用开水烫过去皮，切块。","热锅下洋葱炒香，加番茄炒出沙，放牛腩翻炒。","加热水没过食材，小火炖 90 分钟，加盐调味。"]',
        '["番茄先炒出沙，汤才会浓。"]',
        '0', 3, '0', '0', 'admin', sysdate(), '公共菜谱');

insert into meal_dish (dept_id, category_id, name, cover, description, tags, duration, level, serve, kcal, ingredients, steps, tips, status, sort, source, del_flag, create_by, create_time, remark)
values (0, @cCold, '蒜泥白肉', '', '蒜香浓郁，肥瘦相间', '凉菜,解腻', '25 分钟', '简单', '2 人份', '460 千卡',
        '[{"name":"五花肉","amount":"400 g"},{"name":"蒜","amount":"1 头"},{"name":"黄瓜","amount":"1 根"}]',
        '["五花肉整块冷水下锅，加姜葱料酒煮 20 分钟，捞出放凉。","黄瓜切薄片垫盘底。","肉切薄片码在黄瓜上。","蒜捣成泥，加生抽、香醋、辣椒油、少许糖调成料汁淋上去。"]',
        '["肉煮好后过一遍冰水，切片更利落。"]',
        '0', 4, '0', '0', 'admin', sysdate(), '公共菜谱');

insert into meal_dish (dept_id, category_id, name, cover, description, tags, duration, level, serve, kcal, ingredients, steps, tips, status, sort, source, del_flag, create_by, create_time, remark)
values (0, @cStaple, '扬州炒饭', '', '粒粒分明，配料丰富', '主食,快手', '20 分钟', '简单', '2 人份', '560 千卡',
        '[{"name":"隔夜米饭","amount":"2 碗"},{"name":"鸡蛋","amount":"2 个"},{"name":"虾仁","amount":"100 g"},{"name":"火腿丁","amount":"50 g"}]',
        '["鸡蛋打散，热油炒成蛋碎盛出。","虾仁与火腿丁下锅炒香。","倒入米饭中火翻炒到粒粒分开。","加蛋碎、青豆，用盐和少许生抽调味，炒匀出锅。"]',
        '["用隔夜饭，水分少才炒得散。"]',
        '0', 5, '0', '0', 'admin', sysdate(), '公共菜谱');

-- ----------------------------
-- 10、扩展公共菜谱 seed（8 个分类 × 每类补到 5 道）
--
-- 只动公共菜谱(meal_category/meal_dish 的 dept_id=0)，不动家庭私有数据，可重复执行。
-- 分类按 name 定位复用 ID；菜按 (dept_id=0,name) 去重跳过，重复执行不会堆积。
-- 已由上面「9、公共菜谱初始数据」落库的菜只占名额、不重复新增；本段补齐到每类正好 5 道。
-- 演示/填库用，用料为家常模板；若需更贴近真实可自行精修。
-- ----------------------------
SET NAMES utf8mb4;

-- ================= 1. 补齐/确认 8 个公共分类（幂等：存在则复用） =================
INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'招牌热菜','🔥',1,'0','0','admin',NOW(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='招牌热菜' AND del_flag='0');
SET @cHot    = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='招牌热菜' AND del_flag='0' LIMIT 1);

INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'凉菜','🥗',2,'0','0','admin',sysdate(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='凉菜' AND del_flag='0');
SET @cCold   = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='凉菜' AND del_flag='0' LIMIT 1);

INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'汤羹','🫕',3,'0','0','admin',sysdate(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='汤羹' AND del_flag='0');
SET @cSoup   = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='汤羹' AND del_flag='0' LIMIT 1);

INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'主食','🍚',4,'0','0','admin',sysdate(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='主食' AND del_flag='0');
SET @cStaple = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='主食' AND del_flag='0' LIMIT 1);

INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'烤肉','🍖',5,'0','0','admin',sysdate(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='烤肉' AND del_flag='0');
SET @cGrill  = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='烤肉' AND del_flag='0' LIMIT 1);

INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'清淡小炒','🥬',6,'0','0','admin',sysdate(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='清淡小炒' AND del_flag='0');
SET @cLight  = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='清淡小炒' AND del_flag='0' LIMIT 1);

INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'甜品糖水','🍮',7,'0','0','admin',sysdate(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='甜品糖水' AND del_flag='0');
SET @cSweet  = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='甜品糖水' AND del_flag='0' LIMIT 1);

INSERT INTO meal_category (dept_id,name,icon,sort,status,del_flag,create_by,create_time,remark)
SELECT 0,'小菜蘸料','🌶️',8,'0','0','admin',sysdate(),'公共分类'
WHERE NOT EXISTS (SELECT 1 FROM meal_category WHERE dept_id=0 AND name='小菜蘸料' AND del_flag='0');
SET @cSauce  = (SELECT category_id FROM meal_category WHERE dept_id=0 AND name='小菜蘸料' AND del_flag='0' LIMIT 1);

-- ================= 2. 各分类补齐菜品（每类「已有 + 补到」= 5 道） =================
-- 20 列固定顺序：dept_id,category_id,name,cover,description,tags,duration,level,
--   serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark
-- ================= 1. 招牌热菜 @cHot =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cHot,'糖醋排骨','','色泽红亮，酸甜下饭','招牌,下饭','60 分钟','中等','3 人份','610 千卡','[{"name":"排骨","amount":"500g"},{"name":"香醋","amount":"3勺"},{"name":"白糖","amount":"2勺"},{"name":"姜","amount":"3片"}]','["排骨焯水去浮沫","炒糖色给排骨上色","加水没过排骨炖40分钟","大火收汁"]','["糖色要小火防焦，最后收汁勤翻"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='糖醋排骨');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cHot,'宫保鸡丁','','经典川菜，鸡肉花生微辣','招牌,微辣','30 分钟','中等','2 人份','520 千卡','[{"name":"鸡胸肉","amount":"300g"},{"name":"花生米","amount":"50g"},{"name":"干辣椒","amount":"8个"},{"name":"花椒","amount":"1小勺"}]','["鸡胸肉切丁腌制","炒香干辣椒和花椒","下鸡丁炒至变色","加花生米和调味汁翻炒"]','["鸡丁提前腌制更嫩","花生米最后放保持脆"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='宫保鸡丁');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cHot,'鱼香肉丝','','酸甜微辣，下饭','下饭,微辣','30 分钟','中等','2 人份','480 千卡','[{"name":"里脊肉","amount":"300g"},{"name":"木耳","amount":"30g"},{"name":"胡萝卜","amount":"1根"},{"name":"泡椒","amount":"3个"}]','["里脊肉切丝腌制","调鱼香汁","炒香泡椒和姜蒜","下肉丝和配菜翻炒","倒入鱼香汁收汁"]','["鱼香汁糖醋比例约1:1","大火快炒"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='鱼香肉丝');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cHot,'红烧肉','','肥而不腻，酱香浓郁','招牌,家常','90 分钟','中等','4 人份','780 千卡','[{"name":"五花肉","amount":"600g"},{"name":"冰糖","amount":"30g"},{"name":"生抽","amount":"2勺"},{"name":"老抽","amount":"1勺"}]','["五花肉切块焯水","炒糖色","加肉块翻炒上色","加水和调料炖60分钟","大火收汁"]','["炒糖色用小火","炖煮时间要足"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='红烧肉');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cHot,'麻婆豆腐','','麻辣鲜香，嫩豆腐','麻辣,下饭','20 分钟','简单','2 人份','320 千卡','[{"name":"嫩豆腐","amount":"400g"},{"name":"牛肉末","amount":"100g"},{"name":"豆瓣酱","amount":"1勺"},{"name":"花椒粉","amount":"适量"}]','["豆腐切块焯水","炒香牛肉末和豆瓣酱","加水和豆腐煮5分钟","勾芡撒花椒粉"]','["豆腐焯水去豆腥","勾芡分两次更浓稠"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='麻婆豆腐');

-- ================= 2. 凉菜 @cCold =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cCold,'凉拌黄瓜','','清爽解腻','爽口,凉菜','10 分钟','简单','2 人份','80 千卡','[{"name":"黄瓜","amount":"2根"},{"name":"蒜","amount":"3瓣"},{"name":"香醋","amount":"2勺"},{"name":"香油","amount":"1勺"}]','["黄瓜拍碎切段","加蒜末和调料拌匀","冷藏10分钟更爽口"]','["拍碎比切片更入味"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='凉拌黄瓜');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cCold,'口水鸡','','川味红油，麻辣鲜香','麻辣,凉菜','40 分钟','中等','3 人份','450 千卡','[{"name":"鸡腿","amount":"2个"},{"name":"红油","amount":"3勺"},{"name":"花椒粉","amount":"1小勺"},{"name":"花生碎","amount":"适量"}]','["鸡腿煮熟后冰水浸泡","切块摆盘","淋红油和调料","撒花生碎和葱花"]','["冰水浸泡鸡肉更紧实"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='口水鸡');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cCold,'皮蛋豆腐','','嫩豆腐配皮蛋','凉菜,清淡','10 分钟','简单','2 人份','180 千卡','[{"name":"内酯豆腐","amount":"1盒"},{"name":"皮蛋","amount":"2个"},{"name":"生抽","amount":"2勺"},{"name":"香油","amount":"1勺"}]','["豆腐切块装盘","皮蛋切瓣放在豆腐上","淋生抽和香油","撒葱花"]','["豆腐用内酯豆腐更嫩"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='皮蛋豆腐');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cCold,'夫妻肺片','','麻辣牛肉牛杂','麻辣,凉菜','60 分钟','较难','3 人份','520 千卡','[{"name":"牛肉","amount":"300g"},{"name":"牛肚","amount":"200g"},{"name":"红油","amount":"3勺"},{"name":"花椒粉","amount":"1小勺"}]','["牛肉牛肚煮熟切片","调红油汁","拌匀后撒花生碎和香菜"]','["牛肚煮软后再切"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='夫妻肺片');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cCold,'凉拌木耳','','爽脆开胃','爽口,凉菜','15 分钟','简单','2 人份','90 千卡','[{"name":"干木耳","amount":"30g"},{"name":"小米辣","amount":"2个"},{"name":"香醋","amount":"2勺"},{"name":"生抽","amount":"1勺"}]','["木耳泡发焯水","过凉水沥干","加调料拌匀"]','["木耳焯水后过凉水更脆"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='凉拌木耳');

-- ================= 3. 汤羹 @cSoup =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSoup,'西红柿鸡蛋汤','','家常酸甜','汤羹,家常','15 分钟','简单','2 人份','120 千卡','[{"name":"西红柿","amount":"2个"},{"name":"鸡蛋","amount":"2个"},{"name":"盐","amount":"适量"},{"name":"香油","amount":"几滴"}]','["西红柿切块炒出汁","加水烧开","淋入蛋液","加盐和香油"]','["蛋液淋入时搅拌成蛋花"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='西红柿鸡蛋汤');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSoup,'紫菜蛋花汤','','清淡鲜香','汤羹,清淡','10 分钟','简单','2 人份','80 千卡','[{"name":"紫菜","amount":"1小把"},{"name":"鸡蛋","amount":"1个"},{"name":"虾皮","amount":"1小把"},{"name":"香油","amount":"几滴"}]','["水烧开放紫菜和虾皮","淋入蛋液","加盐和香油"]','["紫菜提前泡开"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='紫菜蛋花汤');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSoup,'冬瓜排骨汤','','清润解腻','汤羹,滋补','60 分钟','中等','3 人份','280 千卡','[{"name":"排骨","amount":"400g"},{"name":"冬瓜","amount":"300g"},{"name":"姜","amount":"3片"},{"name":"盐","amount":"适量"}]','["排骨焯水","加姜片炖40分钟","加冬瓜再炖15分钟","加盐调味"]','["冬瓜后放避免煮烂"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='冬瓜排骨汤');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSoup,'玉米胡萝卜汤','','清甜营养','汤羹,清淡','40 分钟','简单','3 人份','180 千卡','[{"name":"玉米","amount":"1根"},{"name":"胡萝卜","amount":"1根"},{"name":"排骨","amount":"300g"},{"name":"盐","amount":"适量"}]','["排骨焯水","加玉米胡萝卜和姜片","炖40分钟","加盐调味"]','["玉米选甜玉米"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='玉米胡萝卜汤');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSoup,'酸辣汤','','酸辣开胃','汤羹,酸辣','20 分钟','中等','2 人份','150 千卡','[{"name":"豆腐","amount":"100g"},{"name":"木耳","amount":"20g"},{"name":"鸡蛋","amount":"1个"},{"name":"醋","amount":"2勺"},{"name":"白胡椒粉","amount":"1小勺"}]','["所有食材切丝","水烧开下食材","加醋和白胡椒粉","勾芡淋蛋液"]','["醋最后放保持酸味"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='酸辣汤');

-- ================= 4. 主食 @cStaple =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cStaple,'米饭','','东北大米','主食','30 分钟','简单','1 人份','230 千卡','[{"name":"大米","amount":"1杯"},{"name":"水","amount":"1.2杯"}]','["大米淘洗","加水放入电饭煲","煮饭模式"]','["米水比例约1:1.2"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='米饭');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cStaple,'馒头','','手工馒头','主食,面食','90 分钟','中等','4 人份','280 千卡','[{"name":"面粉","amount":"500g"},{"name":"酵母","amount":"5g"},{"name":"水","amount":"250ml"}]','["和面发酵","排气揉团","二次醒发","蒸15分钟"]','["发酵至两倍大"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='馒头');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cStaple,'花卷','','葱香花卷','主食,面食','90 分钟','中等','4 人份','300 千卡','[{"name":"面粉","amount":"500g"},{"name":"酵母","amount":"5g"},{"name":"葱花","amount":"适量"},{"name":"油","amount":"适量"}]','["和面发酵","擀开抹油撒葱花","卷起切段","蒸15分钟"]','["二次醒发后再蒸"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='花卷');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cStaple,'蛋炒饭','','粒粒分明','主食,家常','15 分钟','简单','1 人份','450 千卡','[{"name":"米饭","amount":"1碗"},{"name":"鸡蛋","amount":"2个"},{"name":"葱花","amount":"适量"},{"name":"盐","amount":"适量"}]','["鸡蛋炒散","加米饭翻炒","加盐和葱花","炒匀出锅"]','["用隔夜饭更好"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='蛋炒饭');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cStaple,'阳春面','','清汤细面','主食,面食','15 分钟','简单','1 人份','320 千卡','[{"name":"细面条","amount":"100g"},{"name":"猪油","amount":"1勺"},{"name":"生抽","amount":"1勺"},{"name":"葱花","amount":"适量"}]','["碗中放猪油生抽和葱花","面条煮熟","舀面汤冲开调料","放入面条"]','["猪油是灵魂"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='阳春面');

-- ================= 5. 烤肉 @cGrill =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cGrill,'韩式烤五花肉','','配生菜蘸酱','烤肉,韩式','30 分钟','简单','2 人份','650 千卡','[{"name":"五花肉","amount":"400g"},{"name":"生菜","amount":"1把"},{"name":"韩式蘸酱","amount":"适量"},{"name":"蒜片","amount":"适量"}]','["五花肉切片","烤盘烤至两面金黄","用生菜包肉加蒜片蘸酱"]','["五花肉冷冻后更好切"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='韩式烤五花肉');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cGrill,'孜然羊肉串','','孜然香辣','烤肉,烧烤','40 分钟','中等','3 人份','550 千卡','[{"name":"羊肉","amount":"500g"},{"name":"孜然粉","amount":"2勺"},{"name":"辣椒粉","amount":"1勺"},{"name":"洋葱","amount":"半个"}]','["羊肉切块用洋葱腌制","串成串","烤至变色撒孜然辣椒粉","再烤2分钟"]','["腌制时加少许油"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='孜然羊肉串');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cGrill,'烤鸡翅','','奥尔良风味','烤肉,烤翅','40 分钟','简单','2 人份','480 千卡','[{"name":"鸡翅","amount":"8个"},{"name":"奥尔良腌料","amount":"30g"},{"name":"蜂蜜","amount":"1勺"}]','["鸡翅划刀腌制2小时","烤箱200度烤20分钟","刷蜂蜜再烤5分钟"]','["腌制时间越长越入味"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='烤鸡翅');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cGrill,'烤牛肉','','嫩烤牛肉','烤肉,牛肉','30 分钟','中等','2 人份','520 千卡','[{"name":"牛肉","amount":"300g"},{"name":"黑胡椒","amount":"适量"},{"name":"盐","amount":"适量"},{"name":"橄榄油","amount":"1勺"}]','["牛肉切片用黑胡椒盐橄榄油腌制","烤盘大火快烤","两面变色即可"]','["不要烤太久保持嫩度"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='烤牛肉');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cGrill,'烤蔬菜拼盘','','菌菇时蔬','烤肉,素食','25 分钟','简单','2 人份','180 千卡','[{"name":"香菇","amount":"6朵"},{"name":"金针菇","amount":"1把"},{"name":"青椒","amount":"1个"},{"name":"洋葱","amount":"半个"}]','["蔬菜洗净切好","刷油撒盐和黑胡椒","烤15分钟"]','["金针菇容易熟后放"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='烤蔬菜拼盘');

-- ================= 6. 清淡小炒 @cLight =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cLight,'清炒时蔬','','当季时蔬','清淡,小炒','10 分钟','简单','2 人份','120 千卡','[{"name":"时令蔬菜","amount":"400g"},{"name":"蒜","amount":"2瓣"},{"name":"盐","amount":"适量"}]','["蔬菜洗净切好","蒜爆香","下蔬菜大火快炒","加盐调味"]','["大火快炒保持脆嫩"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='清炒时蔬');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cLight,'蒜蓉西兰花','','蒜香清爽','清淡,小炒','15 分钟','简单','2 人份','140 千卡','[{"name":"西兰花","amount":"1颗"},{"name":"蒜","amount":"4瓣"},{"name":"盐","amount":"适量"}]','["西兰花掰小朵焯水","蒜爆香","下西兰花翻炒","加盐调味"]','["焯水时加少许盐和油"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='蒜蓉西兰花');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cLight,'西芹百合','','清脆爽口','清淡,小炒','15 分钟','简单','2 人份','130 千卡','[{"name":"西芹","amount":"200g"},{"name":"百合","amount":"100g"},{"name":"盐","amount":"适量"}]','["西芹切段百合掰开","焯水","大火快炒加盐"]','["百合最后放"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='西芹百合');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cLight,'番茄炒蛋','','家常酸甜','清淡,家常','15 分钟','简单','2 人份','220 千卡','[{"name":"番茄","amount":"2个"},{"name":"鸡蛋","amount":"3个"},{"name":"盐","amount":"适量"},{"name":"糖","amount":"1小勺"}]','["鸡蛋炒散盛出","番茄炒出汁","加鸡蛋翻炒","加盐和糖调味"]','["加少许糖提鲜"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='番茄炒蛋');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cLight,'木耳炒山药','','爽脆养生','清淡,小炒','20 分钟','简单','2 人份','160 千卡','[{"name":"山药","amount":"200g"},{"name":"木耳","amount":"30g"},{"name":"蒜","amount":"2瓣"},{"name":"盐","amount":"适量"}]','["山药切片木耳泡发","焯水","蒜爆香后翻炒","加盐调味"]','["山药焯水去黏液"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='木耳炒山药');

-- ================= 7. 甜品糖水 @cSweet =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSweet,'红豆沙','','绵密香甜','甜品,糖水','60 分钟','中等','3 人份','220 千卡','[{"name":"红豆","amount":"200g"},{"name":"冰糖","amount":"50g"},{"name":"水","amount":"800ml"}]','["红豆浸泡2小时","加水煮至软烂","加冰糖搅拌融化"]','["红豆浸泡后更易煮烂"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='红豆沙');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSweet,'银耳莲子羹','','润燥甜羹','甜品,糖水','60 分钟','中等','3 人份','180 千卡','[{"name":"银耳","amount":"1朵"},{"name":"莲子","amount":"30g"},{"name":"红枣","amount":"6颗"},{"name":"冰糖","amount":"30g"}]','["银耳泡发撕小朵","加莲子和红枣炖40分钟","加冰糖再炖10分钟"]','["银耳撕小朵易出胶"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='银耳莲子羹');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSweet,'芒果西米露','','芒果椰香','甜品,糖水','30 分钟','简单','2 人份','260 千卡','[{"name":"芒果","amount":"2个"},{"name":"西米","amount":"50g"},{"name":"椰浆","amount":"200ml"},{"name":"糖","amount":"适量"}]','["西米煮至透明过凉水","芒果切块","椰浆加糖和芒果西米拌匀"]','["西米煮好后过凉水更Q弹"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='芒果西米露');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSweet,'杨枝甘露','','港式甜品','甜品,糖水','30 分钟','中等','2 人份','300 千卡','[{"name":"芒果","amount":"2个"},{"name":"西柚","amount":"半个"},{"name":"西米","amount":"50g"},{"name":"椰浆","amount":"200ml"}]','["西米煮熟","芒果打泥","混合椰浆和西米","加芒果块和西柚粒"]','["西柚粒最后放"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='杨枝甘露');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSweet,'冰糖雪梨','','润肺清甜','甜品,糖水','40 分钟','简单','2 人份','120 千卡','[{"name":"雪梨","amount":"2个"},{"name":"冰糖","amount":"30g"},{"name":"枸杞","amount":"适量"}]','["雪梨切块","加水加冰糖炖30分钟","加枸杞再炖5分钟"]','["雪梨去皮口感更好"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='冰糖雪梨');

-- ================= 8. 小菜蘸料 @cSauce =================
INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSauce,'蒜泥','','蒜香蘸料','蘸料,小菜','5 分钟','简单','2 人份','30 千卡','[{"name":"蒜","amount":"5瓣"},{"name":"盐","amount":"适量"},{"name":"香油","amount":"1勺"}]','["蒜捣成泥","加盐和香油拌匀"]','["捣蒜比切蒜更香"]','0',1,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='蒜泥');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSauce,'辣椒油','','香辣红油','蘸料,辣','15 分钟','简单','4 人份','180 千卡','[{"name":"辣椒粉","amount":"50g"},{"name":"油","amount":"200ml"},{"name":"白芝麻","amount":"1勺"},{"name":"八角","amount":"2个"}]','["辣椒粉和白芝麻放碗中","油烧热加八角炸香","热油泼入辣椒粉","拌匀冷却"]','["油温不要太高防糊"]','0',2,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='辣椒油');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSauce,'芝麻酱','','浓香芝麻','蘸料,小菜','5 分钟','简单','2 人份','150 千卡','[{"name":"芝麻酱","amount":"2勺"},{"name":"温水","amount":"适量"},{"name":"生抽","amount":"1勺"},{"name":"醋","amount":"1勺"}]','["芝麻酱加温水澥开","加生抽和醋拌匀"]','["温水少量多次加"]','0',3,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='芝麻酱');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSauce,'酱油醋汁','','清爽蘸汁','蘸料,小菜','3 分钟','简单','2 人份','20 千卡','[{"name":"生抽","amount":"2勺"},{"name":"香醋","amount":"1勺"},{"name":"香油","amount":"几滴"},{"name":"小米辣","amount":"1个"}]','["所有调料混合","加小米辣圈"]','["现调现用"]','0',4,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='酱油醋汁');

INSERT INTO meal_dish (dept_id,category_id,name,cover,description,tags,duration,level,serve,kcal,ingredients,steps,tips,status,sort,source,del_flag,create_by,create_time,remark)
SELECT 0,@cSauce,'韩式蘸酱','','烤肉蘸酱','蘸料,韩式','5 分钟','简单','2 人份','80 千卡','[{"name":"韩式大酱","amount":"2勺"},{"name":"香油","amount":"1勺"},{"name":"蒜末","amount":"1小勺"},{"name":"白糖","amount":"1小勺"}]','["所有材料混合拌匀"]','["可加少许雪碧调稀"]','0',5,'0','0','admin',sysdate(),'公共菜谱'
WHERE NOT EXISTS (SELECT 1 FROM meal_dish WHERE dept_id=0 AND name='韩式蘸酱');
