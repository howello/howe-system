CREATE TABLE IF NOT EXISTS book_node (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    node_type VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    cover_kind VARCHAR(16) NULL,
    cover_ref VARCHAR(512) NULL,
    content_json JSON NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_book_node_children (user_id, parent_id, deleted_at, sort_order, id),
    KEY idx_book_node_parent (parent_id, deleted_at, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='读书笔记节点';

-- 锁住固定的一行，串行化创建、移动和删除等共享树变更。
CREATE TABLE IF NOT EXISTS book_tree_lock (
    id TINYINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB COMMENT='共享书架写入锁';

INSERT IGNORE INTO book_tree_lock (id) VALUES (1);

-- 读书管理目录、节点菜单和操作权限。
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, is_frame, is_cache, visible, status, icon, create_by, create_time)
SELECT '读书管理', 0, 8, 'book', 'M', 1, 0, '0', '0', 'documentation', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'book');

SET @book_menu = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id = 0 AND path = 'book');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, is_frame, is_cache, visible, status, perms, icon, create_by, create_time)
SELECT '节点管理', @book_menu, 1, 'nodes', 'book/node/index', 'C', 1, 0, '0', '0', 'book:node:list', '#', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @book_menu AND path = 'nodes');

SET @book_nodes_menu = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id = @book_menu AND path = 'nodes');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, is_frame, is_cache, visible, status, perms, icon, create_by, create_time)
SELECT p.label, @book_nodes_menu, p.position, '#', 'F', 1, 0, '0', '0', p.permission, '#', 'admin', NOW()
FROM (
    SELECT '节点详情' AS label, 1 AS position, 'book:node:query' AS permission
    UNION ALL SELECT '新增节点', 2, 'book:node:add'
    UNION ALL SELECT '编辑节点', 3, 'book:node:edit'
    UNION ALL SELECT '删除节点', 4, 'book:node:remove'
) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.parent_id = @book_nodes_menu AND m.perms = p.permission);

-- 节点管理角色。通过用户管理按需授予，不自动给现有账号授权。
INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
SELECT '读书管理员', 'book_manager', 30, '1', 1, 1, '0', '0', 'admin', NOW(),
       '管理共享书架的后台节点'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'book_manager');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, menu.menu_id
FROM sys_role role
JOIN sys_menu menu ON menu.menu_id IN (@book_menu, @book_nodes_menu)
    OR (menu.parent_id = @book_nodes_menu AND menu.perms IN ('book:node:query', 'book:node:add', 'book:node:edit', 'book:node:remove'))
WHERE role.role_key = 'book_manager' AND role.del_flag = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = role.role_id AND existing.menu_id = menu.menu_id);

-- 读书 WGT 的渠道权限独立授予，节点管理员默认没有版本发布权限。
SET @book_app_version_menu = (SELECT MIN(menu_id) FROM sys_menu WHERE component = 'tool/appversion/index');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '管理读书渠道', @book_app_version_menu, 7, '#', 1, 0, 'F', '0', '0', 'tool:appVersion:book', '#', 'admin', NOW()
WHERE @book_app_version_menu IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @book_app_version_menu AND perms = 'tool:appVersion:book');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, param_type, `sensitive`)
SELECT '读书 App 发布令牌', 'book.app.publishToken', '', 'N', 'admin', NOW(),
       '仅用于读书 App 的 WGT 脚本发布，留空禁用', '读书', 'Y'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'book.app.publishToken');

-- 兼容已手工登记参数的库。保留当前值，后续通过参数维护接口保存时按敏感配置处理。
UPDATE sys_config SET `sensitive` = 'Y'
WHERE config_key = 'book.app.publishToken' AND `sensitive` <> 'Y';

-- 演示数据：共享书架的分类 / 书本 / 感悟 / 备注，创建人记录为 admin。
-- 固定 UUID 前缀，重复执行本文件时先清理旧演示数据，不影响真实节点。
DELETE FROM book_node
WHERE id LIKE '11111111-1111-4111-8111-%'
   OR id LIKE '22222222-2222-4222-8222-%'
   OR id LIKE '33333333-3333-4333-8333-%'
   OR id LIKE '44444444-4444-4444-8444-%';

-- 分类（第一层）
INSERT INTO book_node (id, user_id, parent_id, node_type, title, sort_order, cover_kind, cover_ref, content_json, version, created_at, updated_at, deleted_at) VALUES
('11111111-1111-4111-8111-111111111101', 1, NULL, 'category', '文学', 1, 'local', 'shared/cover-01.webp', '{"desc":"小说 · 散文","color":"clay","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL),
('11111111-1111-4111-8111-111111111102', 1, NULL, 'category', '认知', 2, 'local', 'shared/cover-02.webp', '{"desc":"思维 · 学习","color":"moss","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL),
('11111111-1111-4111-8111-111111111103', 1, NULL, 'category', '技术', 3, 'local', 'shared/cover-03.webp', '{"desc":"工程 · 架构","color":"navy","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL),
('11111111-1111-4111-8111-111111111104', 1, NULL, 'category', '哲学', 4, 'local', 'shared/cover-04.webp', '{"desc":"思辨 · 伦理","color":"plum","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL),
('11111111-1111-4111-8111-111111111105', 1, NULL, 'category', '历史', 5, 'local', 'shared/cover-05.webp', '{"desc":"断代 · 人物","color":"ochre","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL),
('11111111-1111-4111-8111-111111111106', 1, NULL, 'category', '生活', 6, 'local', 'shared/cover-06.webp', '{"desc":"身体 · 家庭","color":"teal","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL),
('11111111-1111-4111-8111-111111111107', 1, NULL, 'category', '诗集', 7, 'local', 'shared/cover-07.webp', '{"desc":"短句 · 抄写","color":"rose","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL),
('11111111-1111-4111-8111-111111111108', 1, NULL, 'category', '待分类', 8, 'local', 'shared/cover-08.webp', '{"desc":"还没归架","color":"sand","art":"plain"}', 1, '2026-11-18 21:00:00.000', '2026-11-18 21:00:00.000', NULL);

-- 书本（第二层）
INSERT INTO book_node (id, user_id, parent_id, node_type, title, sort_order, cover_kind, cover_ref, content_json, version, created_at, updated_at, deleted_at) VALUES
('22222222-2222-4222-8222-222222222201', 1, '11111111-1111-4111-8111-111111111101', 'book', '活着', 1, 'local', 'shared/cover-09.webp', '{"author":"余华","translator":"","publisher":"作家出版社","year":"2012","pages":"191","isbn":"9787506365437","rating":"9.4","status":"已读完","progress":100,"intro":"人是为活着本身而活着的，而不是为了活着之外的任何事物所活着。","tags":["小说","中国当代","生命"],"color":"clay","art":"frame"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222202', 1, '11111111-1111-4111-8111-111111111101', 'book', '百年孤独', 2, 'local', 'shared/cover-10.webp', '{"author":"加西亚·马尔克斯","translator":"范晔","publisher":"南海出版公司","year":"2011","pages":"360","isbn":"9787544253994","rating":"9.3","status":"在读","progress":62,"intro":"多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。","tags":["拉美","魔幻现实"],"color":"moss","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222203', 1, '11111111-1111-4111-8111-111111111101', 'book', '我与地坛', 3, 'local', 'shared/cover-11.png', '{"author":"史铁生","translator":"","publisher":"人民文学出版社","year":"2010","pages":"234","isbn":"9787020083173","rating":"9.2","status":"已读完","progress":100,"intro":"一个人，出生了，这就不再是一个可以辩论的问题，而只是上帝交给他的一个事实。","tags":["散文","生命"],"color":"ochre","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222204', 1, '11111111-1111-4111-8111-111111111101', 'book', '局外人', 4, 'local', 'shared/cover-12.png', '{"author":"阿尔贝·加缪","translator":"柳鸣九","publisher":"上海译文出版社","year":"2013","pages":"144","isbn":"9787532761767","rating":"9.1","status":"已读完","progress":100,"intro":"今天，妈妈死了。也许是昨天，我不知道。","tags":["存在主义","中篇"],"color":"ink","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222205', 1, '11111111-1111-4111-8111-111111111101', 'book', '夜晚的潜水艇', 5, 'local', 'shared/cover-13.webp', '{"author":"陈春成","translator":"","publisher":"上海三联书店","year":"2020","pages":"312","isbn":"9787542669209","rating":"8.6","status":"在读","progress":45,"intro":"我想起一种可能：也许我们的一生，都只是在为某个尚未到来的夜晚做准备。","tags":["短篇集","幻想"],"color":"navy","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222206', 1, '11111111-1111-4111-8111-111111111101', 'book', '额尔古纳河右岸', 6, 'local', 'shared/cover-14.png', '{"author":"迟子建","translator":"","publisher":"人民文学出版社","year":"2019","pages":"288","isbn":"9787020151287","rating":"8.9","status":"在读","progress":78,"intro":"我是雨和雪的老熟人了，我有九十岁了。雨雪看老了我，我也把它们给看老了。","tags":["民族","长篇"],"color":"sand","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222207', 1, '11111111-1111-4111-8111-111111111101', 'book', '呼兰河传', 7, 'local', 'shared/cover-15.png', '{"author":"萧红","translator":"","publisher":"天津人民出版社","year":"2016","pages":"254","isbn":"9787201105114","rating":"8.8","status":"已读完","progress":100,"intro":"我家有一个大花园，这花园里蜂子、蝴蝶、蜻蜓、蚂蚱，样样都有。","tags":["乡土","回忆"],"color":"rose","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222208', 1, '11111111-1111-4111-8111-111111111102', 'book', '思考，快与慢', 1, 'local', 'shared/cover-16.png', '{"author":"丹尼尔·卡尼曼","translator":"胡晓姣","publisher":"中信出版社","year":"2012","pages":"448","isbn":"9787508633558","rating":"8.7","status":"在读","progress":34,"intro":"我们以为的理性判断，多数时候是系统一给出的快速答案。","tags":["认知","心理学"],"color":"slate","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222209', 1, '11111111-1111-4111-8111-111111111103', 'book', '代码整洁之道', 1, 'local', 'shared/cover-17.png', '{"author":"Robert C. Martin","translator":"韩磊","publisher":"人民邮电出版社","year":"2020","pages":"388","isbn":"9787115216878","rating":"8.9","status":"在读","progress":88,"intro":"代码的整洁度与团队的生产力，是一条缓慢下滑的曲线。","tags":["工程","规范"],"color":"teal","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL),
('22222222-2222-4222-8222-222222222210', 1, '11111111-1111-4111-8111-111111111103', 'book', '数据密集型应用系统设计', 2, 'local', 'shared/cover-18.png', '{"author":"Martin Kleppmann","translator":"赵军平","publisher":"中国电力出版社","year":"2018","pages":"514","isbn":"9787519821964","rating":"9.0","status":"在读","progress":41,"intro":"可靠、可扩展、可维护——三个词几乎概括了所有数据系统的争论。","tags":["分布式","存储"],"color":"navy","art":"plain"}', 1, '2026-11-18 21:01:00.000', '2026-11-18 21:01:00.000', NULL);

-- 感悟（第三层）
INSERT INTO book_node (id, user_id, parent_id, node_type, title, sort_order, cover_kind, cover_ref, content_json, version, created_at, updated_at, deleted_at) VALUES
('33333333-3333-4333-8333-333333333301', 1, '22222222-2222-4222-8222-222222222201', 'note', '活着本身就是意义', 1, NULL, NULL, '{"chapter":"第五章 · 有庆之死","text":"福贵把家珍背回家，一路上没说话。我忽然明白余华为什么不写他的心理活动——那种时候，人是没有心里的。所有情绪都被压进了动作里：背起来、走、放下、坐下。这些动词比任何形容都重。","summary":"福贵把家珍背回家，一路上没说话。我忽然明白余华为什么不写他的心理活动——那种时候，人是没有心里的。","blocks":[{"id":"b1","type":"text","text":"福贵把家珍背回家，一路上没说话。我忽然明白余华为什么不写他的心理活动——那种时候，人是没有心里的。"},{"id":"b2","type":"image","caption":"深夜读到这一段，书页折了角"},{"id":"b3","type":"text","text":"读到这里第一次哭。不是因为死了人，是因为活着的人还要继续喂牛、种地、吃饭。"}],"attachments":[]}', 1, '2026-11-18 22:14:00.000', '2026-11-18 22:14:00.000', NULL),
('33333333-3333-4333-8333-333333333302', 1, '22222222-2222-4222-8222-222222222201', 'note', '福贵与老牛', 2, NULL, NULL, '{"chapter":"第十章","text":"老牛有七八个名字，福贵一个一个喊，喊的都是他死去的人。这是全书最温柔也最残忍的一段。","summary":"老牛有七八个名字，福贵一个一个喊，喊的都是他死去的人。这是全书最温柔也最残忍的一段。","blocks":[{"id":"b1","type":"text","text":"老牛有七八个名字，福贵一个一个喊，喊的都是他死去的人。这是全书最温柔也最残忍的一段。"},{"id":"b2","type":"audio","duration":102,"text":"","url":""}],"attachments":[]}', 1, '2026-11-12 21:30:00.000', '2026-11-12 21:30:00.000', NULL),
('33333333-3333-4333-8333-333333333303', 1, '22222222-2222-4222-8222-222222222201', 'note', '余华谈写作：把苦难写成日常', 3, NULL, NULL, '{"chapter":"序言","text":"「写作让我拥有两条人生。」这句话被我抄在了扉页上。","summary":"「写作让我拥有两条人生。」这句话被我抄在了扉页上。","blocks":[{"id":"b1","type":"quote","text":"写作让我拥有两条人生。","page":87},{"id":"b2","type":"text","text":"这句话被我抄在了扉页上。"}],"attachments":[]}', 1, '2026-10-30 20:10:00.000', '2026-10-30 20:10:00.000', NULL),
('33333333-3333-4333-8333-333333333304', 1, '22222222-2222-4222-8222-222222222201', 'note', '第一遍读完的空白感', 4, NULL, NULL, '{"chapter":"","text":"合上书之后在阳台站了十分钟，说不上难过，只是觉得房间很静。","summary":"合上书之后在阳台站了十分钟，说不上难过，只是觉得房间很静。","blocks":[{"id":"b1","type":"text","text":"合上书之后在阳台站了十分钟，说不上难过，只是觉得房间很静。"}],"attachments":[]}', 1, '2026-10-26 23:05:00.000', '2026-10-26 23:05:00.000', NULL),
('33333333-3333-4333-8333-333333333305', 1, '22222222-2222-4222-8222-222222222203', 'note', '一个人，出生了，就不再是可以辩论的问题', 1, NULL, NULL, '{"chapter":"第一章","text":"一个人，出生了，这就不再是一个可以辩论的问题，而只是上帝交给他的一个事实。","summary":"一个人，出生了，这就不再是一个可以辩论的问题，而只是上帝交给他的一个事实。","blocks":[{"id":"b1","type":"text","text":"一个人，出生了，这就不再是一个可以辩论的问题，而只是上帝交给他的一个事实。"},{"id":"b2","type":"quote","text":"死是一件不必急于求成的事，死是一个必然会降临的节日。","page":12}],"attachments":[]}', 1, '2026-11-02 19:40:00.000', '2026-11-02 19:40:00.000', NULL),
('33333333-3333-4333-8333-333333333306', 1, '22222222-2222-4222-8222-222222222203', 'note', '园子里的四季', 2, NULL, NULL, '{"chapter":"第三章","text":"地坛的四季不是背景，是他用来安放自己的容器。","summary":"地坛的四季不是背景，是他用来安放自己的容器。","blocks":[{"id":"b1","type":"text","text":"地坛的四季不是背景，是他用来安放自己的容器。"}],"attachments":[]}', 1, '2026-11-06 18:20:00.000', '2026-11-06 18:20:00.000', NULL),
('33333333-3333-4333-8333-333333333307', 1, '22222222-2222-4222-8222-222222222202', 'note', '冰块与行刑队', 1, NULL, NULL, '{"chapter":"第一章","text":"多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。","summary":"多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。","blocks":[{"id":"b1","type":"quote","text":"多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。","page":1}],"attachments":[]}', 1, '2026-11-08 22:00:00.000', '2026-11-08 22:00:00.000', NULL),
('33333333-3333-4333-8333-333333333308', 1, '22222222-2222-4222-8222-222222222204', 'note', '今天，妈妈死了', 1, NULL, NULL, '{"chapter":"第一部","text":"今天，妈妈死了。也许是昨天，我不知道。开篇就把情绪抽干了。","summary":"今天，妈妈死了。也许是昨天，我不知道。开篇就把情绪抽干了。","blocks":[{"id":"b1","type":"text","text":"今天，妈妈死了。也许是昨天，我不知道。开篇就把情绪抽干了。"}],"attachments":[]}', 1, '2026-10-20 21:00:00.000', '2026-10-20 21:00:00.000', NULL),
('33333333-3333-4333-8333-333333333309', 1, '22222222-2222-4222-8222-222222222205', 'note', '为尚未到来的夜晚做准备', 1, NULL, NULL, '{"chapter":"夜晚的潜水艇","text":"我想起一种可能：也许我们的一生，都只是在为某个尚未到来的夜晚做准备。","summary":"也许我们的一生，都只是在为某个尚未到来的夜晚做准备。","blocks":[{"id":"b1","type":"text","text":"我想起一种可能：也许我们的一生，都只是在为某个尚未到来的夜晚做准备。"}],"attachments":[]}', 1, '2026-11-11 22:40:00.000', '2026-11-11 22:40:00.000', NULL),
('33333333-3333-4333-8333-333333333310', 1, '22222222-2222-4222-8222-222222222206', 'note', '雨雪看老了我', 1, NULL, NULL, '{"chapter":"清晨","text":"我是雨和雪的老熟人了，我有九十岁了。雨雪看老了我，我也把它们给看老了。","summary":"我是雨和雪的老熟人了，我有九十岁了。","blocks":[{"id":"b1","type":"quote","text":"我是雨和雪的老熟人了，我有九十岁了。雨雪看老了我，我也把它们给看老了。","page":1}],"attachments":[]}', 1, '2026-11-05 07:30:00.000', '2026-11-05 07:30:00.000', NULL),
('33333333-3333-4333-8333-333333333311', 1, '22222222-2222-4222-8222-222222222207', 'note', '我家有一个大花园', 1, NULL, NULL, '{"chapter":"第一章","text":"我家有一个大花园，这花园里蜂子、蝴蝶、蜻蜓、蚂蚱，样样都有。","summary":"我家有一个大花园，这花园里蜂子、蝴蝶、蜻蜓、蚂蚱，样样都有。","blocks":[{"id":"b1","type":"text","text":"我家有一个大花园，这花园里蜂子、蝴蝶、蜻蜓、蚂蚱，样样都有。"}],"attachments":[]}', 1, '2026-10-18 20:00:00.000', '2026-10-18 20:00:00.000', NULL),
('33333333-3333-4333-8333-333333333312', 1, '22222222-2222-4222-8222-222222222208', 'note', '系统一给你的快速答案', 1, NULL, NULL, '{"chapter":"第二章","text":"我们以为的理性判断，多数时候是系统一给出的快速答案。","summary":"我们以为的理性判断，多数时候是系统一给出的快速答案。","blocks":[{"id":"b1","type":"text","text":"我们以为的理性判断，多数时候是系统一给出的快速答案。"}],"attachments":[]}', 1, '2026-11-14 20:15:00.000', '2026-11-14 20:15:00.000', NULL),
('33333333-3333-4333-8333-333333333313', 1, '22222222-2222-4222-8222-222222222209', 'note', '整洁度与生产力', 1, NULL, NULL, '{"chapter":"第一章","text":"代码的整洁度与团队的生产力，是一条缓慢下滑的曲线。","summary":"代码的整洁度与团队的生产力，是一条缓慢下滑的曲线。","blocks":[{"id":"b1","type":"text","text":"代码的整洁度与团队的生产力，是一条缓慢下滑的曲线。"}],"attachments":[]}', 1, '2026-11-16 21:10:00.000', '2026-11-16 21:10:00.000', NULL),
('33333333-3333-4333-8333-333333333314', 1, '22222222-2222-4222-8222-222222222210', 'note', '可靠、可扩展、可维护', 1, NULL, NULL, '{"chapter":"第一章","text":"可靠、可扩展、可维护——三个词几乎概括了所有数据系统的争论。","summary":"可靠、可扩展、可维护——三个词几乎概括了所有数据系统的争论。","blocks":[{"id":"b1","type":"text","text":"可靠、可扩展、可维护——三个词几乎概括了所有数据系统的争论。"}],"attachments":[]}', 1, '2026-11-17 21:00:00.000', '2026-11-17 21:00:00.000', NULL);

-- 备注（第四层起，含五级连续下钻：n1 → r1 → r2 → r3 → r5）
INSERT INTO book_node (id, user_id, parent_id, node_type, title, sort_order, cover_kind, cover_ref, content_json, version, created_at, updated_at, deleted_at) VALUES
('44444444-4444-4444-8444-444444444401', 1, '33333333-3333-4333-8333-333333333301', 'remark', '「人是没有心里的」', 1, NULL, NULL, '{"text":"「人是没有心里的」——这句我想了很久，是不是说极端处境下语言会失效？"}', 1, '2026-11-18 22:31:00.000', '2026-11-18 22:31:00.000', NULL),
('44444444-4444-4444-8444-444444444402', 1, '44444444-4444-4444-8444-444444444401', 'remark', '余华说过写苦难不用形容词', 1, NULL, NULL, '{"text":"对，余华自己说过：写苦难时他故意不用形容词，只用动作和对话。"}', 1, '2026-11-18 22:40:00.000', '2026-11-18 22:40:00.000', NULL),
('44444444-4444-4444-8444-444444444403', 1, '44444444-4444-4444-8444-444444444402', 'remark', '《巨大的差距里》也提过类似写法', 1, NULL, NULL, '{"text":"补：他在《我们生活在巨大的差距里》也提过类似的写法，可以找来对照。"}', 1, '2026-11-19 09:12:00.000', '2026-11-19 09:12:00.000', NULL),
('44444444-4444-4444-8444-444444444405', 1, '44444444-4444-4444-8444-444444444403', 'remark', '第 87 页访谈几乎原话', 1, NULL, NULL, '{"text":"找到了，第 87 页那段访谈几乎原话复述，可以当引文直接抄。"}', 1, '2026-11-19 21:07:00.000', '2026-11-19 21:07:00.000', NULL),
('44444444-4444-4444-8444-444444444404', 1, '44444444-4444-4444-8444-444444444401', 'remark', '想到《局外人》的默尔索', 2, NULL, NULL, '{"text":"另外想到《局外人》的默尔索，同样是把情绪抽干的写法，但加缪是冷的，余华是热的。"}', 1, '2026-11-19 09:20:00.000', '2026-11-19 09:20:00.000', NULL),
('44444444-4444-4444-8444-444444444406', 1, '33333333-3333-4333-8333-333333333301', 'remark', '第五章节奏变了', 2, NULL, NULL, '{"text":"第五章这一段的节奏和前四章不一样，句子明显变短了。"}', 1, '2026-11-20 20:05:00.000', '2026-11-20 20:05:00.000', NULL),
('44444444-4444-4444-8444-444444444407', 1, '33333333-3333-4333-8333-333333333302', 'remark', '老牛的名字都是他死去的人', 1, NULL, NULL, '{"text":"老牛的名字都是他死去的人，念一遍名字，就是把一生又过了一遍。"}', 1, '2026-11-13 08:10:00.000', '2026-11-13 08:10:00.000', NULL),
('44444444-4444-4444-8444-444444444408', 1, '33333333-3333-4333-8333-333333333305', 'remark', '这句抄在扉页', 1, NULL, NULL, '{"text":"这句抄在扉页，每次翻开都看见。"}', 1, '2026-11-03 08:00:00.000', '2026-11-03 08:00:00.000', NULL),
('44444444-4444-4444-8444-444444444409', 1, '33333333-3333-4333-8333-333333333307', 'remark', '开篇的时间结构', 1, NULL, NULL, '{"text":"一个句子同时指向未来和过去，时间被折叠了。"}', 1, '2026-11-09 07:45:00.000', '2026-11-09 07:45:00.000', NULL),
('44444444-4444-4444-8444-444444444410', 1, '33333333-3333-4333-8333-333333333312', 'remark', '直觉多半不可信', 1, NULL, NULL, '{"text":"所以「直觉」多半是不可信的，但它又太快了，快到来不及怀疑。"}', 1, '2026-11-15 09:30:00.000', '2026-11-15 09:30:00.000', NULL),
('44444444-4444-4444-8444-444444444411', 1, '33333333-3333-4333-8333-333333333303', 'remark', '扉页上的那句话', 1, NULL, NULL, '{"text":"扉页上就是这句话，我每次重读都会停一下。"}', 1, '2026-11-01 10:00:00.000', '2026-11-01 10:00:00.000', NULL),
('44444444-4444-4444-8444-444444444412', 1, '33333333-3333-4333-8333-333333333306', 'remark', '容器这个比喻', 1, NULL, NULL, '{"text":"容器这个比喻很好，四季装着他的时间。"}', 1, '2026-11-07 09:00:00.000', '2026-11-07 09:00:00.000', NULL),
('44444444-4444-4444-8444-444444444413', 1, '33333333-3333-4333-8333-333333333314', 'remark', '三个词的分量', 1, NULL, NULL, '{"text":"三个词的分量在于，它们互相拉扯，不能同时做到最好。"}', 1, '2026-11-18 09:00:00.000', '2026-11-18 09:00:00.000', NULL);
