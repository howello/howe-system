package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务摘要
 *
 * <p>只返回用户可见字段：不包含租约令牌、内部错误栈或凭证。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "孕期任务摘要")
public class TaskVO {

    @Schema(description = "任务ID")
    private String id;

    @Schema(description = "类型（REMINDER/ITEM_CONTENT/EXPORT）")
    private String kind;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "当前阶段（TEXT/IMAGE/SNAPSHOT/PACKAGE/SEND）")
    private String stage;

    @Schema(description = "已尝试次数")
    private Integer attempt;

    @Schema(description = "下次重试时刻")
    private String nextRetryAt;

    @Schema(description = "结果引用（导出可用）")
    private String resultRef;

    @Schema(description = "结果到期时刻")
    private String expiresAt;

    @Schema(description = "AI 文本")
    private String text;

    @Schema(description = "AI 配图地址")
    private String imageUrl;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "用户可见错误提示")
    private String message;
}
