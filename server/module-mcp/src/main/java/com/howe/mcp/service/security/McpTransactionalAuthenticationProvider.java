package com.howe.mcp.service.security;

import java.util.Objects;
import com.howe.mcp.service.McpAuditService.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.transaction.support.TransactionTemplate;

/** 为授权码和刷新兑换提供完整事务；协议拒绝前已做的重放失效也必须提交。 */
@Slf4j
public final class McpTransactionalAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final TransactionTemplate transactions;

    /** 注入框架 provider 和与授权存储相同的数据源事务。 */
    public McpTransactionalAuthenticationProvider(AuthenticationProvider delegate, TransactionTemplate transactions) {
        this.delegate = Objects.requireNonNull(delegate);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** 将标准 OAuth 拒绝带到事务外抛出；基础设施异常仍整笔回滚。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        Outcome outcome = transactions.execute(status -> {
            try {
                return new Outcome(delegate.authenticate(authentication), null);
            } catch (OAuth2AuthenticationException exception) {
                return new Outcome(null, exception);
            }
        });
        if (outcome == null) {
            throw new IllegalStateException("OAuth 事务未返回结果");
        }
        if (outcome.error() != null) {
            McpAuditContext.result(Result.fromCode(outcome.error().getError().getErrorCode()));
            throw outcome.error();
        }
        McpAuditContext.result(Result.OK);
        return outcome.authentication();
    }

    /** 保留框架 provider 的认证类型契约。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    /** 区分需要提交的协议拒绝与需要回滚的持久化故障。 */
    private record Outcome(Authentication authentication, OAuth2AuthenticationException error) {
    }
}
