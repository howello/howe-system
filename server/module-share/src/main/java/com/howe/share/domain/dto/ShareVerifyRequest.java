package com.howe.share.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 公开端密码校验请求
 *
 * @param code     短码
 * @param password 访问密码
 * @author howe
 */
@Schema(description = "密码校验请求")
public record ShareVerifyRequest(
        @Schema(description = "短码", example = "a1B2c3D4")
        @NotBlank(message = "短码不能为空")
        String code,

        @Schema(description = "访问密码")
        @NotBlank(message = "密码不能为空")
        String password) {
}
