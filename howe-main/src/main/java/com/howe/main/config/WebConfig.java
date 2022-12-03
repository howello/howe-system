package com.howe.main.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/16 9:29 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure content negotiation options.
     *
     * @param configurer
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}
