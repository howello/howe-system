package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpClientRegistrationService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.dto.McpClientMetadata;
import com.howe.mcp.service.security.McpRegistrationException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.support.TransactionTemplate;

/** 自动登记客户端的事务服务；登记本身不创建用户同意、grant 或 token。 */
@Slf4j
public final class McpClientRegistrationServiceImpl implements McpClientRegistrationService {
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final RegisteredClientRepository clients;
    private final ConfigProvider config;
    private final PasswordEncoder passwords;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** 注入与其它 MCP 存储相同的数据源及服务端密码编码器。 */
    public McpClientRegistrationServiceImpl(JdbcOperations jdbc, TransactionTemplate transactions,
            RegisteredClientRepository clients, ConfigProvider config, PasswordEncoder passwords, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clients = clients;
        this.config = config;
        this.passwords = passwords;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 只在运行开关开启时允许新登记。 */
    @Override
    public void requireEnabled() {
        if (!"true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED))) {
            throw unavailable();
        }
    }

    /** 签发稳定随机 client_id，secret 单向编码；两个登记表在同一事务中保存。 */
    @Override
    public Map<String, Object> register(McpClientMetadata metadata) {
        requireEnabled();
        String secret = "none".equals(metadata.authenticationMethod()) ? null
                : new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32).generateKey();
        var builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("mcp_" + UUID.randomUUID().toString().replace("-", ""))
                .clientIdIssuedAt(clock.instant()).clientName(metadata.name())
                .clientAuthenticationMethod(new ClientAuthenticationMethod(metadata.authenticationMethod()))
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().authorizationCodeTimeToLive(Duration.ofMinutes(2))
                        .accessTokenTimeToLive(Duration.ofMinutes(15)).accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .refreshTokenTimeToLive(Duration.ofDays(30)).reuseRefreshTokens(false).build());
        metadata.redirects().forEach(builder::redirectUri);
        metadata.grants().forEach(value -> builder.authorizationGrantType(new AuthorizationGrantType(value)));
        metadata.scopes().forEach(builder::scope);
        if (secret != null) {
            builder.clientSecret(passwords.encode(secret));
        }
        RegisteredClient client = builder.build();
        String publicMetadata;
        try {
            publicMetadata = mapper.writeValueAsString(metadata.publicValues());
        } catch (JsonProcessingException exception) {
            throw unavailable();
        }
        transactions.executeWithoutResult(status -> {
            // 固定在线参数行充当登记配额互斥锁，管理员调整配额与并发登记保持串行。
            var values = jdbc.query("select config_value from sys_config where config_key=? for update",
                    (row, index) -> row.getString(1), ConfigConstants.MCP_REGISTRATION_MAX_CLIENTS);
            if (values.size() != 1) {
                throw unavailable();
            }
            int maximum = McpClientRegistrationService.parseLimit(values.get(0), 1, 100000);
            Long current = jdbc.queryForObject("select count(*) from mcp_client_ext", Long.class);
            if (current == null || current >= maximum) {
                throw unavailable();
            }
            clients.save(client);
            jdbc.update("insert into mcp_client_ext(registered_client_id,registration_source,status,metadata_json) values(?,'DCR','0',?)",
                    client.getId(), publicMetadata);
        });
        com.howe.mcp.service.security.McpAuditContext.verified(null, client.getId(), null);
        Map<String, Object> response = new LinkedHashMap<>(metadata.publicValues());
        response.put("client_id", client.getClientId());
        response.put("client_id_issued_at", client.getClientIdIssuedAt().getEpochSecond());
        if (secret != null) {
            response.put("client_secret", secret);
            response.put("client_secret_expires_at", 0);
        }
        return response;
    }

    /** 不暴露数据库错误、内部配额值或密码材料。 */
    private static McpRegistrationException unavailable() {
        return new McpRegistrationException("temporarily_unavailable", 503, "客户端登记暂不可用");
    }
}
