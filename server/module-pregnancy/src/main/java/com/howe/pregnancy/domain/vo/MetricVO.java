package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 指标目录项
 *
 * @author howe
 */
@Data
@Schema(description = "固定指标目录项")
public class MetricVO {

    @Schema(description = "指标编码")
    private String metricCode;

    @Schema(description = "指标名称")
    private String name;

    @Schema(description = "写入形态")
    private String kind;

    @Schema(description = "默认展示顺序")
    private Integer sortOrder;

    @Schema(description = "是否默认展示")
    private Boolean visible;

    @Schema(description = "允许单位")
    private List<String> units;

    @Schema(description = "是否使用次数值")
    private Boolean secondaryValue;

    @Schema(description = "是否允许按日求和")
    private Boolean summable;

    @Schema(description = "字段定义")
    private List<FieldVO> fields;

    @Schema(description = "场景定义")
    private List<SceneVO> scenes;

    @Schema(description = "是否必须选择场景")
    private Boolean sceneRequired;

    /**
     * 字段定义
     */
    @Data
    @Schema(description = "指标字段")
    public static class FieldVO {

        @Schema(description = "字段编码")
        private String code;

        @Schema(description = "字段标签")
        private String label;

        @Schema(description = "字段类型")
        private String type;

        @Schema(description = "数值精度")
        private Integer scale;

        @Schema(description = "选项")
        private List<OptionVO> options;
    }

    /**
     * 选项定义
     */
    @Data
    @Schema(description = "指标选项")
    public static class OptionVO {

        @Schema(description = "选项编码")
        private String code;

        @Schema(description = "选项标签")
        private String label;
    }

    /**
     * 场景定义
     */
    @Data
    @Schema(description = "指标场景")
    public static class SceneVO {

        @Schema(description = "场景编码")
        private String code;

        @Schema(description = "场景标签")
        private String label;
    }
}
