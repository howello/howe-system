package com.howe.meal.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.howe.meal.domain.MealNotification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 点餐通知轮询聚合结果。
 *
 * <p>一次请求同时返回当前账号可读取的通知与厨师待办数量，避免点餐区并行轮询通知和角标。
 * 非厨师不查询待办，因此对应字段为 {@code null} 且不会出现在 JSON 中。</p>
 *
 * @param notifications 本轮读取并已从队列移除的通知
 * @param pendingCount   当前家庭待接单数量
 * @param cookingCount   当前厨师制作中数量
 * @author howe
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "点餐通知轮询聚合结果")
public record MealNotifyOverview(
        @Schema(description = "本轮通知列表") List<MealNotification> notifications,
        @Schema(description = "待接单数量，仅厨师返回") Long pendingCount,
        @Schema(description = "制作中数量，仅厨师返回") Long cookingCount) {
}
