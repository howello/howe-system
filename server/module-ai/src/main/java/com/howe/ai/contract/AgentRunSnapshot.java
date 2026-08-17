package com.howe.ai.contract;

import com.howe.ai.domain.RunStatus;

public record AgentRunSnapshot(String runId, RunStatus status) {
    public AgentRunSnapshot {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("运行标识不能为空");
        if (status == null) throw new IllegalArgumentException("运行状态不能为空");
    }
}
