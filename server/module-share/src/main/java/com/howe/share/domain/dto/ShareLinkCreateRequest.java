package com.howe.share.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

/**
 * 创建分享请求
 *
 * <p>三种来源共用同一个 DTO，按 {@code sourceType} 条件校验：</p>
 * <ul>
 *   <li>{@code WEBDAV}：需要 {@code sourceId} + {@code filePath}（缺省即此类型，兼容旧前端）；</li>
 *   <li>{@code LOCAL}：需要 {@code uploadToken}（本地上传返回的令牌）；</li>
 *   <li>{@code REMOTE}：需要 {@code remoteUrl}。</li>
 * </ul>
 *
 * @param sourceType    来源类型：WEBDAV / LOCAL / REMOTE，为空按 WEBDAV 处理
 * @param sourceId      WebDAV 源ID
 * @param filePath      WebDAV 源文件路径
 * @param uploadToken   本地上传令牌
 * @param remoteUrl     远程链接地址
 * @param password      访问密码，为空表示无密码
 * @param expireAt      过期时间，为空表示不过期
 * @param maxViews      最大查看次数，&lt;=0 表示不限；为空默认 1
 * @param burnAfterRead 阅后即焚（0否 1是），为空默认 0
 * @param remark        备注
 * @author howe
 */
@Schema(description = "创建分享请求")
public record ShareLinkCreateRequest(
        @Schema(description = "来源类型：WEBDAV / LOCAL / REMOTE，为空按 WEBDAV 处理", example = "WEBDAV")
        String sourceType,

        @Schema(description = "WebDAV 源ID（仅 WEBDAV 必填）", example = "1")
        Long sourceId,

        @Schema(description = "WebDAV 源文件路径（仅 WEBDAV 必填）", example = "/文档/季度报表.pdf")
        String filePath,

        @Schema(description = "本地上传令牌（仅 LOCAL 必填）", example = "u_2f1c9a...")
        String uploadToken,

        @Schema(description = "远程链接地址（仅 REMOTE 必填）", example = "https://example.com/file.pdf")
        String remoteUrl,

        @Schema(description = "访问密码，为空表示无密码")
        String password,

        @Schema(description = "过期时间，为空表示不过期")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        Date expireAt,

        @Schema(description = "最大查看次数，<=0 表示不限；为空默认 1", example = "1")
        Integer maxViews,

        @Schema(description = "阅后即焚（0否 1是），为空默认 0", example = "0")
        String burnAfterRead,

        @Schema(description = "备注")
        String remark) {

    /**
     * 归一化后的来源类型
     *
     * <p>未传时按 WEBDAV 处理，兼容旧前端。</p>
     *
     * @return WEBDAV / LOCAL / REMOTE
     */
    public String normalizedSourceType() {
        if (sourceType == null || sourceType.isBlank()) {
            return "WEBDAV";
        }
        String value = sourceType.trim().toUpperCase();
        if (!"WEBDAV".equals(value) && !"LOCAL".equals(value) && !"REMOTE".equals(value)) {
            throw new IllegalArgumentException("不支持的来源类型：" + sourceType);
        }
        return value;
    }
}
