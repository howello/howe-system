package com.howe.mcp.operation;

import com.howe.common.tool.exception.ToolException;
import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.security.McpInvocationContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 浏览器确认入口使用的授权绑定解析。
 *
 * <p>只按操作与账号读取账本，再核对当前授权仍有效；内部
 * {@code registered_client_id} 来自 {@code mcp_grant}，不使用外部 client_id。</p>
 */
@RequiredArgsConstructor
public final class McpOperationBindingService {

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
                       c.client_id,e.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                  join mcp_client_ext e on e.registered_client_id=c.id
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
        // 复用现有授权守卫：总开关、grant、consent、resource 与 connect 范围一起复核。
        grants.requireActive(invocation(record, grant));
        return new McpOperationBinding(record.userId(), record.preparedDeptId(), record.registeredClientId(),
                record.grantId(), record.grantVersion(), record.resourceUri(),
                parseScopes(record.approvedScopes()), accountHash(record));
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
                       g.authorization_version,c.client_id,e.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                  join mcp_client_ext e on e.registered_client_id=c.id
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
        java.util.Set<String> approved = scopes(grant.registeredClientId(), userId);
        grants.requireActive(new McpInvocationContext(userId, null, null, grant.externalClientId(),
                grant.grantId(), grant.version(), approved, grant.resourceUri(), "operation-prepare"));
        return new McpOperationBinding(userId, null, grant.registeredClientId(), grant.grantId(), grant.version(),
                grant.resourceUri(), scopes(grant.registeredClientId(), userId),
                McpOperationStoreImpl.sha256(userId + "|" + grant.registeredClientId()));
    }

    /**
     * 恢复用途的受限技术绑定。
     *
     * <p>只确认原 grant/client lineage 仍存在并绑定同一内部 client；不要求当前 consent
     * 仍覆盖原 scope，因为此绑定只允许读取证据与补齐已发生事实，调用方不得发起外部写，
     * 也不得用它向模型或浏览器投影结果。</p>
     *
     * @param record 原操作
     * @return 受限恢复绑定
     */
    public McpOperationBinding recoveryBinding(McpOperationRecord record) {
        List<GrantRow> rows = jdbc.query("""
                select g.user_id,g.grant_id,g.registered_client_id,g.resource_uri,g.authorization_version,
                       c.client_id,e.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                  join mcp_client_ext e on e.registered_client_id=c.id
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
                record.grantId(), record.grantVersion(), record.resourceUri(), parseScopes(record.approvedScopes()),
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

    /** 当前同意范围，用于准备时冻结快照。 */
    private java.util.Set<String> scopes(String registeredClientId, Long userId) {
        List<String> rows = jdbc.query("""
                select authorities from oauth2_authorization_consent
                 where registered_client_id=? and principal_name=?
                """, (row, index) -> row.getString(1), registeredClientId, String.valueOf(userId));
        if (rows.isEmpty()) {
            throw new ToolException("authorization_changed", "当前授权已失效，请重新授权");
        }
        return java.util.Arrays.stream(rows.get(0).split(",")).map(String::trim)
                .filter(value -> value.startsWith("SCOPE_")).map(value -> value.substring(6))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 派发时按账本重建可信绑定。
     *
     * <p>与浏览器确认不同：这里不要求当前授权版本等于准备时的版本，
     * 但要求 grant 仍有效、客户端仍启用、consent 仍覆盖原范围；
     * 撤权与授权范围变化由 {@link McpGrantService#requireActive} 复核。</p>
     *
     * @param record 账本记录
     * @return 可信授权绑定
     */
    public McpOperationBinding dispatchBinding(McpOperationRecord record) {
        return binding(record);
    }

    /** 检查原工具当前开关。 */
    public void requireToolEnabled(McpOperationRecord record) {
        if (!grants.toolEnabled(record.toolName())) {
            throw new ToolException("tool_unavailable", "原工具已停用");
        }
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

    /**
     * 计算确认摘要哈希；服务端唯一来源，浏览器只能回传比较。
     *
     * @param record 账本记录
     * @return 32 字节摘要
     */
    public byte[] summaryHash(McpOperationRecord record) {
        return McpOperationStoreImpl.sha256(String.join("|", record.operationId(), record.toolName(),
                record.toolVersion(), hex(record.argumentsHash()), record.targetType(), record.targetId(),
                record.expectedVersion(), record.grantId().toString(), Long.toString(record.grantVersion())));
    }

    /** 重建经验证的原授权调用上下文。 */
    private McpInvocationContext invocation(McpOperationRecord record, GrantRow grant) {
        return new McpInvocationContext(record.userId(), null, record.preparedDeptId(), grant.externalClientId(),
                record.grantId(), record.grantVersion(), parseScopes(record.approvedScopes()), record.resourceUri(),
                "operation-" + record.operationId());
    }

    /** 范围快照按逗号解析。 */
    private static java.util.Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Arrays.stream(scopes.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 账号上下文摘要保持账本原值。 */
    private static byte[] accountHash(McpOperationRecord record) {
        return record.accountContextHash() == null
                ? McpOperationStoreImpl.sha256(String.valueOf(record.userId())) : record.accountContextHash();
    }

    /** 十六进制辅助。 */
    private static String hex(byte[] value) {
        return value == null ? "" : java.util.HexFormat.of().formatHex(value);
    }

    /** 授权行投影。 */
    private record GrantRow(Long userId, Long grantId, String registeredClientId, String resourceUri, long version,
            String externalClientId, String clientStatus) {
    }
}
