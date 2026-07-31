-- ----------------------------
-- 博客模块建表脚本
--
-- 文章的真源是 GitHub 仓库里的 markdown 文件，blog_article 只是本地索引：
-- 列表分页、搜索、排序走这张表，正文按需实时从 GitHub 拉取。
-- 索引通过「手动同步」和 GitHub push webhook 两种方式与仓库对齐。
--
-- 库名 ry-vue，字符集 utf8mb4。执行方式：
--   mysql -u root -p ry-vue < blog_20260730.sql
-- ----------------------------

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
-- 3、参数配置
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
-- 4、菜单与权限
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
