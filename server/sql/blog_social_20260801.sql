-- ----------------------------
-- 博客社交模块建表脚本（友链 / 朋友圈 / 说说）
--
-- 三张新表：
--   blog_link       站点表，link_type 区分 1=友链 2=RSS订阅源。二者语义独立、互不关联，
--                   共表只为省掉重复的表与 Mapper；权限边界在 Controller 层（双 Controller）
--   blog_feed_item  朋友圈抓取条目，只存标题/作者/链接/摘要/发布时间，不存正文
--   blog_talk       说说，正文存 markdown 原文
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < blog_social_20260801.sql
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」，
-- 并去「系统管理 > 字典管理」确认 blog_link_group 已生效。
-- ----------------------------

-- ----------------------------
-- 1、博客站点表（友链与RSS订阅源）
-- ----------------------------
drop table if exists blog_link;
create table blog_link (
  link_id           bigint(20)      not null auto_increment    comment '主键ID',
  link_type         char(1)         not null default '1'       comment '类型（1友链 2RSS订阅源）',
  link_name         varchar(128)    not null                   comment '站点名称',
  link_url          varchar(500)    default ''                 comment '站点地址',
  avatar            varchar(500)    default ''                 comment '头像/图标地址',
  descr             varchar(500)    default ''                 comment '站点描述',
  group_code        varchar(64)     default ''                 comment '友链分组（字典 blog_link_group，仅 link_type=1 使用）',
  rss_url           varchar(500)    default ''                 comment 'RSS/Atom 订阅地址（仅 link_type=2 使用）',
  last_sync_time    datetime                                   comment '最后同步时间（仅 link_type=2）',
  last_error        varchar(500)    default ''                 comment '最后一次同步失败原因，成功时清空（仅 link_type=2）',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  order_num         int(4)          default 0                  comment '显示顺序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (link_id),
  key idx_blog_link_type (link_type, status)                    comment '管理端按type筛、公开接口按type+status筛，两类高频查询共用',
  key idx_blog_link_group (group_code)
) engine=innodb auto_increment=1 comment = '博客站点表（友链与RSS订阅源）';

-- ----------------------------
-- 2、博客朋友圈条目表
--
-- url 取 varchar(500) 是硬约束，不可放大：
-- InnoDB DYNAMIC 行格式单列索引上限 3072 bytes，utf8mb4 每字符最多 4 bytes，
-- 即最长 768 字符。改成 varchar(1000) 会导致建表直接失败。
--
-- 本表由机器写入，不需要 create_by/update_*/remark，只保留 create_time。
-- 条目永久累积，不因源站删文或缩短 RSS 输出长度而清除。
-- ----------------------------
drop table if exists blog_feed_item;
create table blog_feed_item (
  item_id           bigint(20)      not null auto_increment    comment '主键ID',
  link_id           bigint(20)      not null                   comment '来源订阅源ID（blog_link.link_id）',
  title             varchar(500)    not null                   comment '条目标题',
  author            varchar(128)    default ''                 comment '条目作者',
  url               varchar(500)    not null                   comment '条目原文链接（去重唯一键）',
  summary           varchar(500)    default ''                 comment '摘要纯文本（抓取时已剥离HTML并截断）',
  pub_date          datetime                                   comment '发布时间',
  create_time       datetime                                   comment '入库时间',
  primary key (item_id),
  unique key uk_blog_feed_item_url (url)                        comment '同一条目只入库一次，重复同步不产生重复行',
  key idx_blog_feed_item_link (link_id),
  key idx_blog_feed_item_date (pub_date)
) engine=innodb auto_increment=1 comment = '博客朋友圈条目表';

-- ----------------------------
-- 3、博客说说表
--
-- content 存 markdown 原文，不存渲染后的 HTML。
-- 管理端用 MarkdownEditor 录入，blog-ui 侧用 marked 渲染。
-- ----------------------------
drop table if exists blog_talk;
create table blog_talk (
  talk_id           bigint(20)      not null auto_increment    comment '主键ID',
  content           longtext                                   comment '正文（markdown 原文）',
  tags              varchar(500)    default ''                 comment '标签，多个用逗号分隔',
  pub_date          datetime                                   comment '发布时间',
  is_top            char(1)         default '0'                comment '是否置顶（0否 1是）',
  status            char(1)         default '0'                comment '状态（0发布 1隐藏）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (talk_id),
  key idx_blog_talk_top_date (is_top, pub_date)                 comment '匹配公开接口的 order by is_top desc, pub_date desc',
  key idx_blog_talk_status (status)
) engine=innodb auto_increment=1 comment = '博客说说表';

-- ----------------------------
-- 4、友链分组字典
--
-- 用字典而非自由文本，避免「技术」「技术 」「技术大佬」变成三个不同的组。
-- blog-ui 是静态站读不到字典表，所以公开接口返回时由后端翻译成组名下发。
-- 分组为空或字典项已被删除的友链会归入「其他」组并排在最后，不会从页面上消失。
-- ----------------------------
delete from sys_dict_data where dict_type = 'blog_link_group';
delete from sys_dict_type where dict_type = 'blog_link_group';

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
values ('友链分组', 'blog_link_group', '0', 'admin', sysdate(), '博客友链的分组，blog-ui 友链页按此分区展示');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
values (1, '好友', 'friend', 'blog_link_group', '', 'primary', 'N', '0', 'admin', sysdate(), '');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
values (2, '技术', 'tech', 'blog_link_group', '', 'success', 'N', '0', 'admin', sysdate(), '');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
values (3, '推荐', 'recommend', 'blog_link_group', '', 'warning', 'N', '0', 'admin', sysdate(), '');

-- ----------------------------
-- 5、RSS 抓取参数配置
--
-- 键名登记在 module-common 的 ConfigConstants，代码通过 ConfigUtils 读取，
-- 在「系统管理 > 参数设置」改完即时生效、不用重启容器。
-- ----------------------------
delete from sys_config where config_key in ('blog.feed.timeout', 'blog.feed.maxSize', 'blog.feed.summaryLength', 'blog.feed.userAgent');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-RSS抓取超时', 'blog.feed.timeout', '30000', 'Y', 'admin', sysdate(), '单个订阅源的连接与读取超时，单位毫秒');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-RSS响应体上限', 'blog.feed.maxSize', '5242880', 'Y', 'admin', sysdate(), '单位字节，超过即中断并按失败处理，防超大 RSS 撑爆内存');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-RSS摘要长度', 'blog.feed.summaryLength', '200', 'Y', 'admin', sysdate(), '摘要剥离 HTML 后截断的字数');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-RSS请求UA', 'blog.feed.userAgent', 'Mozilla/5.0 (compatible; HoweBlogBot/1.0; +https://www.wyantao.com)', 'Y', 'admin', sysdate(), '部分站点拒绝默认 UA');

-- ----------------------------
-- 6、菜单与权限
--
-- 不硬编码 menu_id：sys_menu 的 auto_increment 是 2000，写死会与后续自增撞车。
-- 「博客管理」目录已由 blog_20260730.sql 创建，这里按名称捕获其 ID，
-- 再用 LAST_INSERT_ID() 逐级捕获新建菜单的 ID。
-- ----------------------------
select @blogDirId := menu_id from sys_menu where menu_name = '博客管理' and parent_id = 0 limit 1;

-- 幂等保护：重复执行本脚本时先清掉上一轮的菜单与按钮，避免菜单树出现重复项。
-- 菜单与按钮的 perms 都以 blog:link:/blog:feed:/blog:talk: 开头，一条即可清干净。
delete from sys_menu where perms like 'blog:link:%' or perms like 'blog:feed:%' or perms like 'blog:talk:%';

-- 友链管理菜单
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('友链管理', @blogDirId, '3', 'link', 'blog/link/index', 1, 0, 'C', '0', '0', 'blog:link:list', 'peoples', 'admin', sysdate(), '友链管理菜单');

select @linkMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('友链查询', @linkMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'blog:link:query',  '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('友链新增', @linkMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'blog:link:add',    '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('友链修改', @linkMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'blog:link:edit',   '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('友链删除', @linkMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'blog:link:remove', '#', 'admin', sysdate(), '');

-- 朋友圈管理菜单
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('朋友圈管理', @blogDirId, '4', 'feed', 'blog/feed/index', 1, 0, 'C', '0', '0', 'blog:feed:list', 'rss', 'admin', sysdate(), 'RSS订阅源与抓取条目管理');

select @feedMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('订阅源查询', @feedMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'blog:feed:query',  '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('订阅源新增', @feedMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'blog:feed:add',    '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('订阅源修改', @feedMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'blog:feed:edit',   '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('订阅源删除', @feedMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'blog:feed:remove', '#', 'admin', sysdate(), '删除订阅源与抓取条目');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('手动同步', @feedMenuId, '5', '#', '', 1, 0, 'F', '0', '0', 'blog:feed:sync',   '#', 'admin', sysdate(), '立即抓取全部或单个订阅源');

-- 说说管理菜单
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('说说管理', @blogDirId, '5', 'talk', 'blog/talk/index', 1, 0, 'C', '0', '0', 'blog:talk:list', 'message', 'admin', sysdate(), '说说管理菜单');

select @talkMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('说说查询', @talkMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'blog:talk:query',  '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('说说新增', @talkMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'blog:talk:add',    '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('说说修改', @talkMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'blog:talk:edit',   '#', 'admin', sysdate(), '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('说说删除', @talkMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'blog:talk:remove', '#', 'admin', sysdate(), '');

-- ----------------------------
-- 7、定时任务
--
-- invoke_target 填 bean 名而非全限定类名：JobInvokeUtil 对不含包名的 target
-- 走 SpringUtils.getBean(beanName) 按运行时 bean 名解析，因此 blogFeedTask
-- 定义在 module-blog 即可被 module-quartz 调用，无需任何模块间依赖。
--
-- concurrent='1' 禁止并发，防止上一轮未跑完又被触发。
-- status='1' 默认停用——未配置订阅源时启用只会空跑，配置好后再去界面启用。
-- ----------------------------
delete from sys_job where invoke_target = 'blogFeedTask.sync()';

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values('博客朋友圈RSS同步', 'DEFAULT', 'blogFeedTask.sync()', '0 0 */2 * * ?', '3', '1', '1', 'admin', sysdate(), '每2小时抓取一次订阅源；默认停用，配置好订阅源后在界面启用');
