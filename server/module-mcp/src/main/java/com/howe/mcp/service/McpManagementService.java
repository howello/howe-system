package com.howe.mcp.service;

import io.modelcontextprotocol.spec.McpSchema;
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

    /** 目录仅来自实际注册代码；数据库遗留或任意新增名称不会成为可调用工具。 */
    List<ToolView> tools();

    /** 单个工具详情采用代码目录，未知名称直接拒绝。 */
    ToolView tool(String name);

    /** 只允许切换代码中已有工具，不接受 schema、权限、实现或回调的编辑。 */
    void toolStatus(String name, String enabled, String operator);

    /** 全站授权查询明确要求后台方法权限，服务不从请求推定操作者身份。 */
    Page<GrantView> grants(Query query);

    /** 返回最小授权状态，不公开加密载荷、token 或权限快照。 */
    GrantView grant(Long id);

    /** 管理员撤销仍复用相同事务服务；清除专属审计上下文，避免进入旧 JWT 线程后残留。 */
    void revoke(Long id);

    /** 调用查询只选取允许公开的元数据，绝不读取 summary 或原始协议。 */
    Page<CallView> calls(Query query);

    /** 单条审计详情与列表采用相同字段白名单。 */
    CallView call(Long id);

    /** 管理查询仅支持明确条件，排序固定在服务端。 */
    public record Query(Integer pageNum, Integer pageSize, String keyword, String status, Long userId, String toolName, String resultCode) { }

    /** 供 Controller 转换为统一后台分页。 */
    public record Page<T>(List<T> rows, long total) { }

    /** 不含认证秘密的客户端列表字段。 */
    public record ClientView(String id, String clientId, String clientName, String registrationSource, String status, String metadataValid,
            long grantCount, String createdAt, String updatedAt, String lastUsedAt) { }

    /** 只公开协议元数据，不允许后台通过此 DTO 修改。 */
    public record ClientDetail(ClientView client, Set<String> redirectUris, List<String> authenticationMethods, List<String> grantTypes, List<String> scopes) { }

    /** 工具配置与实际代码契约一起展示，未配置时默认关闭。 */
    public record ToolView(McpSchema.Tool schema, String version, String scope, Set<String> permissions, long timeoutMs, int maxResultBytes,
            String enabled, String updateBy, String updatedAt, String remark) { }

    /** 最小授权详情。 */
    public record GrantView(Long grantId, Long userId, String clientId, String clientName, String resource, String status, String clientStatus,
            long authorizationVersion, String createdAt, String updatedAt, String revokedAt) { }

    /** 调用详情仅含允许公开的固定元数据。 */
    public record CallView(Long logId, String requestId, String eventType, Long userId, Long grantId, String clientId, String clientName,
            String toolName, String resultCode, long elapsedMs, String createdAt) { }
}
