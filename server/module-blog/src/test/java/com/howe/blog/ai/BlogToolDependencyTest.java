package com.howe.blog.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 博客 Tool 依赖边界：博客 Tool 只能依赖 Service 接口，绝不直接持有 Mapper，
 * 否则就绕过了服务层的权限、审计与共表防护（如 blog_link 的 link_type 约束）。
 */
class BlogToolDependencyTest {

    @Test
    void blogToolProviderOnlyHoldsServiceInterfaces() {
        for (Field field : BlogToolProvider.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            String typeName = type.getName();
            assertFalse(typeName.contains("Mapper"), "博客 Tool 不得直接持有 Mapper: " + typeName);
            assertFalse(typeName.contains(".domain."), "博客 Tool 不得直接持有领域实体: " + typeName);
        }
    }

    @Test
    void blogToolProviderImplementsAiToolProviderSpi() {
        List<Class<?>> interfaces = Arrays.asList(BlogToolProvider.class.getInterfaces());
        assertTrue(interfaces.contains(com.howe.ai.contract.AiToolProvider.class),
            "BlogToolProvider 必须实现 module-ai-common 的 AiToolProvider SPI");
    }

    @Test
    void blogToolPackageDoesNotDependOnAiImplementation() {
        // 契约层 module-ai-common 允许；实现层 module-ai（com.howe.ai.application/runtime/...）禁止
        for (Field field : BlogToolProvider.class.getDeclaredFields()) {
            String typeName = field.getType().getName();
            assertFalse(typeName.startsWith("com.howe.ai.application"),
                "博客 Tool 不得依赖 module-ai 实现层: " + typeName);
            assertFalse(typeName.startsWith("com.howe.ai.runtime"),
                "博客 Tool 不得依赖 module-ai 运行时: " + typeName);
            assertFalse(typeName.startsWith("com.howe.ai.gateway"),
                "博客 Tool 不得依赖 module-ai 网关: " + typeName);
        }
    }
}
