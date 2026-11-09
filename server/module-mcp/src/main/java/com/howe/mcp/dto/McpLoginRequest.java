package com.howe.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.howe.common.constant.UserConstants;
import com.howe.common.core.domain.model.LoginBody;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OAuth 独立登录参数，不接受客户端准入类型、用户主键或跳转目标。 */
@Schema(description = "MCP 独立登录参数")
public record McpLoginRequest(
        @NotBlank @Size(min = UserConstants.USERNAME_MIN_LENGTH, max = UserConstants.USERNAME_MAX_LENGTH) String username,
        @NotBlank @Size(min = UserConstants.PASSWORD_MIN_LENGTH, max = UserConstants.PASSWORD_MAX_LENGTH)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @Size(max = 32) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String code,
        @Size(max = 64) String uuid,
        @Size(max = 4096) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String turnstileToken) {

    /** 转换为现有账号认证输入，保留所有原有校验字段。 */
    public LoginBody toLoginBody() {
        LoginBody body = new LoginBody();
        body.setUsername(username);
        body.setPassword(password);
        body.setCode(code);
        body.setUuid(uuid);
        body.setTurnstileToken(turnstileToken);
        return body;
    }

    /** 禁止记录器通过 DTO 的默认输出泄露登录凭据。 */
    @Override
    public String toString() {
        return "McpLoginRequest[凭据已隐藏]";
    }
}
