package com.howe.web.core.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.howe.common.config.YmlConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Swagger2的接口配置
 *
 * @author howe
 */
@Configuration
public class SwaggerConfig {
    /** 系统基础配置 */
    @Autowired
    private YmlConfig ymlConfig;

    /** 绑定地址与端口（来自 .env，经 compose env_file 注入），用于拼 swagger Try it out 的请求基址 */
    @Value("${BIND_HOST:127.0.0.1}")
    private String bindHost;

    @Value("${APP_PORT:9527}")
    private String appPort;

    /**
     * 自定义的 OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenApi() {
        OpenAPI openApi = new OpenAPI().components(new Components()
                        // 设置认证的请求头
                        .addSecuritySchemes("apikey", securityScheme()))
                .addSecurityItem(new SecurityRequirement().addList("apikey"))
                .info(getApiInfo());
        // 用 BIND_HOST:APP_PORT 拼 server 基址（如 http://127.0.0.1:9527），Try it out 直连后端端口、
        // 不经 nginx 反代，所以协议是 http 且不带 /api。生产要外部可达就把 BIND_HOST 设成可达地址
        if (bindHost != null && !bindHost.isBlank()) {
            openApi.servers(List.of(new Server().url("http://" + bindHost + ":" + appPort)));
        }
        return openApi;
    }

    @Bean
    public SecurityScheme securityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .name("Authorization")
                .in(SecurityScheme.In.HEADER)
                .scheme("Bearer");
    }

    /**
     * 添加摘要信息
     */
    public Info getApiInfo() {
        return new Info()
                // 设置标题
                .title("标题：后台管理系统_接口文档")
                // 描述
                .description("描述：用于管理所有后台，包括admin、blog、wx、小程序等等")
                // 作者信息
                .contact(new Contact().name(ymlConfig.getName()))
                // 版本
                .version("版本号:" + ymlConfig.getVersion());
    }
}
