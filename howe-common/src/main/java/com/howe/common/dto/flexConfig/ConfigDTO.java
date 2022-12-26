package com.howe.common.dto.flexConfig;

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
 * <p>@Date 2022/12/15 9:23 星期四
 * <p>@Version 1.0
 * <p>@Description 灵活配置表
 */
@ApiModel(value = "灵活配置表")
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "t_flex_config", autoResultMap = true)
public class ConfigDTO extends BaseDTO implements Serializable {
    private static final long serialVersionUID = 3776434955710709421L;
    @TableId(value = "RULE_ID", type = IdType.INPUT)
    @ApiModelProperty(value = "规则id")
    @Schema(description = "规则id")
    private String ruleId;

    @TableField(value = "`ENABLE`")
    @ApiModelProperty(value = "是否可用")
    @Schema(description = "是否可用")
    private String enable;

    @TableField(value = "BIZ_TYPE_ID")
    @ApiModelProperty(value = "业务类型id")
    @Schema(description = "业务类型id")
    private String bizTypeId;

    @TableField(value = "BIZ_TYPE_DESC")
    @ApiModelProperty(value = "业务类型描述")
    @Schema(description = "业务类型描述")
    private String bizTypeDesc;

    @TableField(value = "`DATA`")
    @ApiModelProperty(value = "数据")
    @Schema(description = "数据")
    private String data;

    @TableField(value = "`schema`")
    @ApiModelProperty(value = "schema")
    @Schema(description = "schema")
    private String schema;


    public static final String COL_RULE_ID = "RULE_ID";

    public static final String COL_ENABLE = "ENABLE";

    public static final String COL_BIZ_TYPE_ID = "BIZ_TYPE_ID";

    public static final String COL_BIZ_TYPE_DESC = "BIZ_TYPE_DESC";

    public static final String COL_DATA = "DATA";
    public static final String COL_SCHEMA = "SCHEMA";
}
