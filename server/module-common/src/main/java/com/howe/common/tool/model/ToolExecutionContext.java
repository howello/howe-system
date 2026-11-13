package com.howe.common.tool.model;

/**
 * 工具执行的可信业务上下文。
 *
 * <p>字段由平台从执行线程重新加载的当前账号与可信服务端调用上下文构造，
 * 不来自模型参数，也不包含 token、grant、OAuth scope、resource 或可变 {@code LoginUser}。</p>
 *
 * @param userId 当前账号 ID，字符串形式
 * @param deptId 当前账号所属部门 ID；为 {@code null} 表示没有家庭归属
 * @param requestId 本次调用的服务端请求标识，仅用于追踪与审计，不写入工具响应
 */
public record ToolExecutionContext(String userId, String deptId, String requestId) {
}
