-- ============================================================
-- blog.sql — 博客模块脚本（module-blog）
--
-- 内容：文章/草稿索引表、友链/朋友圈/说说表，以及 blog.* 参数配置、菜单与权限。
--       由原 blog_20260730 / blog_social_20260801 / blog_link_enhance_20260816 /
--       blog_schedule_switch_20261008 及 optimize_20260731 的博客开放接口参数合并而来。
--
-- 文章的真源是 GitHub 仓库里的 markdown 文件，blog_article 只是本地索引：
-- 列表分页、搜索、排序走这张表，正文按需实时从 GitHub 拉取。
-- 索引通过「手动同步」和 GitHub push webhook 两种方式与仓库对齐。
--
-- 库名 howe-system，字符集 utf8mb4。执行方式：
--   mysql -u root -p howe-system < blog.sql
-- 依赖：需先执行 base.sql（本脚本会向 sys_menu / sys_config / sys_dict_* 写入数据）。
--
-- 执行后如果服务已在运行，需去「系统管理 > 参数设置」点一次「刷新缓存」，
-- 并去「系统管理 > 字典管理」确认 blog_link_group 已生效。
-- ============================================================

-- ----------------------------
-- 1、博客文章索引表
-- ----------------------------
drop table if exists blog_article;
create table blog_article (
  article_id        bigint(20)      not null auto_increment    comment '文章ID（本地索引主键）',
  slug              varchar(128)    not null                   comment '文章标识（frontmatter 的 id，决定 URL /article/{slug}）',
  title             varchar(255)    not null                   comment '文章标题',
  file_path         varchar(500)    not null                   comment '仓库内文件路径（如 src/content/blog/2026/07/foo.md）',
  git_sha           varchar(64)     default ''                 comment '文件 blob sha（GitHub 写操作的乐观锁凭据）',
  publish_date      datetime                                   comment '发布日期（frontmatter 的 date）',
  categories        varchar(255)    default ''                 comment '分类，多个用逗号分隔',
  tags              varchar(500)    default ''                 comment '标签，多个用逗号分隔',
  cover             varchar(500)    default ''                 comment '封面图地址',
  recommend         char(1)         default '0'                comment '是否推荐（0否 1是）',
  hide              char(1)         default '0'                comment '是否隐藏（0否 1是）',
  is_top            char(1)         default '0'                comment '是否置顶（0否 1是）',
  summary           varchar(1000)   default ''                 comment '摘要（取正文开头，仅列表展示用）',
  word_count        int(11)         default 0                  comment '正文字数',
  last_sync_time    datetime                                   comment '最后一次与仓库对齐的时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (article_id),
  unique key uk_blog_article_slug (slug)                        comment 'slug 决定文章 URL，必须全局唯一',
  unique key uk_blog_article_path (file_path)                   comment '一个仓库文件只对应一条索引',
  key idx_blog_article_date (publish_date)
) engine=innodb auto_increment=1 comment = '博客文章索引表';

-- ----------------------------
-- 2、博客草稿表
-- ----------------------------
drop table if exists blog_draft;
create table blog_draft (
  draft_id          bigint(20)      not null auto_increment    comment '草稿ID',
  title             varchar(255)    not null                   comment '标题',
  slug              varchar(128)    default ''                 comment '计划使用的文章标识，发布时作为 frontmatter 的 id',
  content           longtext                                   comment 'markdown 正文（不含 frontmatter）',
  categories        varchar(255)    default ''                 comment '分类，多个用逗号分隔',
  tags              varchar(500)    default ''                 comment '标签，多个用逗号分隔',
  cover             varchar(500)    default ''                 comment '封面图地址',
  publish_date      datetime                                   comment '计划发布日期',
  recommend         char(1)         default '0'                comment '是否推荐（0否 1是）',
  hide              char(1)         default '0'                comment '是否隐藏（0否 1是）',
  is_top            char(1)         default '0'                comment '是否置顶（0否 1是）',
  status            char(1)         default '0'                comment '状态（0草稿 1已发布）',
  published_path    varchar(500)    default ''                 comment '发布后对应的仓库文件路径',
  publish_time      datetime                                   comment '发布时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (draft_id),
  key idx_blog_draft_status (status)
) engine=innodb auto_increment=1 comment = '博客草稿表';

-- ----------------------------
-- 3、博客参数配置（存储 + GitHub）
--
-- 存储凭据与 GitHub 仓库信息都放在参数配置表里，可在「系统管理 > 参数设置」随时修改，
-- 改完即时生效（底层走 Redis 缓存，保存时会自动刷新），不需要重启服务、也不进 .env。
-- 执行完本脚本后如果服务已在运行，去「系统管理 > 参数设置」点一次「刷新缓存」。
--
-- config_type='Y' 表示系统内置，在界面上不可删除，避免误删导致上传功能失效。
-- ----------------------------

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('文件存储-存储类型', 'sys.storage.type', 'r2', 'Y', 'admin', sysdate(), '', null, 'r2 上传到 Cloudflare R2 图床；local 落本地磁盘（仅本机开发，容器重建会丢文件）');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('文件存储-R2端点地址', 'sys.storage.r2.endpoint', '', 'Y', 'admin', sysdate(), '', null, 'S3 兼容端点，形如 https://<accountId>.r2.cloudflarestorage.com');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('文件存储-R2访问密钥ID', 'sys.storage.r2.accessKeyId', '', 'Y', 'admin', sysdate(), '', null, 'R2 API 令牌的 Access Key ID');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('文件存储-R2访问密钥', 'sys.storage.r2.secretAccessKey', '', 'Y', 'admin', sysdate(), '', null, 'R2 API 令牌的 Secret Access Key');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('文件存储-R2存储桶', 'sys.storage.r2.bucket', '', 'Y', 'admin', sysdate(), '', null, 'R2 存储桶名称');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('文件存储-R2公开域名', 'sys.storage.r2.publicUrl', 'https://img.wyantao.com', 'Y', 'admin', sysdate(), '', null, '绑定到桶的公开访问域名，末尾不带斜杠');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('文件存储-R2对象键前缀', 'sys.storage.r2.keyPrefix', 'img', 'Y', 'admin', sysdate(), '', null, '对象键统一前缀，留空表示直接放在桶根目录。现有文章图片都在 img/ 下');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-GitHub接口地址', 'blog.github.apiBase', 'https://api.github.com', 'Y', 'admin', sysdate(), '', null, 'GitHub API 基址，自建 GitHub Enterprise 时才需要改');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-仓库拥有者', 'blog.github.owner', 'howello', 'Y', 'admin', sysdate(), '', null, 'GitHub 用户名或组织名');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-仓库名', 'blog.github.repo', 'Astro-Blog', 'Y', 'admin', sysdate(), '', null, '存放文章的仓库名');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-目标分支', 'blog.github.branch', 'main', 'Y', 'admin', sysdate(), '', null, '文章提交到哪个分支');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-访问令牌', 'blog.github.token', '', 'Y', 'admin', sysdate(), '', null, 'fine-grained PAT 需授予目标仓库 Contents 读写权限');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-文章目录', 'blog.github.contentDir', 'src/content/blog', 'Y', 'admin', sysdate(), '', null, '文章所在目录，相对仓库根');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-提交作者名', 'blog.github.committerName', '', 'Y', 'admin', sysdate(), '', null, '与邮箱都填了才生效，留空则用令牌所属账号');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-提交作者邮箱', 'blog.github.committerEmail', '', 'Y', 'admin', sysdate(), '', null, '与作者名都填了才生效，留空则用令牌所属账号');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-Webhook密钥', 'blog.github.webhookSecret', '', 'Y', 'admin', sysdate(), '', null, '与 GitHub 仓库 webhook 的 Secret 一致；留空则 webhook 接口拒绝所有请求');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-GitHub请求超时', 'blog.github.timeout', '30000', 'Y', 'admin', sysdate(), '', null, '单位毫秒');

-- ----------------------------
-- 4、博客菜单与权限（文章 / 草稿）
--
-- 不硬编码 menu_id：sys_menu 的 auto_increment 是 2000，写死 ID 会与后续自增撞车。
-- 用 LAST_INSERT_ID() 逐级捕获父级 ID。
-- ----------------------------

-- 博客管理目录
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('博客管理', '0', '5', 'blog', null, 1, 0, 'M', '0', '0', '', 'documentation', 'admin', sysdate(), '', null, '博客管理目录');

select @blogDirId := LAST_INSERT_ID();

-- 文章管理菜单
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('文章管理', @blogDirId, '1', 'article', 'blog/article/index', 1, 0, 'C', '0', '0', 'blog:article:list', 'form', 'admin', sysdate(), '', null, '文章管理菜单');

select @articleMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('文章查询', @articleMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'blog:article:query',  '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('文章新增', @articleMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'blog:article:add',    '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('文章修改', @articleMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'blog:article:edit',   '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('文章删除', @articleMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'blog:article:remove', '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('文章导出', @articleMenuId, '5', '#', '', 1, 0, 'F', '0', '0', 'blog:article:export', '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('文章同步', @articleMenuId, '6', '#', '', 1, 0, 'F', '0', '0', 'blog:article:sync',   '#', 'admin', sysdate(), '', null, '从 GitHub 全量重建索引');

-- 草稿管理菜单
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('草稿管理', @blogDirId, '2', 'draft', 'blog/draft/index', 1, 0, 'C', '0', '0', 'blog:draft:list', 'edit', 'admin', sysdate(), '', null, '草稿管理菜单');

select @draftMenuId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('草稿查询', @draftMenuId, '1', '#', '', 1, 0, 'F', '0', '0', 'blog:draft:query',   '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('草稿新增', @draftMenuId, '2', '#', '', 1, 0, 'F', '0', '0', 'blog:draft:add',     '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('草稿修改', @draftMenuId, '3', '#', '', 1, 0, 'F', '0', '0', 'blog:draft:edit',    '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('草稿删除', @draftMenuId, '4', '#', '', 1, 0, 'F', '0', '0', 'blog:draft:remove',  '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('草稿发布', @draftMenuId, '5', '#', '', 1, 0, 'F', '0', '0', 'blog:draft:publish', '#', 'admin', sysdate(), '', null, '把草稿提交到 GitHub 变成正式文章');

-- ----------------------------
-- 5、博客站点表（友链与 RSS 订阅源）
--
--   blog_link  站点表，link_type 区分 1=友链 2=RSS订阅源。二者语义独立、互不关联，
--              共表只为省掉重复的表与 Mapper；权限边界在 Controller 层（双 Controller）
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
-- 6、博客朋友圈条目表
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
-- 7、博客说说表
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
-- 8、友链分组字典
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
-- 9、RSS 抓取参数配置
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
-- 10、博客社交菜单与权限
--
-- 不硬编码 menu_id：sys_menu 的 auto_increment 是 2000，写死会与后续自增撞车。
-- 「博客管理」目录已由上面的「4、博客菜单与权限」创建，这里按名称捕获其 ID，
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
-- 11、waline 参数配置
-- ----------------------------
delete from sys_config where config_key in ('blog.waline.url', 'blog.waline.pageSize', 'blog.waline.timeout');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-waline评论端点', 'blog.waline.url', 'https://waline.wyantao.com/api/comment', 'Y', 'admin', sysdate(), 'waline 评论列表基础地址（不含查询参数）');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-waline每页条数', 'blog.waline.pageSize', '10', 'Y', 'admin', sysdate(), '单次请求拉取的评论条数');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-waline请求超时', 'blog.waline.timeout', '30000', 'Y', 'admin', sysdate(), '单位毫秒');

-- ----------------------------
-- 12、博客开放接口参数
--
-- POST /blog/open/article，匿名接口，靠请求头 X-Blog-Token 与下面的令牌比对放行。
-- 开关关闭或令牌为空时一律拒绝，不存在裸奔的写入口。
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-开放接口开关', 'blog.open.enabled', 'false', 'Y', 'admin', sysdate(), '', null, '是否允许站外工具调用 POST /blog/open/article 投稿');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-开放接口令牌', 'blog.open.token', '', 'Y', 'admin', sysdate(), '', null, '调用方需在请求头 X-Blog-Token 中带上该值。建议 32 位以上随机串，留空等同于关闭');

-- ----------------------------
-- 13、博客定时任务开关（替代原 quartz sys_job 的启停控制）
--
-- module-quartz 移除后，博客的两个同步任务改由 Spring @Scheduled 承载，
-- 原 sys_job.status 的启停语义改由下面两个参数控制（系统管理 > 参数设置）：
--   blog.feed.syncEnabled  朋友圈 RSS 同步（每 2 小时）     默认 false 停用
--   blog.link.syncEnabled  waline 友链申请同步（每 30 分钟） 默认 false 停用
--
-- 默认停用与原 sys_job 一致；配置好订阅源 / waline 后改成 true 即可启用。
-- ----------------------------
delete from sys_config where config_key in ('blog.feed.syncEnabled', 'blog.link.syncEnabled');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-朋友圈同步开关', 'blog.feed.syncEnabled', 'false', 'Y', 'admin', sysdate(), 'true 启用；每 2 小时抓取一次已启用的 RSS 订阅源');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
values ('博客-友链同步开关', 'blog.link.syncEnabled', 'false', 'Y', 'admin', sysdate(), 'true 启用；每 30 分钟同步 waline 友链申请留言');
