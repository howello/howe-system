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

import java.io.Serializable;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/7 9:41 星期三
 * <p>@Version 1.0
 * <p>@Description 字典类型表
 */
@ApiModel(value = "字典类型表")
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "sys_dict_type")
public class DictTypeDTO extends BaseDTO implements Serializable {
    /**
     * 字典主键
     */
    @TableId(value = "dict_id", type = IdType.INPUT)
    @ApiModelProperty(value = "字典主键")
    @Schema(description = "字典主键")
    private Long dictId;

    /**
     * 字典名称
     */
    @TableField(value = "dict_name")
    @ApiModelProperty(value = "字典名称")
    @Schema(description = "字典名称")
    private String dictName;

    /**
     * 字典类型
     */
    @TableField(value = "dict_type")
    @ApiModelProperty(value = "字典类型")
    @Schema(description = "字典类型")
    private String dictType;

    /**
     * 状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态（0正常 1停用）")
    @Schema(description = "状态（0正常 1停用）")
    private String status;

    private static final long serialVersionUID = 1L;

    public static final String COL_DICT_ID = "dict_id";

    public static final String COL_DICT_NAME = "dict_name";

    public static final String COL_DICT_TYPE = "dict_type";

    public static final String COL_STATUS = "status";
}
