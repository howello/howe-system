package com.howe.ai.persistence.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 生成图资产 ai_image_asset
 *
 * <p>单独记录每次生成的图片，方便后续清理对象存储与做统计。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "AI 生成图资产")
public class AiImageAsset implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联的调用记录 ID")
    private Long callLogId;

    @Schema(description = "对象存储中的对象键")
    private String objectKey;

    @Schema(description = "永久访问地址")
    private String url;

    @Schema(description = "宽度（像素）")
    private Integer width;

    @Schema(description = "高度（像素）")
    private Integer height;

    @Schema(description = "文件字节数")
    private Long sizeBytes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;
}
