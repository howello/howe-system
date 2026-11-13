package com.howe.share.tool.config;

import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.share.service.IShareLinkService;
import com.howe.share.tool.provider.ShareReadToolProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安装 MCP 时装配分享只读工具。
 *
 * <p>未安装时不创建 Provider，也不查询任何 MCP 表；读取 {@code howe.mcp.installed}
 * 配置键不需要引用 MCP Java 类型。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class ShareToolConfiguration {

    /**
     * 注册分享只读 Provider。
     *
     * @param links 分享管理服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpShareReadTools(IShareLinkService links) {
        return new ShareReadToolProvider(links);
    }
}
