package com.howe.ai.application;

public record AgentDraft(String agentKey, String name, String systemPrompt, String routeJson,
                         String toolJson, String budgetJson) {}
