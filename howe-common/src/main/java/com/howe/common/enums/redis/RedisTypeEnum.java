package com.howe.common.enums.redis;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/12 16:09 星期一
 * <p>@Version 1.0
 * <p>@Description redis的缓存类型
 */
public enum RedisTypeEnum {
    /**
     * redis的缓存类型
     */
    STRING(0, "字符串类型"),
    LIST(1, "list类型"),

    SET(2, "set类型"),

    OBJECT(3, "自定义对象类型");

    RedisTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private int code;

    private String desc;

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
