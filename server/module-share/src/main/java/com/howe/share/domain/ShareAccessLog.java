package com.howe.share.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 分享访问日志对象 share_access_log
 *
 * @author howe
 */
@Data
@Schema(description = "分享访问日志")
public class ShareAccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID", example = "1")
    private Long logId;

    @Schema(description = "分享ID", example = "1")
    private Long linkId;

    @Schema(description = "短码", example = "a1B2c3D4")
    private String code;

    @Schema(description = "访问IP", example = "127.0.0.1")
    private String ip;

    @Schema(description = "User-Agent")
    private String userAgent;

    @Schema(description = "动作（detail/verify/open/file/download）", example = "open")
    private String action;

    @Schema(description = "结果（success/fail）", example = "success")
    private String result;

    @Schema(description = "说明")
    private String message;

    @Schema(description = "访问时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
