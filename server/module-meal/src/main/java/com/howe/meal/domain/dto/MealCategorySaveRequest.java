package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增/修改请求
 *
 * <p>服务端不信任本对象里的归属与审计字段：{@code deptId} 仅平台管理员可选，
 * 家庭账号强制使用登录家庭；{@code createBy}/{@code updateBy}/{@code delFlag}
 * 一律由服务端填充。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "点餐分类保存请求")
public class MealCategorySaveRequest {

    @Schema(description = "所属家庭ID；仅平台管理员可指定，家庭账号忽略")
    private Long deptId;

    @Schema(description = "分类名称", example = "岷县")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称长度不能超过64个字符")
    private String name;

    @Schema(description = "分类说明（一句话定位）", example = "岷县地方特色菜")
    @NotBlank(message = "分类说明不能为空")
    @Size(max = 255, message = "分类说明长度不能超过255个字符")
    private String description;

    @Schema(description = "分类依据（放什么、不放什么、示例）")
    @NotBlank(message = "分类依据不能为空")
    @Size(max = 1000, message = "分类依据长度不能超过1000个字符")
    private String basis;

    @Schema(description = "分类图标（emoji）", example = "🏔️")
    @Size(max = 500, message = "分类图标长度不能超过500个字符")
    private String icon;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "状态（0正常 1停用）", example = "0")
    private String status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
