package com.howe.mcp.service;

import com.howe.mcp.service.security.McpInvocationContext;

/** 用户授权状态校验和撤销服务接口。 */
public interface McpGrantService {
    /** 每次调用按当前记录检查主体、客户端、资源、授权版本与用户当前同意范围。 */
    void requireActive(McpInvocationContext invocation);

    /** 工具缺失配置时关闭；此方法不替代总开关和授权检查。 */
    boolean toolEnabled(String name);

    /**
     * 撤销本人在客户端上的整个资源授权；与同意和凭据索引在同一事务提交。
     * 关闭运行开关时仍允许撤销。重复撤销保持幂等，不恢复或创建授权。
     */
    void revokeOwned(Long userId, Long grantId);
}
