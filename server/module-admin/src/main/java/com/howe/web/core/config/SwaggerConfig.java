package com.howe.web.core.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
        // 相对 server /api：swagger-ui 按当前页面 origin 解析成「协议://域名/api」。
        // 在 https://admin.wyantao.com/api/swagger-ui 打开时即自动拼出 https://admin.wyantao.com/api，
        // 协议与域名随访问方式自动变化，无需在配置里写死；/api 前缀与前端 VITE_APP_BASE_API / Nginx 反代硬耦合
        openApi.servers(List.of(new Server().url("/api")));
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
