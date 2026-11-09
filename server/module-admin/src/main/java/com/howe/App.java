package com.howe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerJwtAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;

/**
 * 启动程序
 *
 * @author howe
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class,
        OAuth2AuthorizationServerAutoConfiguration.class, OAuth2AuthorizationServerJwtAutoConfiguration.class,
        SessionAutoConfiguration.class })
public class App {
    /** 在网络组件初始化前启用 JDK 系统代理发现；保留运维显式设置的开关。 */
    public static void main(String[] args) {
        System.getProperties().putIfAbsent("java.net.useSystemProxies", "true");
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(App.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }
}
