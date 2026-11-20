package com.howe.pregnancy.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 私有原图 preg_file
 *
 * <p>字节保存在独立私有目录，数据库只记元数据与不可变存储键；接口一律使用文件 ID，
 * 不返回磁盘路径或公开地址。</p>
 *
 * <p>状态流转：{@code TEMP → BOUND → ORPHAN → DELETING}。解绑后进入 ORPHAN，
 * 保留期后再领取为 DELETING 并删除字节；DELETING 不允许重新绑定。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "孕期私有原图")
public class PregFile {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文件ID")
    private Long id;

    @Schema(description = "上传人账号ID")
    private Long uploaderUserId;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "私有存储键")
    private String storageKey;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "字节数")
    private Long sizeBytes;

    @Schema(description = "SHA-256")
    private String sha256;

    @Schema(description = "图片宽度")
    private Integer width;

    @Schema(description = "图片高度")
    private Integer height;

    @Schema(description = "状态（TEMP/BOUND/ORPHAN/DELETING）")
    private String state;

    @Schema(description = "所属业务类型（REPORT/DIET）")
    private String ownerType;

    @Schema(description = "所属业务ID")
    private Long ownerId;

    @Schema(description = "业务内展示顺序")
    private Integer sortOrder;

    @Schema(description = "解绑时刻")
    private OffsetDateTime detachedAt;

    @Schema(description = "可回收时刻")
    private OffsetDateTime reclaimAt;

    @Schema(description = "上传请求ID")
    private String requestId;
}
