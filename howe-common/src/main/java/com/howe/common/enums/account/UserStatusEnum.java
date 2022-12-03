package com.howe.common.enums.account;


/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 9:07 星期二
 * <p>@Version 1.0
 * <p>@Description 用户账号状态 <p>
 * {@link com.howe.common.dto.role.UserDTO#status}
 */
public enum UserStatusEnum {

    /**
     * 正常
     */
    NORMAL("0", "正常"),

    /**
     * 账号已过期
     */
    ACCOUNT_EXPIRED("1", "账号已过期"),

    /**
     * 凭证已过期
     */
    CREDENTIALS_EXPIRED("2", "凭证已过期"),

    /**
     * 锁定
     */
    LOCKED("3", "锁定"),

    /**
     * 禁用
     */
    BLOCKED("4", "禁用"),

    DELETE("5", "删除");


    private String code;
    private String desc;

    UserStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
