package com.howe.share.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 本地上传暂存 share_upload
 *
 * <p>上传成功后字节先落到暂存目录，记录在这里；创建分享时按令牌原子认领，
 * 缓存成功后再删除暂存。这样做的原因：不能把上传直接落到最终存储，
 * 否则未提交的上传会变成一堆无人认领的公开对象。</p>
 *
 * <p>令牌明文只返回一次，库里只存哈希；{@code owner_user_id} 保证别人的令牌用不了。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "本地上传暂存")
public class ShareUpload extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "上传ID")
    private Long uploadId;

    @Schema(description = "上传令牌哈希（库里不存明文）")
    private String tokenHash;

    @Schema(description = "上传者用户ID")
    private Long ownerUserId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "字节数")
    private Long fileSize;

    @Schema(description = "MIME 类型")
    private String contentType;

    @Schema(description = "小写扩展名")
    private String fileExt;

    @Schema(description = "暂存相对路径")
    private String stagingLocation;

    @Schema(description = "状态：TEMP / CLAIMED / BOUND / EXPIRED")
    private String state;

    @Schema(description = "绑定的分享ID")
    private Long linkId;

    @Schema(description = "过期时间")
    private Date expireAt;
}
