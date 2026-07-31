package com.howe.framework.captcha;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 验证码下发信息
 *
 * <p>
 * 只带前端渲染需要的字段，答案留在 {@link CaptchaService} 内部与 Redis 里。
 * </p>
 *
 * @param uuid 验证码标识，登录时原样回传
 * @param img 图片 base64，不含 data URI 前缀
 * @param imgType 图片格式，png 或 gif，前端据此拼 data URI
 * @param captchaType 验证码类型，前端可据此调整输入提示
 * @author howe
 */
@Schema(description = "验证码下发信息")
public record CaptchaVo(
        @Schema(description = "验证码标识，登录时原样回传", example = "5d0c3f0d5a1c4d2f8e6b7a9c0d1e2f3a")
        String uuid,

        @Schema(description = "图片 base64，不含 data URI 前缀")
        String img,

        @Schema(description = "图片格式", example = "png", allowableValues = { "png", "gif" })
        String imgType,

        @Schema(description = "验证码类型", example = "math",
                allowableValues = { "char", "math", "line", "circle", "shear", "gif" })
        String captchaType)
{
}
