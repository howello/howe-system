-- 共享书架的写入锁与父节点索引，已写入 book.sql 的建表部分。
-- 适用于已存在 book_node 的数据库，本增量执行结束可删除。

CREATE TABLE IF NOT EXISTS book_tree_lock (
    id TINYINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB COMMENT='共享书架写入锁';

INSERT IGNORE INTO book_tree_lock (id) VALUES (1);

SET @book_parent_index_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'book_node' AND INDEX_NAME = 'idx_book_node_parent'
), 'SELECT 1', 'ALTER TABLE book_node ADD INDEX idx_book_node_parent (parent_id, deleted_at, sort_order, id)');
PREPARE book_parent_index_stmt FROM @book_parent_index_sql;
EXECUTE book_parent_index_stmt;
DEALLOCATE PREPARE book_parent_index_stmt;

UPDATE sys_role SET remark = '管理共享书架的后台节点'
WHERE role_key = 'book_manager' AND remark = '管理读书后台节点，可跨账号查看和操作读书节点';
