package com.howe.ai.persistence.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页 AI 图片资产概况
 *
 * @param totalCount    已生成图片总数
 * @param totalBytes    占用空间合计（字节）
 * @param latestTime    最近生成时间（yyyy-MM-dd HH:mm）；从未生成时为 null
 * @author howe
 */
@Schema(description = "首页 AI 图片资产概况")
public record AiImageAssetOverview(
        @Schema(description = "已生成图片总数") long totalCount,
        @Schema(description = "占用空间合计（字节）") long totalBytes,
        @Schema(description = "最近生成时间") String latestTime) {
}
