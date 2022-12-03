package com.howe.common.enums.redis;

import java.io.Serializable;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 15:10 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
public enum RedisKeyPrefixEnum implements Serializable {
    /**
     * 全局ID生成
     */
    ID_GENERATOR_KEY("id_generator_key", "全局ID生成"),

    /**
     * 灵活配置前缀
     */
    FLEX_CONFIG("flex_config", "灵活配置前缀"),

    /**
     * 验证码
     */
    VERIFICATION_CODE("verification_code", "验证码"),

    TOKEN_INFO("token_info", "Token信息"),

    END("end", "结尾");


    /**
     * 前缀
     */
    private String prefix;

    /**
     * 描述
     */
    private String desc;

    RedisKeyPrefixEnum(String prefix, String desc) {
        this.prefix = prefix;
        this.desc = desc;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDesc() {
        return desc;
    }
}
