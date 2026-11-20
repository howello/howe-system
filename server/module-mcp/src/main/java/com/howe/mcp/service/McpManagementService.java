package com.howe.mcp.service;

import java.util.List;
import java.util.Set;

/** MCP 后台管理查询与状态维护服务接口。 */
public interface McpManagementService {
    /** 分页检索客户端；关键字为字面子串，不把百分号当通配符。 */
    Page<ClientView> clients(Query query);

    /** 详情明确选择可公开的客户端元数据，不序列化框架 RegisteredClient。 */
    ClientDetail client(String id);

    /** 停用会原子撤销现有授权及待确认 state；恢复仅改管理状态，不能恢复旧凭据。 */
    void clientStatus(String id, String value);

    /** 全站授权查询明确要求后台方法权限，服务不从请求推定操作者身份。 */
    Page<GrantView> grants(Query query);

    /** 返回最小授权状态，不公开加密载荷、token 或权限快照。 */
    GrantView grant(Long id);

    /** 管理员撤销仍复用相同事务服务；清除专属审计上下文，避免进入旧 JWT 线程后残留。 */
    void revoke(Long id);

    /** 管理查询仅支持明确条件，排序固定在服务端。 */
    public record Query(Integer pageNum, Integer pageSize, String keyword, String status, Long userId, String toolName, String resultCode) { }

    /** 供 Controller 转换为统一后台分页。 */
    public record Page<T>(List<T> rows, long total) { }

    /** 不含认证秘密的客户端列表字段。 */
    public record ClientView(String id, String clientId, String clientName, String registrationSource, String status, String metadataValid,
            long grantCount, String createdAt, String updatedAt, String lastUsedAt) { }

    /** 只公开协议元数据，不允许后台通过此 DTO 修改。 */
    public record ClientDetail(ClientView client, Set<String> redirectUris, List<String> authenticationMethods, List<String> grantTypes, List<String> scopes) { }

    /** 最小授权详情。 */
    public record GrantView(Long grantId, Long userId, String clientId, String clientName, String resource, String status, String clientStatus,
            long authorizationVersion, String createdAt, String updatedAt, String revokedAt) { }

}
