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
 * 博客站点对象 blog_link
 *
 * <p>友链与 RSS 订阅源共用本表，由 {@code linkType} 区分：1=友链 2=RSS订阅源。
 * 二者语义独立、互不关联；{@code groupCode} 仅友链使用，
 * {@code rssUrl}/{@code lastSyncTime}/{@code lastError} 仅订阅源使用。</p>
 *
 * <p>共表带来的越权风险由「单条操作同时约束 link_id 与 link_type」拦截，
 * 见 BlogLinkMapper.xml 的 update/delete 语句。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "博客站点（友链/RSS订阅源）")
public class BlogLink extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1")
    private Long linkId;

    @Schema(description = "类型（1友链 2RSS订阅源）", example = "1")
    private String linkType;

    @Schema(description = "站点名称", example = "王艳涛博客")
    @NotBlank(message = "站点名称不能为空")
    @Size(max = 128, message = "站点名称长度不能超过128个字符")
    private String linkName;

    @Schema(description = "站点地址", example = "https://www.wyantao.com")
    @Size(max = 500, message = "站点地址长度不能超过500个字符")
    private String linkUrl;

    @Schema(description = "头像/图标地址", example = "https://img.wyantao.com/avatar.png")
    @Size(max = 500, message = "头像地址长度不能超过500个字符")
    private String avatar;

    @Schema(description = "站点描述", example = "一个热爱技术的博客")
    @Size(max = 500, message = "站点描述长度不能超过500个字符")
    private String descr;

    @Schema(description = "友链分组（字典 blog_link_group，仅友链使用）", example = "tech")
    @Size(max = 64, message = "分组编码长度不能超过64个字符")
    private String groupCode;

    @Schema(description = "RSS/Atom 订阅地址（仅订阅源使用）", example = "https://www.wyantao.com/rss.xml")
    @Size(max = 500, message = "订阅地址长度不能超过500个字符")
    private String rssUrl;

    @Schema(description = "最后同步时间（仅订阅源）", example = "2026-08-01 12:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSyncTime;

    @Schema(description = "最后一次同步失败原因，成功时清空（仅订阅源）")
    private String lastError;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "显示顺序", example = "0")
    private Integer orderNum;
}
