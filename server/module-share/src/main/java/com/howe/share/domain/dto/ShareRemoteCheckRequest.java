package com.howe.share.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 分享远程链接校验请求
 *
 * @param url 远程文件地址（https）
 * @author howe
 */
@Schema(description = "分享远程链接校验请求")
public record ShareRemoteCheckRequest(
        @NotBlank(message = "远程地址不能为空")
        @Schema(description = "远程文件地址（https）", example = "https://example.com/report.pdf")
        String url) {
}
