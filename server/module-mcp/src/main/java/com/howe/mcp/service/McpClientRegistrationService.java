package com.howe.mcp.service;

import com.howe.mcp.dto.McpClientMetadata;
import com.howe.mcp.service.security.McpRegistrationException;
import java.util.Map;

/** OAuth 客户端自动登记服务接口。 */
public interface McpClientRegistrationService {
    /** 只在运行开关开启时允许新登记。 */
    void requireEnabled();

    /** 签发稳定随机 client_id，secret 单向编码；两个登记表在同一事务中保存。 */
    Map<String, Object> register(McpClientMetadata metadata);

    /** 参数非法时关闭入口，避免错误值变成无上限。 */
    public static int parseLimit(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new McpRegistrationException("temporarily_unavailable", 503, "客户端登记暂不可用");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new McpRegistrationException("temporarily_unavailable", 503, "客户端登记暂不可用");
        }
    }
}
