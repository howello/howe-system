package com.howe.ai.domain;

import java.util.EnumSet;
import java.util.Set;

public enum RunStatus {
    CREATED, QUEUED, RUNNING, CANCEL_REQUESTED, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT;

    public boolean canTransitionTo(RunStatus target) {
        return switch (this) {
            case CREATED -> target == QUEUED;
            case QUEUED -> target == RUNNING || target == CANCEL_REQUESTED;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCEL_REQUESTED || target == TIMED_OUT;
            case CANCEL_REQUESTED -> target == CANCELLED || target == FAILED;
            case SUCCEEDED, FAILED, CANCELLED, TIMED_OUT -> false;
        };
    }

    public boolean isTerminal() {
        return EnumSet.of(SUCCEEDED, FAILED, CANCELLED, TIMED_OUT).contains(this);
    }

    public static Set<RunStatus> terminalStatuses() {
        return Set.of(SUCCEEDED, FAILED, CANCELLED, TIMED_OUT);
    }
}
