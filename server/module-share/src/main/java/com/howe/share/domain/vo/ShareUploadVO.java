package com.howe.share.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 本地上传结果
 *
 * <p>{@code uploadToken} 只返回这一次，后续创建分享时带上它完成认领。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "本地上传结果")
public class ShareUploadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "上传令牌，仅返回一次")
    private String uploadToken;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "字节数")
    private Long fileSize;

    @Schema(description = "小写扩展名")
    private String fileExt;

    @Schema(description = "过期时间")
    private Date expireAt;
}
