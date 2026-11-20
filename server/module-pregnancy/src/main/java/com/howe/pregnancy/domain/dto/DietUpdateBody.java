package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 饮食编辑请求
 *
 * <p>PATCH 语义：未出现的字段保持原值；显式传 {@code fileIds} 才原子替换照片。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "饮食编辑请求")
public class DietUpdateBody {

    @Schema(description = "版本号，必填")
    private Integer version;

    @Schema(description = "餐次")
    private String mealType;

    @Schema(description = "发生时刻")
    private String occurredAt;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "能量等补充文字")
    private String extraText;

    @Schema(description = "文件ID列表；省略不改")
    private List<String> fileIds;
}
