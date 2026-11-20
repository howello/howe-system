package com.howe.mcp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

/** 客户端信任和管理状态收紧时采用相同失效顺序，不保留可恢复的旧凭据。 */
@Slf4j
final class McpClientLifecycle {
    static final int MAX_CLIENTS = 10000;
    private static final String LOCK_NAME = "concat('howe:mcp:registration:',left(sha2(database(),256),32))";

    /** 同一数据库内登记与清理串行，提交或回滚后在原连接释放锁。 */
    static void lockRegistration(JdbcOperations jdbc) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("客户端登记锁需要事务");
        }
        Integer acquired = jdbc.queryForObject("select get_lock(" + LOCK_NAME + ",2)", Integer.class);
        if (!Integer.valueOf(1).equals(acquired)) {
            throw new com.howe.mcp.service.security.McpRegistrationException(
                    "temporarily_unavailable", 503, "客户端登记暂不可用");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 清理事务仍绑定原连接时释放 MySQL 会话锁。 */
            @Override
            public void afterCompletion(int status) {
                jdbc.queryForObject("select release_lock(" + LOCK_NAME + ")", Integer.class);
            }
        });
    }

    /** 仅提供事务中的静态生命周期操作。 */
    private McpClientLifecycle() { }

    /** 调用方先持有客户端行锁，再按 grant、授权、令牌顺序使全部旧状态失效。 */
    public static void invalidate(JdbcOperations jdbc, String client) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("客户端失效必须在事务中执行");
        }
        jdbc.queryForList("select grant_id from mcp_grant where registered_client_id=? order by grant_id for update", client);
        jdbc.queryForList("select id from oauth2_authorization where registered_client_id=? order by id for update", client);
        jdbc.update("""
                update mcp_grant set status='1',authorization_version=authorization_version+1,
                       revoked_at=current_timestamp,update_time=current_timestamp
                 where registered_client_id=? and status='0'
                """, client);
        jdbc.update("""
                update mcp_token_index set status='REVOKED',update_time=current_timestamp
                 where grant_id in (select grant_id from mcp_grant where registered_client_id=?)
                    or authorization_id in (select id from oauth2_authorization where registered_client_id=?)
                """, client, client);
    }
}
