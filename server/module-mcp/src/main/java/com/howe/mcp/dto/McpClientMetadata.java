package com.howe.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.howe.mcp.service.security.McpRegistrationException;
import com.howe.mcp.service.security.McpScopes;
import com.howe.mcp.service.security.McpUriPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 经校验的客户端元数据，不包含服务器生成的 secret 或用户授权。 */
public record McpClientMetadata(String name, List<String> redirects, String authenticationMethod,
        Set<String> grants, Set<String> scopes) {

    /** 防止后续修改已校验的集合。 */
    public McpClientMetadata {
        redirects = List.copyOf(redirects);
        grants = Set.copyOf(grants);
        scopes = Set.copyOf(scopes);
    }

    /** 按 OAuth 动态登记规则读取支持字段；未知可选展示字段不执行或发布。 */
    public static McpClientMetadata parse(JsonNode document) {
        if (document == null || !document.isObject() || document.has("client_id") || document.has("client_secret")) {
            throw invalid("登记文档格式不正确或含服务器管理字段");
        }
        String name = text(document, "client_name", "MCP 客户端", 200);
        String method = text(document, "token_endpoint_auth_method", "client_secret_basic", 64);
        if (!Set.of("none", "client_secret_basic", "client_secret_post").contains(method)) {
            throw invalid("不支持该客户端认证方式");
        }
        List<String> redirects = array(document, "redirect_uris", null, 10);
        for (String redirect : redirects) {
            try {
                McpUriPolicy.requireRedirectUri(redirect);
                // 框架 JDBC 仓库使用逗号分隔 URI，要求客户端用 %2C 表达 URI 内的字面逗号。
                if (redirect.contains(",")) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new McpRegistrationException("invalid_redirect_uri", 400, "回调地址不符合支持的 HTTPS 或 loopback 规则");
            }
        }
        Set<String> grants = Set.copyOf(array(document, "grant_types", List.of("authorization_code"), 2));
        if (!grants.contains("authorization_code") || !Set.of("authorization_code", "refresh_token").containsAll(grants)
                || !array(document, "response_types", List.of("code"), 1).equals(List.of("code"))) {
            throw invalid("只支持授权码及其刷新流程");
        }
        String scope = text(document, "scope", McpScopes.CONNECT, 1000);
        String[] scopeValues = scope.split(" ", -1);
        Set<String> scopes = Set.copyOf(List.of(scopeValues));
        if (scopes.size() != scopeValues.length || !scopes.contains(McpScopes.CONNECT) || !McpScopes.SUPPORTED.containsAll(scopes)
                || scopes.contains(McpScopes.OFFLINE_ACCESS) && !grants.contains("refresh_token")) {
            throw invalid("授权范围或刷新能力不受支持");
        }
        return new McpClientMetadata(name, redirects, method, grants, scopes);
    }

    /** CIMD 的缺省认证是 none，不能套用 DCR 的共享密钥缺省值；仅接受本期支持的公共客户端。 */
    public static McpClientMetadata parseCimd(String clientId, JsonNode document) {
        try {
            McpUriPolicy.requireMetadataUri(clientId);
            if (document == null || !document.isObject() || !document.path("client_id").isTextual()
                    || !clientId.equals(document.path("client_id").textValue()) || !document.has("client_name")
                    || document.has("client_secret") || document.has("client_secret_expires_at")
                    || document.has("jwks") && document.has("jwks_uri")) { throw invalid("客户端元数据不符合要求"); }
            String method = text(document, "token_endpoint_auth_method", "none", 64);
            List<String> methods = array(document, "token_endpoint_auth_methods_supported", List.of(method), 8);
            if (methods.stream().anyMatch(value -> value.startsWith("client_secret"))
                    || document.has("token_endpoint_auth_method") && !methods.contains(method) || !methods.contains("none")) {
                throw invalid("不支持该 CIMD 客户端认证方式");
            }
            if (containsPrivateKey(document.get("jwks"))) { throw invalid("客户端文档不能包含私钥"); }
            var normalized = ((com.fasterxml.jackson.databind.node.ObjectNode) document).deepCopy();
            normalized.remove("client_id");
            normalized.put("token_endpoint_auth_method", "none");
            var grants = array(document, "grant_types", List.of("authorization_code"), 16).stream()
                    .filter(value -> Set.of("authorization_code", "refresh_token").contains(value)).toList();
            var responses = array(document, "response_types", List.of("code"), 16).stream().filter("code"::equals).toList();
            normalized.putArray("grant_types").addAll(grants.stream().map(com.fasterxml.jackson.databind.node.TextNode::valueOf).toList());
            normalized.putArray("response_types").addAll(responses.stream().map(com.fasterxml.jackson.databind.node.TextNode::valueOf).toList());
            if (!document.has("scope")) {
                normalized.put("scope", McpScopes.CONNECT + (grants.contains("refresh_token") ? " " + McpScopes.OFFLINE_ACCESS : ""));
            }
            return parse(normalized);
        } catch (IllegalArgumentException exception) { throw invalid("客户端元数据不符合要求"); }
    }

    /** 公布的 JWKS 只能含公钥，不保留或使用任何私有/对称密钥分量。 */
    private static boolean containsPrivateKey(JsonNode node) {
        if (node == null) { return false; }
        if (node.isObject()) {
            for (String field : List.of("d", "p", "q", "dp", "dq", "qi", "oth", "k")) {
                if (node.has(field)) { return true; }
            }
        }
        for (JsonNode child : node) { if (containsPrivateKey(child)) { return true; } }
        return false;
    }

    /** 仅发布白名单字段；不复制不可信任文档中的任意对象、脚本或展示链接。 */
    public Map<String, Object> publicValues() {
        return Map.of("client_name", name, "redirect_uris", redirects, "token_endpoint_auth_method", authenticationMethod,
                "grant_types", grants.stream().sorted().toList(), "response_types", List.of("code"),
                "scope", String.join(" ", scopes.stream().sorted().toList()));
    }

    /** 只接受有界非空文本，不把 JSON 数字或对象强制转换成文本。 */
    private static String text(JsonNode document, String field, String fallback, int maxLength) {
        JsonNode node = document.get(field);
        if (node == null) {
            return fallback;
        }
        if (!node.isTextual() || node.textValue().isBlank() || node.textValue().length() > maxLength
                || node.textValue().codePoints().anyMatch(Character::isISOControl)) {
            throw invalid("客户端元数据文本格式不正确");
        }
        return node.textValue();
    }

    /** URI 与 grant 数组必须非空、有界且不重复。 */
    private static List<String> array(JsonNode document, String field, List<String> fallback, int maximum) {
        JsonNode node = document.get(field);
        if (node == null && fallback != null) {
            return fallback;
        }
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > maximum) {
            throw invalid("客户端元数据列表格式不正确");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 2048) {
                throw invalid("客户端元数据列表内容不正确");
            }
            result.add(item.textValue());
        }
        if (new LinkedHashSet<>(result).size() != result.size()) {
            throw invalid("客户端元数据列表不能重复");
        }
        return result;
    }

    /** 统一返回固定说明，不将不可信元数据回显到异常中。 */
    private static McpRegistrationException invalid(String message) {
        return new McpRegistrationException("invalid_client_metadata", 400, message);
    }
}
