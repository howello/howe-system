package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 饮食记录展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "饮食记录")
public class DietVO {

    @Schema(description = "记录ID")
    private String id;

    @Schema(description = "餐次")
    private String mealType;

    @Schema(description = "发生时刻")
    private String occurredAt;

    @Schema(description = "业务自然日")
    private String recordDate;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "能量等补充文字")
    private String extraText;

    @Schema(description = "操作人账号ID")
    private String actorUserId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "图片")
    private List<FileVO> files;
}
