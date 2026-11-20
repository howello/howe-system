package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 后台任务 preg_task
 *
 * <p>提醒、物品 AI 内容与导出共用一张任务表，以 {@code kind} 区分，避免不同入口
 * 的去重键冲突。领取、续租与提交都必须核对租约令牌；回写还要核对业务版本。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "孕期后台任务")
public class PregTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "类型（REMINDER/ITEM_CONTENT/EXPORT）")
    private String kind;

    @Schema(description = "业务ID")
    private Long businessId;

    @Schema(description = "业务版本")
    private Integer businessVersion;

    @Schema(description = "请求人账号ID")
    private Long requesterUserId;

    @Schema(description = "去重键")
    private String dedupKey;

    @Schema(description = "受控入参 JSON")
    private String payloadJson;

    @Schema(description = "阶段结果 JSON")
    private String stageJson;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "已尝试次数")
    private Integer attempt;

    @Schema(description = "尝试上限")
    private Integer maxAttempts;

    @Schema(description = "下次执行时刻")
    private OffsetDateTime nextRunAt;

    @Schema(description = "租约令牌")
    private String leaseToken;

    @Schema(description = "租约到期时刻")
    private OffsetDateTime leaseUntil;

    @Schema(description = "最近错误码")
    private String errorCode;

    @Schema(description = "结果引用")
    private String resultRef;

    @Schema(description = "结果到期时刻")
    private OffsetDateTime expiresAt;

    @Schema(description = "提醒实际发送时刻")
    private OffsetDateTime sentAt;
}
