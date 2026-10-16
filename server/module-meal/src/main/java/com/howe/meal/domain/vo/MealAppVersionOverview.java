package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页点餐-App 版本概况
 *
 * @param latestVersionName 当前已发布的最新版本名；无已发布版本时为 null
 * @param latestPublishTime 最新版本发布时间（yyyy-MM-dd HH:mm）；无已发布版本时为 null
 * @param forceUpdate       最新版本是否强制更新
 * @param totalCount        版本记录总数（未删除）
 * @author howe
 */
@Schema(description = "首页点餐-App 版本概况")
public record MealAppVersionOverview(
        @Schema(description = "当前已发布的最新版本名；无已发布版本时为 null") String latestVersionName,
        @Schema(description = "最新版本发布时间；无已发布版本时为 null") String latestPublishTime,
        @Schema(description = "最新版本是否强制更新") boolean forceUpdate,
        @Schema(description = "版本记录总数") long totalCount) {
}
