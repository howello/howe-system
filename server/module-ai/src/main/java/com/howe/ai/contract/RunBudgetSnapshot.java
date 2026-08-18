package com.howe.ai.contract;

public record RunBudgetSnapshot(long maxDurationSeconds, int maxModelCalls, int maxToolCalls,
                                int maxOutputTokens, long maxEstimatedCost, int maxFallbackAttempts) {
    public RunBudgetSnapshot {
        if (maxDurationSeconds < 0 || maxModelCalls < 0 || maxToolCalls < 0
            || maxOutputTokens < 0 || maxEstimatedCost < 0 || maxFallbackAttempts < 0) {
            throw new IllegalArgumentException("运行预算不能为负数");
        }
    }
}
