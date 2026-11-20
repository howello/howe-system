package com.howe.pregnancy.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 请求摘要工具
 *
 * <p>可重试的创建操作在业务行上保存 requestId 与请求摘要：同一范围、同 ID 同内容
 * 返回同一资源；内容不同时返回幂等冲突。摘要用规范化后的关键字段计算，不依赖
 * JSON 序列化顺序。</p>
 *
 * @author howe
 */
public final class RequestHashUtils {

    /**
     * 工具类不允许实例化
     */
    private RequestHashUtils() {
    }

    /**
     * 计算字段摘要
     *
     * <p>入参按给定顺序以 {@code \u0001} 分隔；{@code null} 统一写为空串占位，
     * 避免 null 与空串被算成不同请求。</p>
     *
     * @param parts 参与摘要的字段
     * @return 64 位十六进制摘要
     */
    public static String hash(Object... parts) {
        StringBuilder builder = new StringBuilder();
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    builder.append('\u0001');
                }
                Object part = parts[i];
                builder.append(part == null ? "" : String.valueOf(part));
            }
        }
        return sha256(builder.toString());
    }

    /**
     * 计算字符串的 SHA-256
     *
     * @param text 文本
     * @return 64 位十六进制摘要
     */
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }
}
