package com.howe.blog.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 博客说说对象 blog_talk
 *
 * <p>{@code content} 存 <b>markdown 原文</b>，不存渲染后的 HTML——渲染是 blog-ui 的职责，
 * 后端存原文才能让管理端重新打开时仍看到原始 markdown 语法。</p>
 *
 * <p>正文无需为全局 XSS 过滤器做豁免：{@code XssFilter} 在 {@code FilterConfig} 里按
 * {@code xss.urlPatterns}（/system/*、/monitor/*、/tool/*）注册，{@code /blog/**}
 * 结构上就不在其过滤范围内，正文中的 {@code <}、{@code >}、{@code &} 不会被改写。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "博客说说")
public class BlogTalk extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1")
    private Long talkId;

    @Schema(description = "正文（markdown 原文，不做 HTML 转换）", example = "今天读完了 **Effective Java**")
    @NotBlank(message = "说说正文不能为空")
    private String content;

    @Schema(description = "标签，多个用逗号分隔", example = "读书,随笔")
    @Size(max = 500, message = "标签长度不能超过500个字符")
    private String tags;

    @Schema(description = "发布时间，为空时由后端取当前时间", example = "2026-08-01 12:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pubDate;

    @Schema(description = "是否置顶（0否 1是）", example = "0")
    private String isTop;

    @Schema(description = "状态（0发布 1隐藏）", example = "0")
    private String status;
}
