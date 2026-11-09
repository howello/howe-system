package com.howe.mcp.service;


/** 过期凭据和未使用客户端清理服务接口。 */
public interface McpCredentialCleanupService {
    /** 单轮最多 100 个授权载荷、5000 条索引及 100 个闲置客户端；五秒后不再开启新事务。 */
    Statistics clean();

    /** 对外仅返回数量，不返回任何用户标识、令牌或哈希。 */
    public record Statistics(int authorizations, int tokens, int unusedClients) { }
}
