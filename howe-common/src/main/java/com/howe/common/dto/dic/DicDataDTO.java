package com.howe.common.dto.dic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.howe.common.dto.BaseDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/5 17:06 星期一
 * <p>@Version 1.0
 * <p>@Description 字典数据表
 */
@ApiModel(value = "字典数据表")
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "sys_dict_data")
public class DicDataDTO extends BaseDTO implements Serializable {
    /**
     * 字典编码
     */
    @TableId(value = "dict_code", type = IdType.INPUT)
    @ApiModelProperty(value = "字典编码")
    @Schema(description = "字典编码")
    @NotNull(message = "字典编码不能为空")
    private Long dictCode;

    /**
     * 字典排序
     */
    @TableField(value = "dict_sort")
    @ApiModelProperty(value = "字典排序")
    @Schema(description = "字典排序")
    private Integer dictSort;

    /**
     * 字典标签
     */
    @TableField(value = "dict_label")
    @ApiModelProperty(value = "字典标签")
    @Schema(description = "字典标签")
    private String dictLabel;

    /**
     * 字典键值
     */
    @TableField(value = "dict_value")
    @ApiModelProperty(value = "字典键值")
    @Schema(description = "字典键值")
    private String dictValue;

    /**
     * 字典类型
     */
    @TableField(value = "dict_type")
    @ApiModelProperty(value = "字典类型")
    @Schema(description = "字典类型")
    private String dictType;

    /**
     * 样式属性（其他样式扩展）
     */
    @TableField(value = "css_class")
    @ApiModelProperty(value = "样式属性（其他样式扩展）")
    @Schema(description = "样式属性（其他样式扩展）")
    private String cssClass;

    /**
     * 表格回显样式
     */
    @TableField(value = "list_class")
    @ApiModelProperty(value = "表格回显样式")
    @Schema(description = "表格回显样式")
    private String listClass;

    /**
     * 是否默认（Y是 N否）
     */
    @TableField(value = "is_default")
    @ApiModelProperty(value = "是否默认（Y是 N否）")
    @Schema(description = "是否默认（Y是 N否）")
    private String isDefault;

    /**
     * 状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态（0正常 1停用）")
    @Schema(description = "状态（0正常 1停用）")
    private String status;

    private static final long serialVersionUID = 1L;

    public static final String COL_DICT_CODE = "dict_code";

    public static final String COL_DICT_SORT = "dict_sort";

    public static final String COL_DICT_LABEL = "dict_label";

    public static final String COL_DICT_VALUE = "dict_value";

    public static final String COL_DICT_TYPE = "dict_type";

    public static final String COL_CSS_CLASS = "css_class";

    public static final String COL_LIST_CLASS = "list_class";

    public static final String COL_IS_DEFAULT = "is_default";

    public static final String COL_STATUS = "status";
}
