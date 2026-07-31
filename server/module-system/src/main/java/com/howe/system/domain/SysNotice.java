package com.howe.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.howe.common.core.domain.BaseEntity;
import com.howe.common.xss.Xss;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通知公告表 sys_notice
 *
 * @author howe
 */
@Schema(description = "通知公告")
public class SysNotice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 公告ID */
    @Schema(description = "公告ID", example = "1")
    private Long noticeId;

    /** 公告标题 */
    @Schema(description = "公告标题", example = "温馨提醒：系统将于今晚维护")
    private String noticeTitle;

    /** 公告类型（1通知 2公告） */
    @Schema(description = "公告类型（1通知 2公告）", example = "1")
    private String noticeType;

    /** 公告内容 */
    @Schema(description = "公告内容", example = "维护时间 22:00-23:00")
    private String noticeContent;

    /** 公告状态（0正常 1关闭） */
    @Schema(description = "公告状态（0正常 1关闭）", example = "0")
    private String status;

    /** 是否已读 */
    @JsonProperty("isRead")
    @Schema(description = "当前用户是否已读", example = "false")
    private boolean isRead;

    public Long getNoticeId()
    {
        return noticeId;
    }

    public void setNoticeId(Long noticeId)
    {
        this.noticeId = noticeId;
    }

    public void setNoticeTitle(String noticeTitle)
    {
        this.noticeTitle = noticeTitle;
    }

    @Xss(message = "公告标题不能包含脚本字符")
    @NotBlank(message = "公告标题不能为空")
    @Size(min = 0, max = 50, message = "公告标题不能超过50个字符")
    public String getNoticeTitle()
    {
        return noticeTitle;
    }

    public void setNoticeType(String noticeType)
    {
        this.noticeType = noticeType;
    }

    public String getNoticeType()
    {
        return noticeType;
    }

    public void setNoticeContent(String noticeContent)
    {
        this.noticeContent = noticeContent;
    }

    public String getNoticeContent()
    {
        return noticeContent;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public boolean getIsRead()
    {
        return isRead;
    }

    public void setIsRead(boolean isRead)
    {
        this.isRead = isRead;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("noticeId", getNoticeId())
            .append("noticeTitle", getNoticeTitle())
            .append("noticeType", getNoticeType())
            .append("noticeContent", getNoticeContent())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
