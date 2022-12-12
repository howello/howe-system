package com.howe.common.enums.exception;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 15:23 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public enum MainExceptionEnum implements BaseExceptionEnum {

    /**
     * 开始
     */
    START(3001, "start"),

    AUTH_FAILED(3002, "认证失败，无法访问系统资源"),
    TYPE_NOT_SUPPORTED(3003, "类型不支持"),

    END(3999, "END");

    /**
     * 错误码
     */
    private int code;

    /**
     * 错误消息
     */
    private String message;

    MainExceptionEnum(int code, String message) {
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
