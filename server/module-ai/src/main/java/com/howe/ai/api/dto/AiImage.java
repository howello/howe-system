package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成图
 *
 * <p>{@code url} 一定是转存后的永久地址，不是厂商的临时地址。</p>
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "生成图")
public class AiImage {
    @Schema(description = "永久访问地址")
    private String url;

    @Schema(description = "对象存储中的对象键")
    private String key;

    @Schema(description = "宽度（像素）")
    private Integer width;

    @Schema(description = "高度（像素）")
    private Integer height;

    @Builder.Default
    @Schema(description = "转存状态：READY 已就绪，PENDING_TRANSFER 正在补偿转存")
    private String transferStatus = "READY";

    @Schema(description = "待补偿转存任务 ID，仅待转存时返回")
    private String transferTaskId;
}
