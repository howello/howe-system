package com.howe.ai.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.howe.ai.contract.RunBudgetSnapshot;

/** 在模型/Tool 边界集中执行 Run 预算。 */
public class BudgetGuard {
    private final RunBudgetSnapshot budget;
    private final Clock clock;
    private final Instant deadline;
    private int modelCalls;
    private int toolCalls;
    private int outputTokens;
    private long estimatedCost;
    private int fallbackAttempts;
    private boolean costComputable = true;

    public BudgetGuard(RunBudgetSnapshot budget, Clock clock) {
        this.budget = Objects.requireNonNull(budget, "运行预算不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.deadline = Instant.now(clock).plusSeconds(budget.maxDurationSeconds());
    }

    public boolean allowModelCall() {
        return !expired() && modelCalls < budget.maxModelCalls() && estimatedCost < budget.maxEstimatedCost();
    }

    public boolean withinOutputBudget() {
        // 成本不可精算时不能用伪造的 0 去通过成本校验，只保留可测量的输出上限。
        if (!costComputable) return outputTokens <= budget.maxOutputTokens();
        return outputTokens <= budget.maxOutputTokens() && estimatedCost <= budget.maxEstimatedCost();
    }

    public boolean allowToolCall() {
        return !expired() && toolCalls < budget.maxToolCalls();
    }

    public boolean allowFallback() {
        return !expired() && fallbackAttempts < budget.maxFallbackAttempts();
    }

    public void recordModelCall(int outputTokens, long estimatedCost) {
        if (outputTokens < 0 || estimatedCost < 0) throw new IllegalArgumentException("调用用量不能为负数");
        modelCalls++;
        this.outputTokens += outputTokens;
        this.estimatedCost += estimatedCost;
    }

    /**
     * 记录一次无法计算成本的模型调用。
     *
     * <p>Provider 未上报用量，或没有可用的价格快照时走这里：输出上限仍按可测量的数量兜底，
     * 但成本被标记为不可精算——把缺失用量折算成零成本会让预算与成本审计失真。</p>
     */
    public void recordModelCallWithoutCost(int outputUnits) {
        if (outputUnits < 0) throw new IllegalArgumentException("调用用量不能为负数");
        modelCalls++;
        this.outputTokens += outputUnits;
        this.costComputable = false;
    }

    /** 本次运行的成本是否可以精确计算；任何一次调用缺失用量都会让整体不可精算。 */
    public boolean costComputable() {
        return costComputable;
    }

    public void recordToolCall() {
        toolCalls++;
    }

    public void recordFallback() {
        fallbackAttempts++;
    }

    public boolean expired() {
        return !deadline.isAfter(Instant.now(clock)) || outputTokens > budget.maxOutputTokens();
    }
}
