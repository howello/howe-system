package com.howe.meal.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 点餐-企业微信通知配置 meal_wecom_config
 *
 * <p>每个家庭一行（{@code dept_id} 唯一）。只保存群机器人 webhook 的 key，
 * 完整请求地址由后端固定拼接官方域名，避免用户填入任意地址造成 SSRF。
 * key 是凭证，接口返回时一律掩码。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "点餐-企业微信通知配置")
public class MealWeComConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 启用 */
    public static final String ENABLED_YES = "1";
    /** 停用 */
    public static final String ENABLED_NO = "0";

    @Schema(description = "配置ID", example = "1")
    private Long configId;

    @Schema(description = "所属家庭ID", example = "100")
    private Long deptId;

    @Schema(description = "家庭名称（查询结果附带，不落库）", example = "王家")
    private String deptName;

    @Schema(description = "企业微信群机器人 key（列表与详情返回掩码）", example = "****abcd")
    @Size(max = 128, message = "Webhook Key 长度不能超过128个字符")
    private String webhookKey;

    @Schema(description = "家庭级总开关（0停用 1启用）", example = "1")
    @NotBlank(message = "启用状态不能为空")
    private String enabled;

    @Schema(description = "订单通知开关（0停用 1启用）", example = "1")
    @NotBlank(message = "订单通知开关不能为空")
    private String notifyOrder;

    @Schema(description = "AI 通知开关（0停用 1启用）", example = "1")
    @NotBlank(message = "AI 通知开关不能为空")
    private String notifyAi;

    @Schema(description = "删除标记（0存在 2删除）", example = "0")
    private String delFlag;
}
