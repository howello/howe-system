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
public class AiProperties {
    /** 固定的对话超时（毫秒）。 */
    public static final long CHAT_TIMEOUT_MS = 60_000L;

    /** 总开关，关闭时所有 AI 调用直接抛 AI_DISABLED */
    private boolean enabled;

    /** 是否启用降级链 */
    private boolean fallbackEnabled;

    /** 单次最多生成张数 */
    private int imageMaxCount;

    /**
     * 从参数配置表加载快照
     *
     * @return 参数快照
     */
    public static AiProperties load() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(ConfigUtils.getBoolean(ConfigConstants.AI_ENABLED, false));
        properties.setFallbackEnabled(ConfigUtils.getBoolean(ConfigConstants.AI_FALLBACK_ENABLED, true));
        properties.setImageMaxCount(Math.max(1, Math.min(4, ConfigUtils.getInt(ConfigConstants.AI_IMAGE_MAX_COUNT, 4))));
        return properties;
    }
}
