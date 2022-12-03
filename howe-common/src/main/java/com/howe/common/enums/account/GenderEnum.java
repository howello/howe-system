package com.howe.common.enums.account;


/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 9:07 星期二
 * <p>@Version 1.0
 * <p>@Description 性别
 */
public enum GenderEnum {

    /**
     * 男
     */
    MALE("0", "男"),

    /**
     * 女
     */
    FEMALE("1", "女"),

    /**
     * 未知
     */
    UNKNOWN("2", "未知");


    private String code;
    private String desc;

    GenderEnum(String code, String desc) {
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
