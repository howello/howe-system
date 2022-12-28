package com.howe.common.dto.notice;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.howe.common.dto.BaseDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/28 10:22 星期三
 * <p>@Version 1.0
 * <p>@Description 通知公告表
 */
@ApiModel(value = "通知公告表")
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "t_notice")
public class NoticeDTO extends BaseDTO implements Serializable {
    /**
     * 公告ID
     */
    @TableId(value = "notice_id", type = IdType.INPUT)
    @ApiModelProperty(value = "公告ID")
    @Schema(description = "公告ID")
    private String noticeId;

    /**
     * 公告标题
     */
    @TableField(value = "notice_title")
    @ApiModelProperty(value = "公告标题")
    @Schema(description = "公告标题")
    private String noticeTitle;

    /**
     * 公告类型（1通知 2公告）
     */
    @TableField(value = "notice_type")
    @ApiModelProperty(value = "公告类型（1通知 2公告）")
    @Schema(description = "公告类型（1通知 2公告）")
    private String noticeType;

    /**
     * 公告内容
     */
    @TableField(value = "notice_content")
    @ApiModelProperty(value = "公告内容")
    @Schema(description = "公告内容")
    private String noticeContent;

    /**
     * 公告状态（0正常 1关闭）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "公告状态（0正常 1关闭）")
    @Schema(description = "公告状态（0正常 1关闭）")
    private String status;

    private static final long serialVersionUID = 1L;

    public static final String COL_NOTICE_ID = "notice_id";

    public static final String COL_NOTICE_TITLE = "notice_title";

    public static final String COL_NOTICE_TYPE = "notice_type";

    public static final String COL_NOTICE_CONTENT = "notice_content";

    public static final String COL_STATUS = "status";
}
