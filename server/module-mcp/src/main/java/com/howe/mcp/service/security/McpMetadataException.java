package com.howe.mcp.service.security;

/** 元数据发现只区分短暂网络故障与确定拒绝，不携带 URL、DNS 结果或原始异常。 */
public final class McpMetadataException extends RuntimeException {
    private final boolean transientFailure;

    /** 调用方依据失败类别决定本站拒绝、503 或后续可信状态处理。 */
    public McpMetadataException(boolean transientFailure) {
        super(transientFailure ? "客户端元数据暂不可用" : "客户端元数据不符合要求");
        this.transientFailure = transientFailure;
    }

    /** 短暂失败不应单独撤销此前持久化授权。 */
    public boolean isTransientFailure() { return transientFailure; }
}
