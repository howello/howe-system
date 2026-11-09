package com.howe.mcp.service;

import com.howe.mcp.service.security.McpMetadataFetcher;
import java.util.function.Supplier;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** 客户端身份元数据发现、缓存和信任校验服务接口。 */
public interface McpCimdClientService {
    /** 只把 HTTPS URL 作为自动发现候选，不把 UUID 客户端或其它协议交给出站获取器。 */
    public static boolean isCandidate(String clientId) {
        return clientId != null && clientId.regionMatches(true, 0, "https://", 0, 8);
    }

    /** 新登记不会创建用户授权，缓存命中也始终检查管理员停用状态。 */
    RegisteredClient ensure(String clientId, String remoteIp);

    /** 所有客户端的授权请求和同意都持有客户端行锁，与元数据更新、管理员停用串行。 */
    <T> T withClientLock(String clientId, Supplier<T> action);

    /** 用户确认从自身事务调用同一行锁，不嵌套第二个可能改变拒绝提交语义的事务边界。 */
    void lockForAuthorization(String clientId);

    /** 已验证 JSON 的来源；生产只绑定具有固定 IP/TLS 与有界读取的获取器。 */
    @FunctionalInterface
    public interface Source { /** 获取一次元数据，不缓存错误。 */ McpMetadataFetcher.Document fetch(String clientId); }
}
