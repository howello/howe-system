package com.howe.ai.contract;

import java.util.Set;

public record ToolAllowlistSnapshot(Set<String> toolNames) {
    public ToolAllowlistSnapshot {
        if (toolNames == null || toolNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("工具白名单不能为空且不能包含空名称");
        }
        toolNames = Set.copyOf(toolNames);
    }
}
