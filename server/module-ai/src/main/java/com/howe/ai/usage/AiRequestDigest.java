package com.howe.ai.usage;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;

import java.util.List;
import java.util.Map;

/**
 * 调用记录入参摘要
 *
 * <p>只记录可用于排查的元信息：场景、能力、模型、渠道、长度与摘要值。
 * <b>不写完整 prompt、不写密钥、不写 metadata 的值</b>，避免调用记录变成敏感数据的副本。</p>
 *
 * @author howe
 */
public final class AiRequestDigest
{
    /** 摘要字段上限，对应 ai_call_log.request_digest 的长度 */
    private static final int MAX_LENGTH = 500;

    private AiRequestDigest()
    {
    }

    /**
     * 构造脱敏摘要
     *
     * @param scene        场景标识
     * @param capability   能力类型
     * @param explicitChan 请求显式指定的渠道
     * @param explicitMod  请求显式指定的模型
     * @param prompt       提示词，可为空
     * @param messageCount 消息条数
     * @param metadata     业务附加信息，只记录键的数量
     * @return 摘要字符串
     */
    public static String of(String scene, String capability, String explicitChan, String explicitMod,
            String prompt, Integer messageCount, Map<String, String> metadata)
    {
        StringBuilder builder = new StringBuilder(160);
        builder.append("scene=").append(StrUtil.blankToDefault(scene, "-"));
        builder.append(";capability=").append(StrUtil.blankToDefault(capability, "-"));
        builder.append(";reqChannel=").append(StrUtil.blankToDefault(explicitChan, "-"));
        builder.append(";reqModel=").append(StrUtil.blankToDefault(explicitMod, "-"));
        builder.append(";promptLength=").append(prompt == null ? 0 : prompt.length());
        if (StrUtil.isNotBlank(prompt))
        {
            builder.append(";promptDigest=").append(shortDigest(prompt));
        }
        builder.append(";messageCount=").append(messageCount == null ? 0 : messageCount);
        builder.append(";metadataKeys=").append(metadata == null ? 0 : metadata.size());
        String digest = builder.toString();
        return digest.length() > MAX_LENGTH ? digest.substring(0, MAX_LENGTH) : digest;
    }

    /**
     * 拼接多条文本的摘要信息，用于向量请求
     *
     * @param scene      场景标识
     * @param capability 能力类型
     * @param texts      文本列表
     * @return 摘要字符串
     */
    public static String ofTexts(String scene, String capability, List<String> texts)
    {
        int total = 0;
        StringBuilder joined = new StringBuilder();
        if (texts != null)
        {
            for (String text : texts)
            {
                if (text != null)
                {
                    total += text.length();
                    joined.append(text);
                }
            }
        }
        return "scene=" + StrUtil.blankToDefault(scene, "-")
                + ";capability=" + StrUtil.blankToDefault(capability, "-")
                + ";textCount=" + (texts == null ? 0 : texts.size())
                + ";textLength=" + total
                + (total > 0 ? ";textDigest=" + shortDigest(joined.toString()) : "");
    }

    /**
     * 取内容的短摘要
     *
     * @param content 原文
     * @return 前 16 位 SHA-256
     */
    private static String shortDigest(String content)
    {
        return DigestUtil.sha256Hex(content).substring(0, 16);
    }
}
