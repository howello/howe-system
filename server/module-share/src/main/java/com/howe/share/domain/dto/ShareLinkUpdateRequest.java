package com.howe.share.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

/**
 * 修改分享请求
 *
 * <p>只允许修改约束，不修改源文件本身。</p>
 *
 * @param linkId        分享ID
 * @param password      访问密码：{@code null} 表示不改动，空串表示清除密码，非空表示设为该密码
 * @param expireAt      过期时间：{@code null} 表示改为不过期
 * @param maxViews      最大查看次数，&lt;=0 表示不限
 * @param burnAfterRead 阅后即焚（0否 1是）
 * @param status        链接状态（0正常 1已失效），用于手动失效 / 恢复
 * @param remark        备注
 * @author howe
 */
@Schema(description = "修改分享请求")
public record ShareLinkUpdateRequest(
        @Schema(description = "分享ID", example = "1")
        @NotNull(message = "分享ID不能为空")
        Long linkId,

        @Schema(description = "访问密码：null 不改动，空串清除，非空设为该密码")
        String password,

        @Schema(description = "过期时间，null 表示改为不过期")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        Date expireAt,

        @Schema(description = "最大查看次数，<=0 表示不限", example = "1")
        Integer maxViews,

        @Schema(description = "阅后即焚（0否 1是）", example = "0")
        String burnAfterRead,

        @Schema(description = "链接状态（0正常 1已失效）", example = "0")
        String status,

        @Schema(description = "备注")
        String remark) {
}
