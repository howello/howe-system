package com.howe.ai.contract;

/** 模型渠道的内部稳定契约，不暴露 Provider SDK 类型。 */
public interface ModelGateway {
    ModelStreamResult stream(ModelRequest request);

    default ModelResult complete(ModelRequest request) {
        ModelStreamResult result = stream(request);
        if (!result.success()) return new ModelResult(null, result.errorCategory(), result.providerRequestId());
        return ModelResult.success(result.deltas().stream().map(ModelDelta::content).reduce("", String::concat),
            result.providerRequestId());
    }
}
