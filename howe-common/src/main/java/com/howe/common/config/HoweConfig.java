package com.howe.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/3 16:50 星期四
 * <p>@Version 1.0
 * <p>@Description 读取自定义配置
 */
@Data
@ConfigurationProperties(prefix = "howe")
@Component
public class HoweConfig {

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目版本
     */
    private String version;

    /**
     * Mybatis 配置项
     */
    private Mybatis mybatis;

    /**
     * Token 配置项
     */
    private Token token;

    /**
     * 权限配置项
     */
    private Premission premission;

    /**
     * 验证码
     */
    private Captcha captcha;

    @Data
    public static class Token {
        /**
         * TOken过期时间
         */
        private Integer expireTime;

        /**
         * Token加密密钥
         */
        private String secret;

        /**
         * 令牌自定义标识
         */
        private String header;

        /**
         * 刷新token时间，单位分
         */
        private Integer refreshTokenTime;
    }

    @Data
    public static class Mybatis {
        /**
         * 单页最大数据
         */
        private Long maxLimit;
    }

    @Data
    public static class Premission {
        /**
         * 允许所有
         */
        private List<String> permitAllList;

        /**
         * 允许匿名访问
         */
        private List<String> anonymousList;
    }

    @Data
    public static class Captcha {
        /**
         * 生成验证码的字符串
         */
        private String baseStr;

        /**
         * 随机长度
         */
        private Integer length;

        /**
         * 验证码过期时间
         * 单位 秒
         */
        private Integer expireTime;
    }
}
