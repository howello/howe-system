package com.howe.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.howe.ai.application.SecretCipher;

/**
 * AI 密钥加密装配。
 *
 * <p>主密钥只从部署 Secret/环境变量读取（{@code AI_MASTER_KEY} 经宽松绑定对应 {@code ai.master-key}），
 * 不落配置表也不写入代码库。阶段一 AI 默认关闭，未配置主密钥时不创建该 bean，应用照常启动；
 * 此时任何用到密钥的操作都会在调用点以「AI_MASTER_KEY 未配置」失败关闭，而不是静默放行。</p>
 */
@Configuration
public class AiSecurityConfig {
    @Bean
    @ConditionalOnProperty(name = "ai.master-key")
    public SecretCipher aiSecretCipher(Environment environment) {
        return new SecretCipher(() -> environment.getProperty("ai.master-key"));
    }
}
