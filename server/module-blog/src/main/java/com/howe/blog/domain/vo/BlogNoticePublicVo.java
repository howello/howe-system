package com.howe.blog.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * 公开公告视图对象
 *
 * @param noticeId 公告ID
 * @param noticeTitle 公告标题
 * @param noticeContent 公告HTML内容
 * @param createTime 创建时间
 * @author howe
 */
@Schema(description = "公开公告（仅展示字段）")
public record BlogNoticePublicVo(
        @Schema(description = "公告ID", example = "1") Long noticeId,
        @Schema(description = "公告标题") String noticeTitle,
        @Schema(description = "公告HTML内容") String noticeContent,
        @Schema(description = "创建时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime) {
}
