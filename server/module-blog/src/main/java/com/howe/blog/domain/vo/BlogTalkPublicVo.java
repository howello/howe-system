package com.howe.blog.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * 公开说说视图对象
 *
 * <p>与 {@link BlogLinkPublicVo} 同理：不把继承自 {@code BaseEntity} 的
 * {@code createBy}/{@code updateBy}/{@code remark} 暴露给匿名访客。</p>
 *
 * @param content 正文（markdown 原文，由前端渲染）
 * @param tags    标签，多个用逗号分隔
 * @param pubDate 发布时间
 * @param isTop   是否置顶（0否 1是）
 * @author howe
 */
@Schema(description = "公开说说（仅展示字段）")
public record BlogTalkPublicVo(
        @Schema(description = "正文（markdown 原文）") String content,
        @Schema(description = "标签，多个用逗号分隔") String tags,
        @Schema(description = "发布时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date pubDate,
        @Schema(description = "是否置顶（0否 1是）") String isTop) {
}
