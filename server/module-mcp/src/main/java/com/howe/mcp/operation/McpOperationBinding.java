package com.howe.mcp.operation;

/**
 * 平台作用域提供的已验证授权绑定。
 *
 * <p>由 MCP 在执行工具前从当前数据库授权与重新加载的账号构造，领域与浏览器请求
 * 都不能自行提供。内部 {@code registeredClientId} 来自 {@code mcp_grant}，
 * 不是外部 {@code client_id}。</p>
 *
 * @param userId 当前账号
 * @param deptId 当前部门，可为 {@code null}
 * @param registeredClientId 内部客户端登记主键
 * @param grantId 原授权主键
 * @param grantVersion 原授权版本
 * @param resourceUri 授权资源
 * @param scopes 当前已批准范围
 * @param accountContextHash 账号权限与部门摘要
 */
public record McpOperationBinding(Long userId, Long deptId, String registeredClientId, Long grantId,
        long grantVersion, String resourceUri, java.util.Set<String> scopes, byte[] accountContextHash) {

    /** 防御性复制。 */
    public McpOperationBinding {
        scopes = scopes == null ? java.util.Set.of() : java.util.Set.copyOf(scopes);
        accountContextHash = accountContextHash == null ? null : accountContextHash.clone();
    }
}
