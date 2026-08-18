package com.howe.ai.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RunStateMachineTest {
    @Test
    void supportsNormalCompletionAndCancellationPaths() {
        assertTrue(RunStatus.CREATED.canTransitionTo(RunStatus.QUEUED));
        assertTrue(RunStatus.QUEUED.canTransitionTo(RunStatus.RUNNING));
        assertTrue(RunStatus.RUNNING.canTransitionTo(RunStatus.SUCCEEDED));
        assertTrue(RunStatus.RUNNING.canTransitionTo(RunStatus.CANCEL_REQUESTED));
        assertTrue(RunStatus.CANCEL_REQUESTED.canTransitionTo(RunStatus.CANCELLED));
        assertTrue(RunStatus.RUNNING.canTransitionTo(RunStatus.TIMED_OUT));
    }

    @Test
    void rejectsIllegalAndTerminalTransitionsWithClearException() {
        assertFalse(RunStatus.CREATED.canTransitionTo(RunStatus.SUCCEEDED));
        assertThrows(IllegalStateException.class,
            () -> RunStateMachine.requireTransition(RunStatus.CREATED, RunStatus.RUNNING));
        assertThrows(IllegalStateException.class,
            () -> RunStateMachine.requireTransition(RunStatus.SUCCEEDED, RunStatus.FAILED));
        assertTrue(RunStateMachine.terminalStatuses().containsAll(Set.of(
            RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT)));
    }

    @Test
    void terminalRunCannotBeOverwritten() {
        assertThrows(IllegalStateException.class,
            () -> RunStateMachine.requireTransition(RunStatus.TIMED_OUT, RunStatus.SUCCEEDED));
    }
}
