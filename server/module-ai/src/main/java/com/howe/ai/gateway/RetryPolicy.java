package com.howe.ai.gateway;

import com.howe.ai.contract.ProviderErrorCategory;

/** Router 的重试和 fallback 错误策略。 */
public final class RetryPolicy {
    private RetryPolicy() {
    }

    public static boolean canRetry(ProviderErrorCategory category, int attempt) {
        return attempt < 2 && (category == ProviderErrorCategory.NETWORK_TIMEOUT
            || category == ProviderErrorCategory.RATE_LIMITED || category == ProviderErrorCategory.SERVER_ERROR);
    }

    public static boolean canFallback(ProviderErrorCategory category) {
        return category == ProviderErrorCategory.NETWORK_TIMEOUT
            || category == ProviderErrorCategory.RATE_LIMITED || category == ProviderErrorCategory.SERVER_ERROR;
    }
}
