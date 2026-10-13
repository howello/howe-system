package com.howe.common.core.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 家庭注册对象
 *
 * <p>手机端点餐端通过「家庭代码」加入已有家庭。家庭代码就是目标家庭的部门ID
 * （{@code sys_dept.dept_id}）。本对象只用于新增的独立家庭注册接口，
 * 不影响既有匿名注册 {@code /register}。</p>
 *
 * @author howe
 */
@Schema(description = "家庭注册对象，在登录对象基础上增加家庭代码、昵称与手机号")
public class FamilyRegisterBody extends LoginBody
{
    /**
     * 家庭代码（目标家庭的部门ID）
     */
    @Schema(description = "家庭代码，即目标家庭的部门ID", example = "110")
    private String familyCode;

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称", example = "小明")
    private String nickName;

    /**
     * 手机号码
     */
    @Schema(description = "手机号码，可选", example = "13800000000")
    private String phonenumber;

    public String getFamilyCode()
    {
        return familyCode;
    }

    public void setFamilyCode(String familyCode)
    {
        this.familyCode = familyCode;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getPhonenumber()
    {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber)
    {
        this.phonenumber = phonenumber;
    }
}
