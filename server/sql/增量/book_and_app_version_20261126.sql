-- 读书模块及共享版本管理增量。
-- 最终结构已写入 base.sql（共享 App 版本和发版通知）及 book.sql（读书权限、菜单及数据）。
-- 适用：已执行 base.sql 的 howe-system 库，包括已运行本增量旧版的数据库。
-- 版本表改名须与新后端一起上线。若两个版本表同时存在，RENAME TABLE 会失败并保留两边数据，先核对数据再继续。
-- 执行：mysql --default-character-set=utf8mb4 -u root -p howe-system < 增量/book_and_app_version_20261126.sql
-- 本增量执行结束可删除。

-- 原表直接改名，保留主键、自增序列、状态和所有渠道数据。
SET @app_version_rename = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'meal_app_version'),
    'RENAME TABLE meal_app_version TO app_version',
    'DO 0'
);
PREPARE app_version_rename_stmt FROM @app_version_rename;
EXECUTE app_version_rename_stmt;
DEALLOCATE PREPARE app_version_rename_stmt;

create table if not exists app_version (
  version_id   bigint(20)   not null auto_increment    comment '版本记录ID',
  app_key      varchar(24)  not null default 'meal'    comment '应用渠道',
  version_name varchar(32)  not null                   comment '版本名称（展示用）',
  version_code int(11)      not null                   comment '版本序号（比较基准，整数递增）',
  wgt_url      varchar(500) not null                   comment 'wgt 资源包地址（R2）',
  wgt_size     bigint(20)   default 0                  comment '资源包字节大小',
  file_name    varchar(255) default ''                 comment '上传时的原始文件名',
  update_log   text                                    comment '更新说明',
  force_update char(1)      default '0'                comment '是否强制更新（0否 1是）',
  status       char(1)      default '0'                comment '发布状态（0已发布 1停用）',
  del_flag     char(1)      default '0'                comment '删除标记（0存在 2删除）',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime                                comment '创建时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500) default null               comment '备注',
  primary key (version_id),
  key idx_app_version_code (version_code, status, del_flag),
  key idx_app_version_channel (app_key, status, del_flag, version_code)
) engine=innodb auto_increment=1 comment = '共享 App 版本（wgt 热更新）';

-- 兼容尚未引入渠道字段的旧点餐版本表。
SET @app_channel_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'app_version' AND column_name = 'app_key'),
    'DO 0',
    'ALTER TABLE app_version ADD COLUMN app_key VARCHAR(24) NOT NULL DEFAULT ''meal'' AFTER version_id'
);
PREPARE app_channel_stmt FROM @app_channel_ddl;
EXECUTE app_channel_stmt;
DEALLOCATE PREPARE app_channel_stmt;

SET @app_channel_index_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'app_version' AND index_name = 'idx_app_version_channel'),
    'DO 0',
    'ALTER TABLE app_version ADD INDEX idx_app_version_channel (app_key, status, del_flag, version_code)'
);
PREPARE app_channel_index_stmt FROM @app_channel_index_ddl;
EXECUTE app_channel_index_stmt;
DEALLOCATE PREPARE app_channel_index_stmt;

-- 兼容旧索引名。表名改完后，索引保持与基准结构一致。
SET @app_version_index_ddl = CASE
    WHEN EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'app_version' AND index_name = 'idx_app_version_code') THEN 'DO 0'
    WHEN EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'app_version' AND index_name = 'idx_meal_app_version_code')
        THEN 'ALTER TABLE app_version RENAME INDEX idx_meal_app_version_code TO idx_app_version_code'
    ELSE 'ALTER TABLE app_version ADD INDEX idx_app_version_code (version_code, status, del_flag)'
END;
PREPARE app_version_index_stmt FROM @app_version_index_ddl;
EXECUTE app_version_index_stmt;
DEALLOCATE PREPARE app_version_index_stmt;

create table if not exists app_release_notify_config (
  config_id   bigint       not null auto_increment comment '配置ID',
  app_key     varchar(64)  not null                comment '应用标识',
  label       varchar(80)  not null default ''     comment '群备注',
  webhook_key varchar(1024) not null               comment '加密的群机器人 Webhook Key',
  enabled     tinyint      not null default 0      comment '是否启用（0停用 1启用）',
  primary key (config_id),
  key idx_app_release_notify_app (app_key, enabled)
) engine=innodb default charset=utf8mb4 comment='App 发版通知机器人配置';

-- 登记共享版本菜单和通用权限，已有菜单和授权原地保留。
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT 'App 版本管理', parent.menu_id, 6, 'appversion', 'tool/appversion/index', 1, 0, 'C', '0', '0', 'tool:appVersion:list', 'download', 'admin', NOW()
FROM sys_menu parent
WHERE parent.parent_id = 0 AND parent.path = 'tool' AND parent.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.component = 'tool/appversion/index')
LIMIT 1;

SET @shared_app_version_menu = (SELECT MIN(menu_id) FROM sys_menu WHERE component = 'tool/appversion/index');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT action.name, @shared_app_version_menu, action.position, '#', 1, 0, 'F', '0', '0', action.permission, '#', 'admin', NOW()
FROM (
    SELECT '版本查询' AS name, 1 AS position, 'tool:appVersion:query' AS permission
    UNION ALL SELECT '版本新增', 2, 'tool:appVersion:add'
    UNION ALL SELECT '版本修改', 3, 'tool:appVersion:edit'
    UNION ALL SELECT '版本删除', 4, 'tool:appVersion:remove'
    UNION ALL SELECT '资源包上传', 5, 'tool:appVersion:upload'
    UNION ALL SELECT '管理宝宝渠道', 6, 'tool:appVersion:pregnancy'
) action
WHERE @shared_app_version_menu IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id = @shared_app_version_menu AND existing.perms = action.permission);

-- 旧孕期配置仍只用于孕期提醒，保留开关和凭证，只更新参数说明。
update sys_config
set remark = '用于孕期提醒。填写企业微信群机器人 Webhook 的 key 参数'
where config_key = 'pregnancy.wecom.webhookKey'
  and remark like '%宝宝 App 发版%';

update sys_config
set remark = '孕期提醒的企微通道总开关（0 停用，1 启用）'
where config_key = 'pregnancy.wecom.enabled'
  and remark like '%宝宝 App 发版%';

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
    KEY idx_book_node_children (user_id, parent_id, deleted_at, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='读书笔记节点';

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
       '管理读书后台节点，可跨账号查看和操作读书节点'
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
