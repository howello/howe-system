package com.howe.share.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WebDAV 目录浏览条目
 *
 * @param name         名称
 * @param path         相对源的完整路径（创建分享时回传）
 * @param directory    是否目录
 * @param size         字节数（目录为 0）
 * @param contentType  MIME 类型
 * @param lastModified 最后修改时间（ISO 字符串，可能为空）
 * @author howe
 */
@Schema(description = "WebDAV 目录浏览条目")
public record WebDavEntry(
        @Schema(description = "名称", example = "季度报表.pdf")
        String name,

        @Schema(description = "相对源的完整路径", example = "/文档/季度报表.pdf")
        String path,

        @Schema(description = "是否目录", example = "false")
        boolean directory,

        @Schema(description = "字节数", example = "2411520")
        long size,

        @Schema(description = "MIME 类型", example = "application/pdf")
        String contentType,

        @Schema(description = "最后修改时间")
        String lastModified) {
}
