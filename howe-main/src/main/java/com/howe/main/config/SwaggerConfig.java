package com.howe.main.config;

import com.howe.common.config.HoweConfig;
import io.swagger.models.auth.In;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.web.*;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/3 16:44 星期四
 * <p>@Version 1.0
 * <p>@Description
 */
@Configuration
@EnableOpenApi
@EnableConfigurationProperties(HoweConfig.class)
public class SwaggerConfig {

    @Autowired
    private HoweConfig howeConfig;

    @Value("${swagger.enable}")
    private boolean enable;

    @Value("${swagger.pathMapping}")
    private String pathMapping;

    /**
     * 所有接口
     *
     * @return
     */
    @Bean
    public Docket allApi() {
        return createDocket("all", null);
    }

    /**
     * Admin 包的接口
     *
     * @return
     */
    @Bean
    public Docket adminApi() {
        return createDocket("Admin", "com.howe.admin");
    }

    private Docket createDocket(String groupName, String basePackage) {
        Docket docket = new Docket(DocumentationType.OAS_30)
                .enable(enable)
                .groupName(groupName)
                .apiInfo(setApiInfo())
                .securitySchemes(securitySchemes())
                .securityContexts(securityContexts())
                .pathMapping(pathMapping);
        if (StringUtils.isBlank(basePackage)) {
            docket.select()
                    .paths(PathSelectors.any())
                    .apis(RequestHandlerSelectors.any())
                    .build();
        } else {
            docket.select()
                    .paths(PathSelectors.any())
                    .apis(RequestHandlerSelectors.basePackage(basePackage))
                    .build();
        }
        return docket;
    }

    /**
     * 安全模式，这里指定token通过Authorization头请求头传递
     */
    private List<SecurityScheme> securitySchemes() {
        List<SecurityScheme> securitySchemes = new ArrayList<>();
        ApiKey apiKey = new ApiKey("token", "token", In.HEADER.toValue());
        securitySchemes.add(apiKey);
        return securitySchemes;
    }

    /**
     * 安全上下文
     *
     * @return
     */
    private List<SecurityContext> securityContexts() {
        List<SecurityContext> securityContexts = new ArrayList<>();
        SecurityContext securityContext = SecurityContext.builder()
                .securityReferences(defaultAuth())
                .operationSelector(o -> o.requestMappingPattern().matches("/.*"))
                .build();
        securityContexts.add(securityContext);
        return securityContexts;
    }

    /**
     * 默认的安全上引用
     */
    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        List<SecurityReference> securityReferences = new ArrayList<>();
        securityReferences.add(new SecurityReference("token", authorizationScopes));
        return securityReferences;
    }


    private ApiInfo setApiInfo() {
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title(howeConfig.getName() + "_接口文档")
                .description("描述：是一个xxx的系统，包括xxx,xxxx")
                .contact(new Contact("Howe", "https://howello.github.io/", "xxxxx@gmail.com"))
                .version("版本号：" + howeConfig.getVersion())
                .build();
        return apiInfo;
    }

    /**
     * 增加如下配置可解决Spring Boot 6.x 与Swagger 3.0.0 不兼容问题
     **/
    @Bean
    public WebMvcEndpointHandlerMapping webEndpointServletHandlerMapping(WebEndpointsSupplier webEndpointsSupplier,
                                                                         ServletEndpointsSupplier servletEndpointsSupplier,
                                                                         ControllerEndpointsSupplier controllerEndpointsSupplier,
                                                                         EndpointMediaTypes endpointMediaTypes,
                                                                         CorsEndpointProperties corsProperties,
                                                                         WebEndpointProperties webEndpointProperties, Environment environment) {
        List<ExposableEndpoint<?>> allEndpoints = new ArrayList<>();
        Collection<ExposableWebEndpoint> webEndpoints = webEndpointsSupplier.getEndpoints();
        allEndpoints.addAll(webEndpoints);
        allEndpoints.addAll(servletEndpointsSupplier.getEndpoints());
        allEndpoints.addAll(controllerEndpointsSupplier.getEndpoints());
        String basePath = webEndpointProperties.getBasePath();
        EndpointMapping endpointMapping = new EndpointMapping(basePath);
        boolean shouldRegisterLinksMapping = this.shouldRegisterLinksMapping(webEndpointProperties, environment, basePath);
        return new WebMvcEndpointHandlerMapping(endpointMapping, webEndpoints, endpointMediaTypes, corsProperties.toCorsConfiguration(),
                new EndpointLinksResolver(allEndpoints, basePath), shouldRegisterLinksMapping, null);
    }

    private boolean shouldRegisterLinksMapping(WebEndpointProperties webEndpointProperties, Environment environment, String basePath) {
        return webEndpointProperties.getDiscovery().isEnabled() && (StringUtils.isNotBlank(basePath) || ManagementPortType.get(environment).equals(ManagementPortType.DIFFERENT));
    }
}
