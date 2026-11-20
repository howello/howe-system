package com.howe.mcp.operation;

import com.howe.common.tool.exception.ToolException;
import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.security.McpInvocationContext;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 写操作使用的授权绑定解析。
 *
 * <p>只按操作与账号读取账本，再核对当前授权仍有效；内部
 * {@code registered_client_id} 来自 {@code mcp_grant}，不使用外部 client_id。</p>
 */
@RequiredArgsConstructor
public final class McpOperationBindingService {

    private static final java.util.Set<String> CONNECT_SCOPE = java.util.Set.of(com.howe.mcp.service.security.McpScopes.CONNECT);

    private final JdbcOperations jdbc;
    private final McpOperationStore store;
    private final McpGrantService grants;

    /**
     * 读取本人操作，不存在与越权统一错误。
     *
     * @param userId 会话账号
     * @param operationId 操作标识
     * @return 账本记录
     */
    public McpOperationRecord record(Long userId, String operationId) {
        return store.findOwned(operationId, userId)
                .orElseThrow(() -> new ToolException("not_found_or_forbidden", "操作不存在或无权访问"));
    }

    /**
     * 由账本记录构造当前授权绑定，并复核授权仍有效。
     *
     * @param record 账本记录
     * @return 可信授权绑定
     */
    public McpOperationBinding binding(McpOperationRecord record) {
        List<GrantRow> rows = jdbc.query("""
                select g.user_id,g.grant_id,g.registered_client_id,g.resource_uri,g.authorization_version,
                       c.client_id,c.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                 where g.grant_id=? and g.user_id=?
                """, (row, index) -> new GrantRow(row.getLong("user_id"), row.getLong("grant_id"),
                row.getString("registered_client_id"), row.getString("resource_uri"),
                row.getLong("authorization_version"), row.getString("client_id"), row.getString("client_status")),
                record.grantId(), record.userId());
        if (rows.isEmpty()) {
            throw new ToolException("authorization_changed", "原授权已失效，请重新发起");
        }
        GrantRow grant = rows.get(0);
        if (!grant.registeredClientId().equals(record.registeredClientId())
                || grant.version() != record.grantVersion() || !Objects.equals(grant.resourceUri(), record.resourceUri())
                || !"0".equals(grant.clientStatus())) {
            throw new ToolException("authorization_changed", "原授权已变化，请重新发起");
        }
        // 复用现有授权守卫：总开关、授权、资源与连接 范围一起复核。
        requireActive(invocation(record, grant));
        return new McpOperationBinding(record.userId(), record.preparedDeptId(), record.registeredClientId(),
                record.grantId(), record.grantVersion(), record.resourceUri(),
                CONNECT_SCOPE, accountHash(record));
    }

    /**
     * 同步工具首次准备时构造当前授权绑定。
     *
     * @param userId 当前账号
     * @param grantId 当前授权
     * @return 可信授权绑定
     */
    public McpOperationBinding currentBinding(Long userId, Long grantId) {
        List<GrantRow> rows = jdbc.query("""
                select g.user_id,g.grant_id,g.registered_client_id,g.resource_uri,g.authorization_version,
                       c.client_id,c.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                 where g.grant_id=? and g.user_id=?
                """, (row, index) -> new GrantRow(row.getLong("user_id"), row.getLong("grant_id"),
                row.getString("registered_client_id"), row.getString("resource_uri"),
                row.getLong("authorization_version"), row.getString("client_id"), row.getString("client_status")),
                grantId, userId);
        if (rows.isEmpty()) {
            throw new ToolException("authorization_changed", "原授权已失效，请重新发起");
        }
        GrantRow grant = rows.get(0);
        if (!"0".equals(grant.clientStatus())) {
            throw new ToolException("authorization_changed", "客户端已停用");
        }
        requireActive(new McpInvocationContext(userId, null, null, grant.externalClientId(),
                grant.grantId(), grant.version(), CONNECT_SCOPE, grant.resourceUri(), "operation-submit"));
        return new McpOperationBinding(userId, null, grant.registeredClientId(), grant.grantId(), grant.version(),
                grant.resourceUri(), CONNECT_SCOPE,
                McpOperationStoreImpl.sha256(userId + "|" + grant.registeredClientId()));
    }

    /**
     * 恢复用途的受限技术绑定。
     *
     * <p>只确认原 grant/client lineage 仍存在并绑定同一内部 client；可在原授权撤销后核对已发生的事实，因为此绑定只允许读取证据与补齐已发生事实，调用方不得发起外部写，
     * 也不得用它向模型或浏览器投影结果。</p>
     *
     * @param record 原操作
     * @return 受限恢复绑定
     */
    public McpOperationBinding recoveryBinding(McpOperationRecord record) {
        List<GrantRow> rows = jdbc.query("""
                select g.user_id,g.grant_id,g.registered_client_id,g.resource_uri,g.authorization_version,
                       c.client_id,c.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                 where g.grant_id=? and g.user_id=?
                """, (row, index) -> new GrantRow(row.getLong("user_id"), row.getLong("grant_id"),
                row.getString("registered_client_id"), row.getString("resource_uri"),
                row.getLong("authorization_version"), row.getString("client_id"), row.getString("client_status")),
                record.grantId(), record.userId());
        if (rows.isEmpty() || !rows.get(0).registeredClientId().equals(record.registeredClientId())) {
            throw new ToolException("authorization_changed", "原授权链路不可恢复");
        }
        GrantRow grant = rows.get(0);
        return new McpOperationBinding(record.userId(), record.preparedDeptId(), record.registeredClientId(),
                record.grantId(), record.grantVersion(), record.resourceUri(), CONNECT_SCOPE,
                accountHash(record));
    }

    /**
     * 读取本人操作，不存在返回空。
     *
     * @param userId 账号
     * @param operationId 操作标识
     * @return 记录
     */
    public McpOperationRecord recordOrNull(Long userId, String operationId) {
        return store.findOwned(operationId, userId).orElse(null);
    }

    /** 派发使用原提交时的授权版本，撤销后禁止执行。 */
    public McpOperationBinding dispatchBinding(McpOperationRecord record) {
        return binding(record);
    }

    /** 历史结果使用同一客户端的当前有效连接，允许撤销后重新登录查询。 */
    public McpOperationBinding resultBinding(McpOperationRecord record) {
        McpOperationBinding current = currentBinding(record.userId(), record.grantId());
        if (!current.registeredClientId().equals(record.registeredClientId())
                || !Objects.equals(current.resourceUri(), record.resourceUri())) {
            throw new ToolException("authorization_changed", "原授权链路不可用");
        }
        return current;
    }

    /**
     * 读取原授权对应的外部 client_id。
     *
     * @param record 账本记录
     * @return 外部 client_id
     */
    public String externalClientId(McpOperationRecord record) {
        List<String> rows = jdbc.query("""
                select c.client_id from mcp_grant g
                  join oauth2_registered_client c on c.id=g.registered_client_id
                 where g.grant_id=? and g.user_id=?
                """, (row, index) -> row.getString(1), record.grantId(), record.userId());
        if (rows.isEmpty()) {
            throw new ToolException("authorization_changed", "原授权已失效，请重新发起");
        }
        return rows.get(0);
    }

    /** 将授予中止转换为操作执行器可识别的确定拒绝。 */
    private void requireActive(McpInvocationContext invocation) {
        try {
            grants.requireActive(invocation);
        } catch (org.springframework.security.access.AccessDeniedException exception) {
            throw new ToolException("authorization_changed", "MCP 连接已暂停、撤销或变更");
        }
    }

    /** 重建经验证的原授权调用上下文。 */
    private McpInvocationContext invocation(McpOperationRecord record, GrantRow grant) {
        return new McpInvocationContext(record.userId(), null, record.preparedDeptId(), grant.externalClientId(),
                record.grantId(), record.grantVersion(), CONNECT_SCOPE, record.resourceUri(),
                "operation-" + record.operationId());
    }

    /** 账号上下文摘要保持账本原值。 */
    private static byte[] accountHash(McpOperationRecord record) {
        return record.accountContextHash() == null
                ? McpOperationStoreImpl.sha256(String.valueOf(record.userId())) : record.accountContextHash();
    }

    /** 授权行投影。 */
    private record GrantRow(Long userId, Long grantId, String registeredClientId, String resourceUri, long version,
            String externalClientId, String clientStatus) {
    }
}
