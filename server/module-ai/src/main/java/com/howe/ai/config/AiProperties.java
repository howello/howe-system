package com.howe.ai.config;

import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import lombok.Data;

/**
 * AI 模块的全局参数快照
 *
 * <p>对应 {@code sys_config} 里的 {@code ai.*} 键，由 {@link AiConfigService} 读取。
 * 单值、开关、模板走参数表，渠道与模型这类需要 CRUD 的走数据表。</p>
 *
 * @author howe
 */
@Data
public class AiProperties
{
    /** 总开关，关闭时所有 AI 调用直接抛 AI_DISABLED */
    private boolean enabled;

    /** 全局默认对话模型 ID */
    private Long defaultChatModelId;

    /** 全局默认视觉模型 ID */
    private Long defaultVisionModelId;

    /** 全局默认文生图模型 ID */
    private Long defaultImageModelId;

    /** 全局默认向量模型 ID */
    private Long defaultEmbeddingModelId;

    /** 是否启用降级链 */
    private boolean fallbackEnabled;

    /** 同模型瞬时错误重试次数 */
    private int retryMax;

    /** 重试退避基数（毫秒） */
    private long retryBackoffMs;

    /** 对话超时（毫秒） */
    private long timeoutChatMs;

    /** 文生图超时（毫秒） */
    private long timeoutImageMs;

    /** 单次最多生成张数 */
    private int imageMaxCount;

    /** 调用记录保留天数 */
    private int logRetentionDays;

    /**
     * 从参数配置表加载快照
     *
     * @return 参数快照
     */
    public static AiProperties load()
    {
        AiProperties properties = new AiProperties();
        properties.setEnabled(ConfigUtils.getBoolean(ConfigConstants.AI_ENABLED, false));
        properties.setDefaultChatModelId(readId(ConfigConstants.AI_DEFAULT_CHAT_MODEL));
        properties.setDefaultVisionModelId(readId(ConfigConstants.AI_DEFAULT_VISION_MODEL));
        properties.setDefaultImageModelId(readId(ConfigConstants.AI_DEFAULT_IMAGE_MODEL));
        properties.setDefaultEmbeddingModelId(readId(ConfigConstants.AI_DEFAULT_EMBEDDING_MODEL));
        properties.setFallbackEnabled(ConfigUtils.getBoolean(ConfigConstants.AI_FALLBACK_ENABLED, true));
        properties.setRetryMax(ConfigUtils.getInt(ConfigConstants.AI_RETRY_MAX, 1));
        properties.setRetryBackoffMs(ConfigUtils.getInt(ConfigConstants.AI_RETRY_BACKOFF_MS, 500));
        properties.setTimeoutChatMs(ConfigUtils.getInt(ConfigConstants.AI_TIMEOUT_CHAT, 60000));
        properties.setTimeoutImageMs(ConfigUtils.getInt(ConfigConstants.AI_TIMEOUT_IMAGE, 120000));
        properties.setImageMaxCount(ConfigUtils.getInt(ConfigConstants.AI_IMAGE_MAX_COUNT, 4));
        properties.setLogRetentionDays(ConfigUtils.getInt(ConfigConstants.AI_LOG_RETENTION_DAYS, 30));
        return properties;
    }

    /**
     * 读取模型 ID 配置，空值或非法值返回 null
     *
     * @param configKey 配置键
     * @return 模型 ID
     */
    private static Long readId(String configKey)
    {
        String value = ConfigUtils.getString(configKey, "");
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return Long.valueOf(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
