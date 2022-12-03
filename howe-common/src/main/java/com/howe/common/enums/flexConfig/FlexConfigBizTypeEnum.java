package com.howe.common.enums.flexConfig;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/9 10:16 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
public enum FlexConfigBizTypeEnum {

    /**
     * 例子
     */
    TEXT("text", "text"),

    LOGIN_CONFIG("login_config", "用户登录的配置");


    private String code;
    private String desc;

    FlexConfigBizTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static FlexConfigBizTypeEnum getTypeById(String id) {
        for (FlexConfigBizTypeEnum type : FlexConfigBizTypeEnum.values()) {
            if (type.getCode().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
