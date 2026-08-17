package com.howe.ai.application;

public interface AdminAssistantApplicationService {
    int MAX_PAGE_SIZE = 50;
    void validateDraft(AgentDraft draft);
    String maskSecret(String secret);
}
