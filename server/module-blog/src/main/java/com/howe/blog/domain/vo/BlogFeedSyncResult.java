package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RSS 同步结果
 *
 * @param total    参与本次同步的订阅源总数
 * @param success  同步成功的源数
 * @param failed   同步失败的源数
 * @param newItems 本次新增的条目数
 * @author howe
 */
@Schema(description = "RSS同步结果")
public record BlogFeedSyncResult(
        @Schema(description = "参与同步的订阅源总数", example = "5") int total,
        @Schema(description = "成功的源数", example = "4") int success,
        @Schema(description = "失败的源数", example = "1") int failed,
        @Schema(description = "本次新增条目数", example = "12") int newItems) {
}
