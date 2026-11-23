package com.howe.system.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/** 单个应用的一条发版机器人配置。 */
@Data
public class AppReleaseNotifyConfig {

    private Long configId;

    @Size(max = 80, message = "群备注不能超过 80 个字符")
    private String label;

    @Size(max = 128, message = "Webhook Key 不能超过 128 个字符")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String webhookKey;

    @NotNull(message = "请指定是否启用通知")
    private Boolean enabled;
}
