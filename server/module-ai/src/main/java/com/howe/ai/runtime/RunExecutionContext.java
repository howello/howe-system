package com.howe.ai.runtime;

import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.RunBudgetSnapshot;

/**
 * Harness 的执行上下文，由 Run 创建时固化的快照装配。
 *
 * <p>只读取 {@code ai_run} 的路由/预算快照与已发布版本的 Prompt；快照缺失或非法时直接抛出，
 * 由调用方把 Run 判为失败，绝不退化成「用默认值继续跑」。</p>
 */
public record RunExecutionContext(ModelRequest request, RunBudgetSnapshot budget) {
    public static RunExecutionContext from(Map<String, Object> row) {
        if (row == null || row.isEmpty()) throw new IllegalStateException("运行上下文不存在");
        JSONObject route = parse(row.get("route_snapshot"), "路由快照");
        JSONObject budget = parse(row.get("budget_snapshot"), "预算快照");
        String model = route.getString("model");
        if (model == null || model.isBlank()) throw new IllegalStateException("路由快照缺少模型");
        return new RunExecutionContext(new ModelRequest(model, prompt(row), Map.of()),
            new RunBudgetSnapshot(budget.getLongValue("maxDurationSeconds"), budget.getIntValue("maxModelCalls"),
                budget.getIntValue("maxToolCalls"), budget.getIntValue("maxOutputTokens"),
                budget.getLongValue("maxEstimatedCost"), budget.getIntValue("maxFallbackAttempts")));
    }

    private static String prompt(Map<String, Object> row) {
        String system = text(row.get("system_prompt"));
        String user = text(row.get("user_prompt"));
        if (user.isBlank()) throw new IllegalStateException("运行缺少用户消息");
        return system.isBlank() ? user : system + "\n\n" + user;
    }

    private static JSONObject parse(Object value, String field) {
        String text = text(value);
        if (text.isBlank()) throw new IllegalStateException(field + "为空");
        JSONObject parsed = JSON.parseObject(text);
        if (parsed == null) throw new IllegalStateException(field + "不是合法 JSON");
        return parsed;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
