package com.howe.share.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

/**
 * 创建分享请求
 *
 * @param sourceId      源ID
 * @param filePath      WebDAV 源文件路径
 * @param password      访问密码，为空表示无密码
 * @param expireAt      过期时间，为空表示不过期
 * @param maxViews      最大查看次数，&lt;=0 表示不限；为空默认 1
 * @param burnAfterRead 阅后即焚（0否 1是），为空默认 0
 * @param remark        备注
 * @author howe
 */
@Schema(description = "创建分享请求")
public record ShareLinkCreateRequest(
        @Schema(description = "源ID", example = "1")
        @NotNull(message = "源不能为空")
        Long sourceId,

        @Schema(description = "WebDAV 源文件路径", example = "/文档/季度报表.pdf")
        @NotBlank(message = "文件路径不能为空")
        String filePath,

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
}
