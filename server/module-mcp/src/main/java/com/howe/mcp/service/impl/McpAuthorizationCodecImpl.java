package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpAuthorizationCodec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.utils.crypto.SecretCipher;
import com.howe.mcp.service.security.McpOAuthAttributes;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 授权记录的版本化加密编解码；不启用任意类型反序列化，不保存完整 LoginUser 或密码摘要。
 * 仅支持本期 authorization_code、opaque access token 和 rotating refresh token。
 */
@Slf4j
public final class McpAuthorizationCodecImpl implements McpAuthorizationCodec {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SecretCipher cipher;
    private final RegisteredClientRepository clients;

    /** 注入系统加密工具及稳定的客户端登记存储。 */
    public McpAuthorizationCodecImpl(SecretCipher cipher, RegisteredClientRepository clients) {
        this.cipher = Objects.requireNonNull(cipher);
        this.clients = Objects.requireNonNull(clients);
    }

    /** 将最小协议快照加密；未知对象或缺少主密钥时直接失败。 */
    @Override
    public String encode(OAuth2Authorization authorization) {
        requireUserId(authorization.getPrincipalName());
        if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorization.getAuthorizationGrantType())) {
            throw new IllegalArgumentException("MCP 只持久化授权码授权");
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        authorization.getAttributes().forEach((name, value) -> {
            if (McpOAuthAttributes.PERSISTED.contains(name) || OAuth2ParameterNames.STATE.equals(name)) {
                requireSimple(value);
                attributes.put(name, value);
            } else if (!Principal.class.getName().equals(name)
                    && !OAuth2AuthorizationRequest.class.getName().equals(name)
                    && !McpOAuthAttributes.STORAGE_VERSION.equals(name)) {
                throw new IllegalArgumentException("授权记录含未声明的属性");
            }
        });
        Authentication principal = authorization.getAttribute(Principal.class.getName());
        if (principal != null) {
            String principalId = principal.getPrincipal() instanceof LoginUser user
                    ? String.valueOf(user.getUserId()) : principal.getName();
            if (!principalId.equals(authorization.getPrincipalName()) || !principal.isAuthenticated()) {
                throw new IllegalArgumentException("授权身份与用户ID不一致");
            }
        }
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        RequestSnapshot requestSnapshot = null;
        if (request != null) {
            request.getAdditionalParameters().values().forEach(McpAuthorizationCodecImpl::requireSimple);
            requestSnapshot = new RequestSnapshot(request.getAuthorizationUri(), request.getClientId(), request.getRedirectUri(),
                    request.getScopes(), request.getState(), request.getAdditionalParameters());
        }
        List<TokenSnapshot> tokens = new ArrayList<>();
        addToken(tokens, "code", authorization.getToken(OAuth2AuthorizationCode.class));
        addToken(tokens, "access_token", authorization.getAccessToken());
        addToken(tokens, "refresh_token", authorization.getRefreshToken());
        var snapshot = new AuthorizationSnapshot(1, authorization.getId(), authorization.getRegisteredClientId(),
                authorization.getPrincipalName(), principal != null, authorization.getAuthorizedScopes(), attributes, requestSnapshot, tokens);
        try {
            return cipher.encrypt(mapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OAuth 授权记录序列化失败");
        }
    }

    /** 按预期记录 ID 解密并重建框架领域对象；密文交换、篡改或未知格式均拒绝。 */
    @Override
    public OAuth2Authorization decode(String expectedId, String encrypted) {
        try {
            AuthorizationSnapshot snapshot = mapper.readValue(cipher.decrypt(encrypted), AuthorizationSnapshot.class);
            if (snapshot.formatVersion() != 1 || !Objects.equals(expectedId, snapshot.id())) {
                throw new IllegalArgumentException("OAuth 授权载荷版本或绑定不匹配");
            }
            requireUserId(snapshot.principalName());
            var client = clients.findById(snapshot.registeredClientId());
            if (client == null) {
                throw new IllegalStateException("OAuth 授权对应的客户端不存在");
            }
            var builder = OAuth2Authorization.withRegisteredClient(client).id(snapshot.id())
                    .principalName(snapshot.principalName()).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizedScopes(snapshot.scopes());
            snapshot.attributes().forEach((name, value) -> {
                if (!McpOAuthAttributes.PERSISTED.contains(name) && !OAuth2ParameterNames.STATE.equals(name)) {
                    throw new IllegalArgumentException("授权载荷含未知属性");
                }
                if (McpOAuthAttributes.GRANT_ID.equals(name) || McpOAuthAttributes.AUTHORIZATION_VERSION.equals(name)) {
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException("授权标识必须为数字");
                    }
                    builder.attribute(name, number.longValue());
                } else {
                    builder.attribute(name, value);
                }
            });
            if (snapshot.hasPrincipal()) {
                builder.attribute(Principal.class.getName(), UsernamePasswordAuthenticationToken.authenticated(
                        snapshot.principalName(), null, List.of()));
            }
            if (snapshot.request() != null) {
                RequestSnapshot request = snapshot.request();
                builder.attribute(OAuth2AuthorizationRequest.class.getName(), OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri(request.authorizationUri()).clientId(request.clientId()).redirectUri(request.redirectUri())
                        .scopes(request.scopes()).state(request.state()).additionalParameters(request.additionalParameters()).build());
            }
            for (TokenSnapshot token : snapshot.tokens()) {
                switch (token.type()) {
                    case "code" -> builder.token(new OAuth2AuthorizationCode(token.value(), token.issuedAt(), token.expiresAt()),
                            metadata -> metadata.putAll(restoreMetadata(token.metadata())));
                    case "access_token" -> builder.token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                            token.value(), token.issuedAt(), token.expiresAt(), token.scopes()),
                            metadata -> metadata.putAll(restoreMetadata(token.metadata())));
                    case "refresh_token" -> builder.token(new OAuth2RefreshToken(token.value(), token.issuedAt(), token.expiresAt()),
                            metadata -> metadata.putAll(restoreMetadata(token.metadata())));
                    default -> throw new IllegalArgumentException("授权载荷含未知令牌类型");
                }
            }
            return builder.build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OAuth 授权记录格式不合法");
        }
    }

    /** 提取令牌及元数据，不调用任意领域对象的 Jackson 序列化。 */
    private static void addToken(List<TokenSnapshot> result, String type,
            OAuth2Authorization.Token<? extends org.springframework.security.oauth2.core.OAuth2Token> token) {
        if (token == null) {
            return;
        }
        requireSimple(token.getMetadata());
        var value = token.getToken();
        Set<String> scopes = value instanceof OAuth2AccessToken access ? access.getScopes() : Set.of();
        result.add(new TokenSnapshot(type, value.getTokenValue(), value.getIssuedAt(), value.getExpiresAt(), scopes, token.getMetadata()));
    }

    /** 用户主键是稳定的授权主体；用户名变化不迁移授权。 */
    private static void requireUserId(String principal) {
        try {
            long userId = Long.parseLong(principal);
            if (userId <= 0 || !Long.toString(userId).equals(principal)) {
                throw new IllegalArgumentException("授权主体必须是正常系统用户ID");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("授权主体必须是正常系统用户ID");
        }
    }

    /** 将标准声明中的时间恢复为 Instant，保持框架声明读取接口的类型契约。 */
    private static Map<String, Object> restoreMetadata(Map<String, Object> source) {
        Map<String, Object> metadata = new LinkedHashMap<>(source);
        if (metadata.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME) instanceof Map<?, ?> sourceClaims) {
            Map<String, Object> claims = new LinkedHashMap<>();
            sourceClaims.forEach((name, value) -> claims.put((String) name, value));
            for (String name : List.of("iat", "exp", "nbf")) {
                if (claims.get(name) instanceof String value) {
                    claims.put(name, Instant.parse(value));
                }
            }
            metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims);
        }
        return metadata;
    }

    /** 只允许简单协议数据，拒绝密码对象、任意类及多态反序列化标记。 */
    private static void requireSimple(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Instant || value instanceof java.net.URL || value instanceof java.net.URI) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                if (!(key instanceof String name) || name.startsWith("@")) {
                    throw new IllegalArgumentException("协议属性不允许类型标记");
                }
                requireSimple(item);
            });
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(McpAuthorizationCodecImpl::requireSimple);
            return;
        }
        throw new IllegalArgumentException("协议属性包含不可持久化对象");
    }

    /** 固定结构的授权快照，不含任意 Java 类型信息。 */
    private record AuthorizationSnapshot(int formatVersion, String id, String registeredClientId, String principalName,
            boolean hasPrincipal, Set<String> scopes, Map<String, Object> attributes, RequestSnapshot request, List<TokenSnapshot> tokens) {
    }

    /** 仅保存授权码流程需要的请求字段。 */
    private record RequestSnapshot(String authorizationUri, String clientId, String redirectUri, Set<String> scopes,
            String state, Map<String, Object> additionalParameters) {
    }

    /** 敏感令牌快照只存在于内存和加密载荷中。 */
    private record TokenSnapshot(String type, String value, Instant issuedAt, Instant expiresAt, Set<String> scopes,
            Map<String, Object> metadata) {
        /** 禁止调试输出暴露令牌明文。 */
        @Override
        public String toString() {
            return "TokenSnapshot[凭据已隐藏]";
        }
    }
}
