package com.howe.ai.route;

import com.howe.ai.api.AiErrorCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * 降级与重试策略
 *
 * <p>哪些错误值得换模型再试、哪些必须直接抛给调用方，规则集中在这里。</p>
 *
 * @author howe
 */
public final class AiFallbackPolicy
{
    /**
     * 可以降级的错误：换一个模型有可能成功
     *
     * <p>鉴权失败与渠道停用也算在内——主渠道被停用或密钥失效时，
     * 备用渠道正是为此准备的。</p>
     */
    private static final Set<AiErrorCode> FALLBACKABLE = EnumSet.of(
            AiErrorCode.TIMEOUT,
            AiErrorCode.PROVIDER_ERROR,
            AiErrorCode.RATE_LIMITED,
            AiErrorCode.CHANNEL_DISABLED,
            AiErrorCode.AUTH_FAILED);

    /** 可以在同一模型上重试的瞬时错误 */
    private static final Set<AiErrorCode> RETRYABLE = EnumSet.of(
            AiErrorCode.TIMEOUT,
            AiErrorCode.PROVIDER_ERROR,
            AiErrorCode.RATE_LIMITED);

    private AiFallbackPolicy()
    {
    }

    /**
     * 判断该错误是否允许降级到备用模型
     *
     * @param code 错误码
     * @return 允许降级时为 true
     */
    public static boolean canFallback(AiErrorCode code)
    {
        return FALLBACKABLE.contains(code);
    }

    /**
     * 判断该错误是否允许在同一模型上重试
     *
     * @param code 错误码
     * @return 允许重试时为 true
     */
    public static boolean canRetry(AiErrorCode code)
    {
        return RETRYABLE.contains(code);
    }
}
