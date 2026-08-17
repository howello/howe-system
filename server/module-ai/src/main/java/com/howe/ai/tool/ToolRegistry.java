package com.howe.ai.tool;

import com.howe.ai.contract.AiToolProvider;
import com.howe.ai.contract.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tool 注册表：启动时自动收集所有 {@link AiToolProvider} Bean（含博客 Provider 等提供方），
 * 按 toolName 建立索引，供安全调用链按名查找。
 *
 * <p>新增 Tool 提供方模块只需实现 {@link AiToolProvider} 并注册为 Bean，本注册表零改动即可
 * 收纳——这是把 {@code module-ai} 与 {@code module-blog} 解耦的关键聚合点。</p>
 */
@Service
public class ToolRegistry {

    private final Map<String, AiToolProvider> providersByToolName = new HashMap<>();

    @Autowired
    public ToolRegistry(List<AiToolProvider> providers) {
        Objects.requireNonNull(providers, "Tool Provider 列表不能为空");
        for (AiToolProvider provider : providers) {
            for (ToolDefinition def : provider.describeTools()) {
                AiToolProvider prior = providersByToolName.put(def.name(), provider);
                if (prior != null && prior != provider) {
                    throw new IllegalStateException(
                        "Tool 名称冲突：" + def.name() + " 被多个 Provider 注册");
                }
            }
        }
    }

    /** 所有已注册的 Tool 名集合。 */
    public Set<String> knownToolNames() {
        return Set.copyOf(providersByToolName.keySet());
    }

    /** 是否存在该 Tool。 */
    public boolean isKnown(String toolName) {
        return toolName != null && providersByToolName.containsKey(toolName);
    }

    /**
     * 按名查找 Provider。
     *
     * @return 命中返回 Provider；未知 Tool 返回空（调用链据此拒绝）
     */
    public java.util.Optional<AiToolProvider> findProvider(String toolName) {
        if (toolName == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(providersByToolName.get(toolName));
    }

    /** 汇总所有 Provider 的 Tool 定义，供权限/白名单校验展示。 */
    public List<ToolDefinition> describeAll() {
        return providersByToolName.values().stream()
            .distinct()
            .flatMap(p -> p.describeTools().stream())
            .toList();
    }
}
