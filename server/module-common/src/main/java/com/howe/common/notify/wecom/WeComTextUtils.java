package com.howe.common.notify.wecom;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;

/**
 * 企业微信消息文本工具
 *
 * <p>官方对 text / markdown / news 的字段限制均按 UTF-8 字节计算，这里统一提供按字节截断，
 * 避免中文内容按字符数截断后仍然超限。</p>
 *
 * @author howe
 */
public final class WeComTextUtils {

    private WeComTextUtils() {
    }

    /**
     * 按 UTF-8 字节数截断文本，不拆分多字节字符
     *
     * @param text     原始文本
     * @param maxBytes 最大字节数
     * @return 截断后的文本；原文未超限时原样返回
     */
    public static String truncateUtf8(String text, int maxBytes) {
        if (text == null || maxBytes <= 0) {
            return text;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return text;
        }
        int bytes = 0;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            int codePointBytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            result.appendCodePoint(codePoint);
            bytes += codePointBytes;
            index += charCount;
        }
        return result.toString();
    }

    /**
     * 判断文本是否为空
     *
     * @param text 文本
     * @return 为空返回 true
     */
    public static boolean isBlank(String text) {
        return StrUtil.isBlank(text);
    }

    /**
     * 掩码显示 webhook key，只保留末尾 4 位
     *
     * @param key webhook key
     * @return 掩码后的 key
     */
    public static String maskKey(String key) {
        if (isBlank(key)) {
            return "****";
        }
        String trimmed = key.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
