package com.howe.common.dto.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.howe.common.dto.BaseDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/28 17:04 星期一
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Data
@TableName(value = "sys_user")
@ApiModel("用户信息DTO")
public class UserDTO extends BaseDTO {

    private static final long serialVersionUID = 1667587227999096835L;

    /**
     * 用户ID
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    @ApiModelProperty("用户ID")
    private String userId;

    /**
     * 用户账号
     */
    @TableField(value = "user_name")
    @ApiModelProperty("用户账号")
    private String userName;

    /**
     * 用户昵称
     */
    @TableField(value = "nick_name")
    @ApiModelProperty("用户昵称")
    private String nickName;

    /**
     * 用户邮箱
     */
    @TableField(value = "email")
    @ApiModelProperty("用户邮箱")
    private String email;

    /**
     * 手机号码
     */
    @TableField(value = "phone_number")
    @ApiModelProperty("手机号码")
    private String phoneNumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    @TableField(value = "sex")
    @ApiModelProperty("用户性别")
    private String sex;

    /**
     * 头像地址
     */
    @TableField(value = "avatar")
    @ApiModelProperty("头像地址")
    private String avatar;

    /**
     * 密码
     */
    @TableField(value = "`password`")
    @ApiModelProperty("密码")
    private String password;

    /**
     * 帐号状态
     * {@link com.howe.common.enums.account.UserStatusEnum}
     */
    @TableField(value = "`status`")
    @ApiModelProperty("帐号状态")
    private String status;


    /**
     * 最后登录IP
     */
    @TableField(value = "login_ip")
    @ApiModelProperty("最后登录IP")
    private String loginIp;

    /**
     * 最后登录时间
     */
    @TableField(value = "login_date")
    @ApiModelProperty("最后登录时间")
    private LocalDateTime loginDate;

    public static final String COL_USER_ID = "user_id";

    public static final String COL_DEPT_ID = "dept_id";

    public static final String COL_USER_NAME = "user_name";

    public static final String COL_NICK_NAME = "nick_name";

    public static final String COL_USER_TYPE = "user_type";

    public static final String COL_EMAIL = "email";

    public static final String COL_PHONE_NUMBER = "phone_number";

    public static final String COL_SEX = "sex";

    public static final String COL_AVATAR = "avatar";

    public static final String COL_PASSWORD = "password";

    public static final String COL_STATUS = "status";

    public static final String COL_DEL_FLAG = "del_flag";

    public static final String COL_LOGIN_IP = "login_ip";

    public static final String COL_LOGIN_DATE = "login_date";

    public Boolean isAdmin() {
        return StringUtils.isNotBlank(this.userId) && "1".equals(this.userId);
    }
}
