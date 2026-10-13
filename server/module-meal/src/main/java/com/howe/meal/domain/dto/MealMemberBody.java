package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手机端家庭成员新增/修改请求
 *
 * <p>家庭管理员在点餐端「家庭与成员」页维护本家庭成员。归属家庭由服务端按
 * 当前登录用户确定，不接受前端传入的 deptId。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "家庭成员新增/修改请求")
public class MealMemberBody {

    @Schema(description = "用户ID，修改时必填")
    private Long userId;

    @Schema(description = "登录账号，新增时必填", example = "xiaoming")
    private String userName;

    @Schema(description = "昵称", example = "小明")
    private String nickName;

    @Schema(description = "初始密码，新增时必填")
    private String password;

    @Schema(description = "手机号码，可选", example = "13800000000")
    private String phonenumber;

    @Schema(description = "家庭角色ID列表（点餐员/厨师/家庭管理员），为空时默认点餐员")
    private Long[] roleIds;
}
