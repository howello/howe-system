package com.howe.common.enums;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/5 17:24 星期一
 * <p>@Version 1.0
 * <p>@Description
 */
public enum StatusEnum {
    /**
     * 状态
     */
    NORMAL(0, "正常"),
    DELETE(1, "删除");

    private int code;
    private String desc;

    StatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getCodeStr() {
        return String.valueOf(code);
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
