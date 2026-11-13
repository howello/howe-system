package com.howe.mcp.config;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.service.security.McpUriPolicy;
import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * 启动时固定的协议与页面地址快照；在线修改配置后在重启时重新装载。
 *
 * @param backendBaseUrl 后端公共站点
 * @param frontendBaseUrl 前端授权页面站点
 */
public record McpEndpointSettings(URI backendBaseUrl, URI frontendBaseUrl) {

    /** 校验快照包含两个明确的站点地址。 */
    public McpEndpointSettings {
        Objects.requireNonNull(backendBaseUrl, "后端公共地址不能为空");
        Objects.requireNonNull(frontendBaseUrl, "前端公共地址不能为空");
    }

    /**
     * 从在线配置创建地址快照。
     *
     * @param backend 后端公共基址
     * @param frontend 前端公共基址
     * @param allowLocalHttp 是否明确允许本机开发地址
     * @return 已校验的启动快照
     */
    public static McpEndpointSettings from(String backend, String frontend, boolean allowLocalHttp) {
        return new McpEndpointSettings(McpUriPolicy.requireBaseUrl(backend, allowLocalHttp),
                McpUriPolicy.requireBaseUrl(frontend, allowLocalHttp));
    }

    /**
     * 在模块启用时读取在线参数并冻结协议地址，避免同一授权流程中 issuer 发生变化。
     *
     * @param provider 参数配置提供者
     * @param allowLocalHttp 是否允许本机开发地址
     * @return 当前进程使用的地址快照
     */
    public static McpEndpointSettings load(ConfigProvider provider, boolean allowLocalHttp) {
        return from(provider.getPlainValue(ConfigConstants.MCP_PUBLIC_BASE_URL),
                provider.getPlainValue(ConfigConstants.MCP_FRONTEND_BASE_URL), allowLocalHttp);
    }

    /** 返回 OAuth issuer。 */
    public String issuer() {
        return backendBaseUrl.toASCIIString();
    }

    /** 返回唯一的 MCP resource URI。 */
    public String resource() {
        return backendBaseUrl.resolve("/mcp").toASCIIString();
    }

    /** 返回公开资源元数据 URL。 */
    public String resourceMetadata() {
        return backendBaseUrl.resolve("/.well-known/oauth-protected-resource/mcp").toASCIIString();
    }

    /** 返回前端登录页面，不接收客户端任意跳转参数。 */
    public String loginPage() {
        return frontendBaseUrl.resolve("/mcp/login").toASCIIString();
    }

    /** 返回前端授权确认页面。 */
    public String consentPage() {
        return frontendBaseUrl.resolve("/mcp/consent").toASCIIString();
    }

    /** 返回前端自助连接页面。 */
    public String connectionsPage() {
        return frontendBaseUrl.resolve("/mcp/connections").toASCIIString();
    }

    /** 返回前端写操作确认页面。 */
    public String confirmationPage() {
        return frontendBaseUrl.resolve("/mcp/confirm").toASCIIString();
    }

    /** 返回前端本人操作列表页面。 */
    public String operationsPage() {
        return frontendBaseUrl.resolve("/mcp/operations").toASCIIString();
    }

    /** 返回允许的浏览器站点；跨域请求仅允许这两个显式来源。 */
    public Set<String> allowedOrigins() {
        return Set.copyOf(java.util.List.of(backendBaseUrl.toASCIIString(), frontendBaseUrl.toASCIIString()));
    }
}
