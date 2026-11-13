package com.howe.system.tool.config;

import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.system.service.ISysDeptService;
import com.howe.system.service.ISysNoticeReadService;
import com.howe.system.tool.provider.SystemReadToolProvider;
import com.howe.system.tool.provider.SystemSelfToolProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安装 MCP 时装配系统工具提供者。
 *
 * <p>未安装时不创建 Provider，也不查询任何 MCP 表；读取 {@code howe.mcp.installed}
 * 配置键不需要引用 MCP Java 类型。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class SystemToolConfiguration {

    /**
     * 注册本人信息 Provider。
     *
     * @param depts 部门服务，用于判断有效家庭
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpSystemSelfTools(ISysDeptService depts) {
        return new SystemSelfToolProvider(depts);
    }

    /**
     * 注册 C2 新增的本人公告 Provider。
     *
     * @param notices 公告已读服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpSystemReadTools(ISysNoticeReadService notices) {
        return new SystemReadToolProvider(notices);
    }

    /**
     * 注册公告写工具；只依赖 common 操作网关。
     *
     * @param gateway 操作网关
     * @return 写工具提供者
     */
    @Bean
    public BusinessToolProvider mcpSystemNoticeWriteTools(
            com.howe.common.tool.operation.ToolOperationGateway gateway) {
        return new com.howe.system.tool.provider.SystemNoticeWriteToolProvider(gateway);
    }

    /**
     * 注册公告批量已读处理器。
     *
     * @param notices 公告已读服务
     * @return 操作处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpSystemNoticeMarkReadHandler(
            ISysNoticeReadService notices) {
        return new com.howe.system.tool.operation.SystemNoticeWriteHandler(notices);
    }
}
