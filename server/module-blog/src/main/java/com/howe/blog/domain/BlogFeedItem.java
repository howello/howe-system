package com.howe.blog.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 博客朋友圈条目 blog_feed_item
 *
 * <p>由 RSS 抓取任务写入，不经人工编辑，因此<b>不继承 BaseEntity</b>——
 * 那会带入 searchValue/params/update_by 等这张表用不到的字段。</p>
 *
 * <p>{@code linkName} 不是表字段，由 XML 里 join blog_link 带出，仅用于列表展示来源。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "博客朋友圈条目（RSS抓取结果）")
public class BlogFeedItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1")
    private Long itemId;

    @Schema(description = "来源订阅源ID", example = "2")
    private Long linkId;

    @Schema(description = "条目标题", example = "一篇博客文章")
    private String title;

    @Schema(description = "条目作者", example = "王艳涛")
    private String author;

    @Schema(description = "条目原文链接，同时是去重唯一键", example = "https://www.wyantao.com/article/1")
    private String url;

    @Schema(description = "摘要纯文本，抓取时已剥离HTML并截断")
    private String summary;

    @Schema(description = "发布时间", example = "2026-08-01 12:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pubDate;

    @Schema(description = "入库时间", example = "2026-08-01 12:05:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 非表字段：来源站点名称，由 join blog_link 带出 */
    @Schema(description = "来源站点名称（非表字段，join 带出）", example = "王艳涛博客")
    private String linkName;
}
