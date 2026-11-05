package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 菜品归属可选家庭
 *
 * <p>用于「调整归属」的家庭下拉。只返回家庭 ID、名称与上级名称，
 * 不返回成员、手机号等隐私字段。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜品归属可选家庭")
public class DishOwnerOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "家庭部门 ID")
    private Long deptId;

    @Schema(description = "家庭名称")
    private String name;

    @Schema(description = "上级名称，用于同名家庭区分；顶级家庭为空")
    private String parentName;
}
