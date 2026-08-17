package com.howe.ai.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模块的 Mapper 扫描。
 *
 * <p>父工程 {@code ApplicationConfig} 的全局扫描规则是 {@code com.howe.**.mapper}，只匹配以
 * {@code .mapper} 结尾的包；AI 的 Mapper 位于 {@code persistence} 与 {@code config} 包下，不在其范围内。
 * 全局 {@code @MapperScan} 会注册 {@code MapperScannerConfigurer}，从而关闭 MyBatis 对 {@code @Mapper}
 * 注解的自动扫描，因此这里必须显式声明，否则运行期取不到 Mapper bean。</p>
 */
@Configuration
@MapperScan(basePackages = {"com.howe.ai.persistence", "com.howe.ai.config"}, annotationClass = Mapper.class)
public class AiMyBatisConfig {
}
