-- 微信读书同步增量脚本。
-- 最终结构与参数已写入 book.sql 的 book_node 建表及「微信读书同步配置」。
-- 适用于已有库，本增量执行结束可删除。

SET @weread_binding_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'book_node' AND column_name = 'weread_source_keys'),
    'SELECT 1',
    'ALTER TABLE book_node ADD COLUMN weread_source_keys JSON NULL COMMENT ''微信读书来源绑定键'' AFTER content_json'
);
PREPARE weread_binding_stmt FROM @weread_binding_ddl;
EXECUTE weread_binding_stmt;
DEALLOCATE PREPARE weread_binding_stmt;

-- 微信读书同步配置。重复执行保留已保存的值。
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark, param_type, `sensitive`)
SELECT '微信读书 API Key', 'book.weread.apiKey', '', 'N', 'admin', NOW(),
       '用于服务端书目查询和书架同步，留空停用微信读书接入', '读书', 'Y'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'book.weread.apiKey');

UPDATE sys_config SET `sensitive` = 'Y'
WHERE config_key = 'book.weread.apiKey' AND `sensitive` <> 'Y';
