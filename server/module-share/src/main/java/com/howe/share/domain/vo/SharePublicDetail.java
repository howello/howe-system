package com.howe.share.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * 公开端分享详情（不含文件内容）
 *
 * <p>{@link #state} 取值：{@code ok} 就绪、{@code caching} 缓存中、{@code failed} 缓存失败、
 * {@code expired} 已过期、{@code invalid} 已失效、{@code burned} 已焚毁、
 * {@code exhausted} 次数用尽、{@code notfound} 不存在。</p>
 *
 * @param code           短码
 * @param state          状态
 * @param message        状态说明
 * @param fileName       文件名
 * @param fileSize       字节数
 * @param fileExt        扩展名（小写，不含点）
 * @param contentType    MIME 类型
 * @param needPwd        是否需要密码
 * @param verified       当前请求方是否已通过密码校验（持有有效票据）
 * @param cacheStatus    缓存状态（0待缓存 1缓存中 2就绪 3失败）
 * @param remainingViews 剩余查看次数，{@code null} 表示不限
 * @param expireAt       过期时间，{@code null} 表示不过期
 * @param burnAfterRead  是否阅后即焚
 * @author howe
 */
@Schema(description = "公开端分享详情")
public record SharePublicDetail(
        @Schema(description = "短码", example = "a1B2c3D4")
        String code,

        @Schema(description = "状态", example = "ok")
        String state,

        @Schema(description = "状态说明")
        String message,

        @Schema(description = "文件名", example = "季度报表.pdf")
        String fileName,

        @Schema(description = "字节数", example = "2411520")
        Long fileSize,

        @Schema(description = "扩展名（小写，不含点）", example = "pdf")
        String fileExt,

        @Schema(description = "MIME 类型", example = "application/pdf")
        String contentType,

        @Schema(description = "是否需要密码", example = "false")
        boolean needPwd,

        @Schema(description = "当前请求方是否已通过密码校验", example = "false")
        boolean verified,

        @Schema(description = "缓存状态（0待缓存 1缓存中 2就绪 3失败）", example = "2")
        String cacheStatus,

        @Schema(description = "剩余查看次数，null 表示不限")
        Integer remainingViews,

        @Schema(description = "过期时间，null 表示不过期")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        Date expireAt,

        @Schema(description = "是否阅后即焚", example = "false")
        boolean burnAfterRead) {
}
