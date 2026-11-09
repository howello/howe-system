package com.howe.mcp.service;


/** OAuth 客户端登记限流服务接口。 */
public interface McpRegistrationAdmission {
    /** 在读取登记文档之前消耗每 IP 每分钟配额，键中不保存原始 IP。 */
    void acquire(String ip);
}
