package com.howe.share.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * WebDAV 源配置对象 share_source
 *
 * <p>密码只写不读：入参用 {@link #password} 传入明文，服务端加密后存
 * {@link #passwordCipher}；查询接口不返回 {@code passwordCipher}，只返回
 * {@link #passwordSet} 标识是否已配置密码。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "WebDAV 源配置")
public class ShareSource extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "源ID", example = "1")
    private Long sourceId;

    @Schema(description = "源名称", example = "我的坚果云")
    @NotBlank(message = "源名称不能为空")
    @Size(max = 100, message = "源名称长度不能超过100个字符")
    private String name;

    @Schema(description = "WebDAV 根地址", example = "https://dav.jianguoyun.com/dav/")
    @NotBlank(message = "WebDAV 根地址不能为空")
    @Size(max = 500, message = "WebDAV 根地址长度不能超过500个字符")
    private String baseUrl;

    @Schema(description = "账号", example = "user@example.com")
    @NotBlank(message = "账号不能为空")
    @Size(max = 200, message = "账号长度不能超过200个字符")
    private String username;

    @Schema(description = "应用密码（明文，仅入参；服务端加密后入库，查询不返回）")
    @Size(max = 500, message = "应用密码长度不能超过500个字符")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Schema(description = "应用密码密文（仅入库与内部使用，查询不返回）")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordCipher;

    @Schema(description = "是否已配置密码", example = "true")
    private Boolean passwordSet;

    @Schema(description = "默认根目录", example = "/")
    @Size(max = 500, message = "根目录长度不能超过500个字符")
    private String rootPath;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;
}
