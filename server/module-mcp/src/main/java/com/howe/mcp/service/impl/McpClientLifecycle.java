package com.howe.mcp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 客户端信任和管理状态收紧时采用相同失效顺序，不保留可恢复的旧凭据。 */
@Slf4j
final class McpClientLifecycle {
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
        jdbc.update("delete from oauth2_authorization_consent where registered_client_id=?", client);
        jdbc.update("""
                update mcp_token_index set status='REVOKED',update_time=current_timestamp
                 where grant_id in (select grant_id from mcp_grant where registered_client_id=?)
                    or authorization_id in (select id from oauth2_authorization where registered_client_id=?)
                """, client, client);
    }
}
