package com.howe.share.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 分享记录对象 share_link
 *
 * <p>{@link #cacheStatus} 描述字节缓存进度，{@link #status} 描述链接生死，两者语义分开。
 * {@link #passwordHash} 只存 BCrypt 哈希，不返回前端；{@link #password} 仅作为入参明文。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分享记录")
public class ShareLink extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分享ID", example = "1")
    private Long linkId;

    @Schema(description = "短码", example = "a1B2c3D4")
    private String code;

    @Schema(description = "源ID", example = "1")
    private Long sourceId;

    @Schema(description = "WebDAV 源文件路径", example = "/文档/季度报表.pdf")
    private String filePath;

    @Schema(description = "文件名", example = "季度报表.pdf")
    private String fileName;

    @Schema(description = "扩展名（小写，不含点）", example = "pdf")
    private String fileExt;

    @Schema(description = "字节数", example = "2411520")
    private Long fileSize;

    @Schema(description = "MIME 类型", example = "application/pdf")
    private String contentType;

    @Schema(description = "存储位置（0本地 1R2）", example = "0")
    private String storageType;

    @Schema(description = "存储地址（本地相对路径或 R2 对象键）")
    private String storageLocation;

    @Schema(description = "访问密码明文（仅入参）")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Schema(description = "访问密码 BCrypt 哈希（不返回前端）")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    @Schema(description = "是否已设置密码", example = "false")
    private Boolean passwordSet;

    @Schema(description = "过期时间，NULL 表示不过期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireAt;

    @Schema(description = "最大查看次数，<=0 表示不限", example = "1")
    private Integer maxViews;

    @Schema(description = "已查看次数", example = "0")
    private Integer viewCount;

    @Schema(description = "阅后即焚（0否 1是）", example = "0")
    private String burnAfterRead;

    @Schema(description = "缓存状态（0待缓存 1缓存中 2就绪 3失败）", example = "2")
    private String cacheStatus;

    @Schema(description = "缓存失败原因")
    private String cacheError;

    @Schema(description = "缓存重试次数", example = "0")
    private Integer cacheRetryCount;

    @Schema(description = "链接状态（0正常 1已失效 2已焚毁）", example = "0")
    private String status;

    @Schema(description = "最近访问时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastViewTime;

    @Schema(description = "源名称（列表展示用，非库字段）")
    private String sourceName;

    @Schema(description = "短链地址（列表展示用，非库字段）")
    private String shortUrl;
}
