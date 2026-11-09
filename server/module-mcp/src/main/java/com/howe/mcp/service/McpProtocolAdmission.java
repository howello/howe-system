package com.howe.mcp.service;


/** MCP 协议请求限流服务接口。 */
public interface McpProtocolAdmission {
    /** 白名单维度避免把任意客户端 ID、路径或令牌当作无限增长的限流分类。 */
    public enum Bucket {
        MCP_IP("mcpIp", 300), MCP_USER("mcpUser", 120), AUTHORIZE_IP("authorizeIp", 60),
        TOKEN_IP("tokenIp", 60), REVOKE_IP("revokeIp", 60), METADATA_IP("metadataIp", 120),
        LOGIN_IP("loginIp", 10), BROWSER_IP("browserIp", 120);

        private final String key;
        private final int maximum;

        /** 每个维度具有独立配置键和每分钟缺省额度。 */
        Bucket(String key, int maximum) { this.key = key; this.maximum = maximum; }
        /** 配额配置键。 */
        public String key() { return key; }
        /** 缺省每分钟额度。 */
        public int maximum() { return maximum; }
    }

    /** 消耗请求额度；仅存主体哈希，不保存原始 IP 或任何令牌。 */
    void acquire(Bucket bucket, String subject);

    /** 边界仅返回稳定状态，不传递 Redis、参数配置或网络异常内容。 */
    public static final class Rejected extends RuntimeException {
        private final int status;
        private final int retryAfter;

        /** 只允许服务端生成拒绝状态与等待秒数。 */
        public Rejected(int status, int retryAfter) {
            super("MCP 请求暂不可用");
            this.status = status; this.retryAfter = retryAfter;
        }

        /** HTTP 拒绝状态。 */
        public int status() { return status; }
        /** 超限后可重试的秒数，基础设施故障不承诺恢复时间。 */
        public int retryAfter() { return retryAfter; }
    }
}
