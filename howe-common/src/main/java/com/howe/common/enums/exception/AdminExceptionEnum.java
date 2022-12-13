package com.howe.common.enums.exception;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:47 星期三
 * <p>@Version 1.0
 * <p>@Description 2开头
 */
public enum AdminExceptionEnum implements BaseExceptionEnum {

    /**
     * 密码错误
     */
    PASSWORD_ERROR(2001, "密码错误"),

    USER_DELETED(2002, "用户已删除，不存在！"),
    ACCOUNT_EXPIRED(2003, "账号已过期"),
    CREDENTIALS_EXPIRED(2004, "凭证已过期"),
    LOCKED(2005, "账号已锁定"),
    BLOCKED(2006, "账号已禁用"),
    NOT_EXIST(2007, "账号不存在"),
    DISABLE_REGISTER(2008, "当前系统没有开启注册功能!"),
    USER_EXISTS(2009, "注册用户已存在!"),
    THERE_ARE_SUBMENUS(2010, "存在子菜单，不可删除!"),


    END(2999, "END");

    /**
     * 错误码
     */
    private int code;

    /**
     * 错误消息
     */
    private String message;

    AdminExceptionEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
