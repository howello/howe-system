package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 产检编辑请求
 *
 * <p>PATCH 语义：未出现的字段保持原值；显式传列表才原子替换对应详情。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "产检编辑请求")
public class CheckupUpdateBody {

    @Schema(description = "版本号，必填")
    private Integer version;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "建议窗口起始孕日")
    private Integer suggestStartDay;

    @Schema(description = "建议窗口结束孕日")
    private Integer suggestEndDay;

    @Schema(description = "手填建议日期，空字符串表示清除")
    private String suggestedDate;

    @Schema(description = "医院")
    private String hospital;

    @Schema(description = "地点")
    private String location;

    @Schema(description = "本次重点说明")
    private String focus;

    @Schema(description = "项目列表；省略不改")
    private List<CheckupSaveBody.DetailItem> items;

    @Schema(description = "准备事项列表；省略不改")
    private List<CheckupSaveBody.DetailItem> preparations;
}
