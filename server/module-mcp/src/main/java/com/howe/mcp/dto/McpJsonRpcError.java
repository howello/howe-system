package com.howe.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.spec.McpSchema;

/** 无法确定请求标识时仍显式返回 id:null，不受 SDK 忽略空字段的规则影响。 */
public record McpJsonRpcError(String jsonrpc, @JsonInclude(JsonInclude.Include.ALWAYS) Object id,
        McpSchema.JSONRPCResponse.JSONRPCError error) {

    /** 错误只包含固定消息与标准代码，不携带原始报文或内部异常。 */
    public McpJsonRpcError(Object id, int code, String message) {
        this("2.0", id, new McpSchema.JSONRPCResponse.JSONRPCError(code, message, null));
    }
}
