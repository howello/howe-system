package com.howe.share.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 进入就绪态的结果
 *
 * @param burned         本次是否触发了焚毁（逻辑焚毁）
 * @param remainingViews 剩余查看次数，{@code null} 表示不限
 * @author howe
 */
@Schema(description = "进入就绪态结果")
public record ShareOpenResult(
        @Schema(description = "本次是否触发焚毁", example = "false")
        boolean burned,

        @Schema(description = "剩余查看次数，null 表示不限")
        Integer remainingViews) {
}
