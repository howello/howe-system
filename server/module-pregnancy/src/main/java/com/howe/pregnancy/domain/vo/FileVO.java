package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 私有原图展示对象
 *
 * <p>只暴露文件 ID 与元数据，不返回存储键、磁盘路径或公开地址。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "私有原图元数据")
public class FileVO {

    @Schema(description = "文件ID")
    private String id;

    @Schema(description = "状态（TEMP/BOUND/ORPHAN/DELETING）")
    private String state;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "字节数")
    private String sizeBytes;

    @Schema(description = "SHA-256")
    private String sha256;

    @Schema(description = "图片宽度")
    private Integer width;

    @Schema(description = "图片高度")
    private Integer height;

    @Schema(description = "所属业务类型")
    private String ownerType;

    @Schema(description = "所属业务ID")
    private String ownerId;

    @Schema(description = "业务内顺序")
    private Integer sortOrder;

    @Schema(description = "鉴权内容地址")
    private String contentUrl;
}
