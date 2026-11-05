package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * APP 资源包远程链接校验结果
 *
 * <p>校验是质量约束而不是准入凭据：保存接口在需要时（新增 / 换 URL / 重新发布）
 * 会再次校验，前端传来的结果只用于即时反馈。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "APP 资源包远程链接校验结果")
public class AppPackageCheckVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否可达且为有效 wgt")
    private boolean reachable;

    @Schema(description = "文件字节数")
    private Long size;

    @Schema(description = "从地址推断的文件名")
    private String fileName;

    @Schema(description = "校验时间（yyyy-MM-dd HH:mm:ss）")
    private String checkedAt;

    @Schema(description = "失败原因，成功时为空")
    private String reason;
}
