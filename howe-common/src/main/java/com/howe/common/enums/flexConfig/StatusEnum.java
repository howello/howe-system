package com.howe.common.enums.flexConfig;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/16 10:03 星期五
 * <p>@Version 1.0
 * <p>@Description
 */
public enum StatusEnum {
    /**
     * 状态
     */
    NORMAL("1", "启用"),

    DELETE("0", "停用");

    private String code;
    private String desc;

    StatusEnum(String code, String desc) {
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
