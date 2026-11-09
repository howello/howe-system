package com.howe.web.mcp;

import com.howe.common.core.domain.model.LoginBody;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.framework.web.service.SysCurrentAccountService;
import com.howe.framework.web.service.SysLoginService;
import com.howe.mcp.service.spi.McpAccountBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 在聚合模块桥接账号服务，避免 module-mcp 反向依赖 framework。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpAccountBridgeConfiguration {

    /** MCP 不伪装点餐端登录，也不签发管理端 JWT。 */
    @Bean
    public McpAccountBridge mcpAccountBridge(SysLoginService login, SysCurrentAccountService current) {
        return new McpAccountBridge() {
            /** 复用完整认证校验，所有正常账号采用独立客户端准入策略。 */
            @Override
            public LoginUser authenticate(LoginBody body) {
                LoginUser authenticated = login.authenticateAccount(body);
                return current.loadActiveUser(authenticated.getUserId());
            }

            /** 按已认证主键加载当前账号状态及权限，避免再次验证密码。 */
            @Override
            public LoginUser loadActiveUser(Long userId) {
                return current.loadActiveUser(userId);
            }
        };
    }
}
