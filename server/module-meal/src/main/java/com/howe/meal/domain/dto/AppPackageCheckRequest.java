package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * APP 资源包远程链接校验请求
 *
 * <p>只做地址校验，不产生可复用凭据：保存时服务端会按需重新校验，
 * 不信任前端传来的「校验通过」。</p>
 *
 * @param url 远程 wgt 地址（https）
 * @author howe
 */
@Schema(description = "APP 资源包远程链接校验请求")
public record AppPackageCheckRequest(
        @NotBlank(message = "远程地址不能为空")
        @Schema(description = "远程 wgt 地址（https）", example = "https://cdn.example.com/app/app.wgt")
        String url) {
}
