-- ----------------------------
-- 家庭点餐模块建表与菜单脚本
--
-- 六张新表：
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
--   mysql -u root -p howe-system < meal_20260922.sql
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」，
-- 并给家庭成员账号分配「点餐员 / 厨师 / 家庭管理员」角色。
-- ----------------------------

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
  accept_by    varchar(64)  default ''                 comment '接单人',
  accept_time  datetime                                comment '接单时间',
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
