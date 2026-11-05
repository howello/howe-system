package com.howe.share.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 分享远程链接校验结果
 *
 * <p>仅供表单即时反馈；创建分享时服务端会再次校验，不信任前端结论。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "分享远程链接校验结果")
public class ShareRemoteCheckVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否可达")
    private boolean reachable;

    @Schema(description = "文件字节数")
    private Long fileSize;

    @Schema(description = "从地址推断的文件名")
    private String fileName;

    @Schema(description = "校验时间")
    private String checkedAt;

    @Schema(description = "失败原因，成功时为空")
    private String reason;
}
