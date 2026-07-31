package com.howe.system.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公告已读记录表 sys_notice_read
 *
 * @author howe
 */
@Schema(description = "公告已读记录")
public class SysNoticeRead
{
    /** 主键 */
    @Schema(description = "主键", example = "1")
    private Long readId;

    /** 公告ID */
    @Schema(description = "公告ID", example = "1")
    private Long noticeId;

    /** 用户ID */
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    /** 阅读时间 */
    @Schema(description = "阅读时间", example = "2026-07-31 10:00:00")
    private Date readTime;

    public Long getReadId()
    {
        return readId;
    }

    public void setReadId(Long readId)
    {
        this.readId = readId;
    }

    public Long getNoticeId()
    {
        return noticeId;
    }

    public void setNoticeId(Long noticeId)
    {
        this.noticeId = noticeId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Date getReadTime()
    {
        return readTime;
    }

    public void setReadTime(Date readTime)
    {
        this.readTime = readTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("readId", getReadId())
            .append("noticeId", getNoticeId())
            .append("userId", getUserId())
            .append("readTime", getReadTime())
            .toString();
    }
}
