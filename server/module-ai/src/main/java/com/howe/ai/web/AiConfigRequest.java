package com.howe.ai.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiConfigRequest(
        @Schema(description = "配置编码", requiredMode = Schema.RequiredMode.REQUIRED) String key,
        @Schema(description = "名称") String name,
        @Schema(description = "配置 JSON") String configJson,
        @Schema(description = "是否启用") String enabled,
        @Schema(description = "API Key，仅用于新增或替换，不回显") String apiKey) {}
