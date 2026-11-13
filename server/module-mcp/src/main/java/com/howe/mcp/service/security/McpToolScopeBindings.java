package com.howe.mcp.service.security;

import com.howe.common.tool.spi.BusinessToolProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 能力分组到 OAuth scope 的集中映射表。
 *
 * <p>通用工具定义只声明能力分组（如 {@code blog.read}），由本类解析为 OAuth scope
 * （如 {@code blog:read}）。映射表由平台维护为不可变表，业务 Provider 不能通过声明
 * 分组扩充 {@link McpScopes#SUPPORTED}；绑定目标必须引用现有 scope 常量。</p>
 *
 * <p>安装时执行严格校验：分组格式、绑定存在性、目标合法性、重复绑定与保留分组。
 * 校验失败即启动失败，禁止按名字前缀猜 scope 或默认放行。</p>
 */
public final class McpToolScopeBindings {

    /** 能力分组格式：小写点号分隔，不含冒号。 */
    private static final Pattern GROUP_PATTERN = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+");

    /** 保留给平台内部工具的连接分组。 */
    public static final String CONNECT_GROUP = "mcp.connect";

    /** 分组到 scope 的不可变映射。 */
    private static final Map<String, String> BINDINGS = bindings();

    /** 绑定表不允许实例化。 */
    private McpToolScopeBindings() {
    }

    /**
     * 返回全部绑定，按声明顺序稳定。
     *
     * @return 不可变绑定表
     */
    static Map<String, String> all() {
        return BINDINGS;
    }

    /**
     * 解析能力分组对应的 OAuth scope。
     *
     * @param group 能力分组
     * @return 对应 scope
     * @throws IllegalArgumentException 分组未绑定
     */
    public static String resolve(String group) {
        String scope = BINDINGS.get(group);
        if (scope == null) {
            throw new IllegalArgumentException("能力分组缺少授权范围绑定：" + group);
        }
        return scope;
    }

    /**
     * 判断分组是否为平台保留分组。
     *
     * @param group 能力分组
     * @return 保留时返回 true
     */
    public static boolean reserved(String group) {
        return CONNECT_GROUP.equals(group);
    }

    /**
     * 校验业务 Provider 声明的能力分组集合。
     *
     * <p>要求每个业务分组都合法且已绑定、绑定目标属于 {@link McpScopes#SUPPORTED}、
     * 不绑定 {@code offline_access} 或冒用连接 scope、不得声明保留分组。</p>
     *
     * @param providers 已收集的业务 Provider
     * @throws IllegalStateException 校验失败，安装必须中止
     */
    public static void validate(Collection<? extends BusinessToolProvider> providers) {
        List<String> errors = new ArrayList<>();
        if (providers != null) {
            for (BusinessToolProvider provider : providers) {
                if (provider == null || provider.definitions() == null) {
                    continue;
                }
                provider.definitions().forEach(definition -> {
                    if (definition == null) {
                        return;
                    }
                    String group = definition.capabilityGroup();
                    if (group == null || !GROUP_PATTERN.matcher(group).matches()) {
                        errors.add("能力分组格式非法：" + group + "（工具 " + definition.name() + "）");
                        return;
                    }
                    if (reserved(group)) {
                        errors.add("业务工具不能声明保留分组 " + group + "（工具 " + definition.name() + "）");
                        return;
                    }
                    String scope = BINDINGS.get(group);
                    if (scope == null) {
                        errors.add("能力分组缺少授权范围绑定：" + group + "（工具 " + definition.name() + "）");
                        return;
                    }
                    if (!McpScopes.SUPPORTED.contains(scope)) {
                        errors.add("绑定目标不是已发布 scope：" + scope + "（分组 " + group + "）");
                        return;
                    }
                    if (McpScopes.OFFLINE_ACCESS.equals(scope) || McpScopes.CONNECT.equals(scope)) {
                        errors.add("业务分组不能绑定平台 scope：" + scope + "（分组 " + group + "）");
                    }
                });
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("MCP 能力分组绑定校验失败：" + String.join("；", errors));
        }
    }

    /**
     * 校验映射表自身的一致性。
     *
     * <p>检查分组格式、绑定目标已发布、业务分组未冒用平台 scope、已发布业务 scope 均有绑定。
     * 在类初始化后由装配流程调用一次。</p>
     *
     * @throws IllegalStateException 表自身不合法
     */
    public static void validateTable() {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> entry : BINDINGS.entrySet()) {
            String group = entry.getKey();
            String scope = entry.getValue();
            if (!GROUP_PATTERN.matcher(group).matches()) {
                errors.add("映射表分组格式非法：" + group);
            }
            if (!McpScopes.SUPPORTED.contains(scope)) {
                errors.add("映射表目标不是已发布 scope：" + scope + "（分组 " + group + "）");
            }
            if (!reserved(group) && (McpScopes.OFFLINE_ACCESS.equals(scope) || McpScopes.CONNECT.equals(scope))) {
                errors.add("业务分组不能绑定平台 scope：" + scope + "（分组 " + group + "）");
            }
        }
        Set<String> publishedBusiness = McpScopes.SUPPORTED.stream()
                .filter(scope -> !McpScopes.CONNECT.equals(scope) && !McpScopes.OFFLINE_ACCESS.equals(scope))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> boundBusiness = BINDINGS.entrySet().stream()
                .filter(entry -> !reserved(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(java.util.stream.Collectors.toSet());
        for (String scope : publishedBusiness) {
            if (!boundBusiness.contains(scope)) {
                errors.add("已发布业务 scope 缺少能力分组绑定：" + scope);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("MCP 能力分组映射表不合法：" + String.join("；", errors));
        }
    }

    /**
     * 构造绑定表。
     *
     * <p>使用显式 put 而非 Map.of，便于装配期检测重复键而不是静默覆盖。</p>
     *
     * @return 不可变绑定表
     */
    private static Map<String, String> bindings() {
        Map<String, String> table = new LinkedHashMap<>();
        put(table, CONNECT_GROUP, McpScopes.CONNECT);
        put(table, "blog.read", McpScopes.BLOG_READ);
        put(table, "system.self.read", McpScopes.SYSTEM_SELF_READ);
        put(table, "meal.read", McpScopes.MEAL_READ);
        put(table, "share.read", McpScopes.SHARE_READ);
        put(table, "ai.catalog.read", McpScopes.AI_CATALOG_READ);
        put(table, "ai.logs.read", McpScopes.AI_LOGS_READ);
        put(table, "system.notice.read", McpScopes.SYSTEM_NOTICE_READ);
        put(table, "system.audit.read", McpScopes.SYSTEM_AUDIT_READ);
        put(table, "blog.write", McpScopes.BLOG_WRITE);
        put(table, "system.notice.write", McpScopes.SYSTEM_NOTICE_WRITE);
        put(table, "meal.write", McpScopes.MEAL_WRITE);
        put(table, "meal.kitchen.read", McpScopes.MEAL_KITCHEN_READ);
        put(table, "meal.kitchen.write", McpScopes.MEAL_KITCHEN_WRITE);
        put(table, "meal.proposal.audit", McpScopes.MEAL_PROPOSAL_AUDIT);
        put(table, "meal.member.read", McpScopes.MEAL_MEMBER_READ);
        return java.util.Collections.unmodifiableMap(table);
    }

    /**
     * 校验一组原始绑定条目。
     *
     * <p>重复检测直接从保留重复项的条目列表进入，不先构造 Map 再检查覆盖，
     * 从而能真正发现重复分组与重复业务 scope。</p>
     *
     * @param entries 原始绑定条目，元素为 {@code [group, scope]}
     * @throws IllegalStateException 存在重复分组或重复业务 scope
     */
    public static void validateEntries(List<String[]> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<String> errors = new ArrayList<>();
        Set<String> groups = new java.util.HashSet<>();
        Set<String> businessScopes = new java.util.HashSet<>();
        for (String[] entry : entries) {
            if (entry == null || entry.length != 2) {
                errors.add("绑定条目格式非法");
                continue;
            }
            String group = entry[0];
            String scope = entry[1];
            if (!groups.add(group)) {
                errors.add("能力分组重复绑定：" + group);
            }
            if (!CONNECT_GROUP.equals(group) && !businessScopes.add(scope)) {
                errors.add("业务 scope 被重复绑定：" + scope);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("MCP 能力分组绑定校验失败：" + String.join("；", errors));
        }
    }

    /**
     * 写入绑定并拒绝重复分组或重复业务 scope。
     *
     * @param table 目标表
     * @param group 能力分组
     * @param scope 目标 scope
     */
    private static void put(Map<String, String> table, String group, String scope) {
        if (table.containsKey(group)) {
            throw new IllegalStateException("能力分组重复绑定：" + group);
        }
        if (!CONNECT_GROUP.equals(group) && table.containsValue(scope)) {
            throw new IllegalStateException("业务 scope 被重复绑定：" + scope);
        }
        table.put(group, scope);
    }
}
