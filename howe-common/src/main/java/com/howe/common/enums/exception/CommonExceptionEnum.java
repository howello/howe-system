package com.howe.common.enums.exception;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:47 星期三
 * <p>@Version 1.0
 * <p>@Description 1开头
 */
public enum CommonExceptionEnum implements BaseExceptionEnum {
    /**
     * 空指针异常
     */
    NULL_POINT_ERROR(1001, "空指针异常"),
    INTERNAL_SERVER_ERROR(1002, "服务器内部错误，请联系管理员处理！"),

    BIZ_TYPE_ID_NULL(1003, "业务类型ID为空！"),

    BIZ_TYPE_DOES_NOT_EXIST(1004, "业务类型不存在！"),

    DATA_NOT_EXIST(1005, "数据不存在！"),

    VERIFICATION_CODE_VERIFICATION_FAILED(1006, "验证码校验失败，请刷新重试！"),

    WRONG_PASSWORD(1007, "密码错误，请输入正确的密码"),

    QUERY_USER_INFO_ERROR(1008, "用户登录过期，请重新登录"),

    QUERY_ACCOUNT_INFO_ERROR(1009, "获取用户账户异常"),
    QUERY_DEPT_ID_INFO_ERROR(1010, "获取部门ID异常"),
    QUERY_USER_ID_INFO_ERROR(1011, "获取用户ID异常"),
    TOKEN_ILLEGAL(1012, "Token已过期，请重新登录"),

    DIC_TYPE_HAS_DISTRIBUTION(1013, "字典类型已分配，不可删除"),
    NUMBER_FORMAT_ERROR(1014, "数据转换异常"),
    END(1999, "END");

    /**
     * 错误码
     */
    private int code;

    /**
     * 错误消息
     */
    private String message;

    CommonExceptionEnum(int code, String message) {
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
