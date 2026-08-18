package com.howe.ai.runtime;

import com.howe.ai.contract.ProviderErrorCategory;

public record RuntimeResult(boolean success, String content, ProviderErrorCategory errorCategory) {
    public static RuntimeResult success(String content) {
        return new RuntimeResult(true, content, null);
    }

    public static RuntimeResult failure(ProviderErrorCategory category) {
        return new RuntimeResult(false, null, category);
    }
}
