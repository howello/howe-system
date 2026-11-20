package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 饮食记录 preg_diet_record
 *
 * <p>按餐次保存，同一天允许多次加餐；饮水合计来自指标记录，不在本表另存日合计。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "饮食记录")
public class PregDietRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "饮食记录ID")
    private Long id;

    @Schema(description = "餐次")
    private String mealType;

    @Schema(description = "发生时刻")
    private OffsetDateTime occurredAt;

    @Schema(description = "业务自然日")
    private LocalDate recordDate;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "能量等补充文字")
    private String extraText;

    @Schema(description = "操作人账号ID")
    private Long actorUserId;

    @Schema(description = "客户端请求ID")
    private String requestId;

    @Schema(description = "请求内容摘要")
    private String requestHash;

    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "删除标记（0存在 2删除）")
    private String delFlag;


    @Schema(description = "图片元数据（详情查询填充）")
    private List<PregFile> files;
}
