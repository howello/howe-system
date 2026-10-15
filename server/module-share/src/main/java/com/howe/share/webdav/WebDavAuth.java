package com.howe.share.webdav;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WebDAV 访问凭据
 *
 * <p>由服务层从 {@code share_source} 解密后构造，避免把明文密码挂在实体上到处传递。</p>
 *
 * @param baseUrl  源根地址
 * @param username 账号
 * @param password 应用密码（明文）
 * @author howe
 */
@Schema(description = "WebDAV 访问凭据")
public record WebDavAuth(String baseUrl, String username, String password) {
}
