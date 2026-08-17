package com.howe.ai.domain;

public final class RunStateMachine {
    private RunStateMachine() { }

    public static void requireTransition(RunStatus current, RunStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("非法 Run 状态转移: " + current + " -> " + target);
        }
    }

    public static java.util.Set<RunStatus> terminalStatuses() {
        return RunStatus.terminalStatuses();
    }
}
