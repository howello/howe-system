package com.howe.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 装配契约测试：模拟 Spring 的构造器注入解析，确保被容器管理的类真的能实例化。
 * 事件链路与 Harness 曾因缺少 stereotype、Mapper 未被扫描而在启动期直接抛
 * UnsatisfiedDependencyException，这类缺陷必须由测试拦截，而不是等到部署才发现。
 */
class AiSpringWiringTest {
    /** 由平台或框架提供、无需 module-ai 自己声明的注入类型。 */
    private static final Set<String> PLATFORM_TYPES = Set.of(
        "org.springframework.data.redis.core.RedisTemplate",
        "org.springframework.data.redis.core.StringRedisTemplate",
        "org.springframework.beans.factory.ObjectProvider",
        "org.springframework.core.env.Environment",
        "com.fasterxml.jackson.databind.ObjectMapper",
        "javax.sql.DataSource",
        "org.springframework.context.ApplicationContext");

    @Test
    void springManagedClassesResolveEveryConstructorDependency() throws Exception {
        List<Class<?>> managed = mainClasses().stream().filter(AiSpringWiringTest::isSpringManaged).toList();
        assertTrue(managed.size() >= 5, "module-ai 应当存在被容器管理的类");

        Set<String> providable = providableTypes(managed);
        List<String> failures = new ArrayList<>();
        for (Class<?> type : managed) {
            Constructor<?> constructor;
            try {
                constructor = injectionConstructor(type);
            } catch (IllegalStateException ambiguous) {
                failures.add(type.getName() + " -> " + ambiguous.getMessage());
                continue;
            }
            for (java.lang.reflect.Parameter parameter : constructor.getParameters()) {
                if (!isSatisfiable(parameter, providable)) {
                    failures.add(type.getName() + " 无法注入依赖 " + parameter.getType().getName());
                }
            }
        }
        assertTrue(failures.isEmpty(), "存在无法被 Spring 装配的类：\n" + String.join("\n", failures));
    }

    @Test
    void mapperInterfacesAreCoveredByMapperScan() throws Exception {
        List<Class<?>> mappers = mainClasses().stream()
            .filter(type -> type.isInterface() && type.getSimpleName().endsWith("Mapper")).toList();
        assertTrue(!mappers.isEmpty(), "module-ai 应当存在 Mapper 接口");

        Set<String> scanned = mapperScanPackages();
        List<String> uncovered = mappers.stream()
            .filter(mapper -> scanned.stream().noneMatch(scan -> mapper.getPackageName().equals(scan)
                || mapper.getPackageName().startsWith(scan + ".")))
            .map(Class::getName).toList();
        assertTrue(uncovered.isEmpty(),
            "以下 Mapper 不在任何 @MapperScan 范围内，运行期取不到 bean：" + uncovered
                + "；当前已声明的扫描包=" + scanned);
    }

    /** 全局 @MapperScan("com.howe.**.mapper") 只覆盖 *.mapper 包，AI 的 Mapper 必须自行声明扫描。 */
    private static Set<String> mapperScanPackages() throws Exception {
        Set<String> packages = new LinkedHashSet<>();
        // 父工程的全局扫描规则：仅匹配以 .mapper 结尾的包。
        for (Class<?> type : mainClasses()) {
            org.mybatis.spring.annotation.MapperScan scan =
                type.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
            if (scan == null) continue;
            packages.addAll(List.of(scan.value()));
            packages.addAll(List.of(scan.basePackages()));
        }
        for (Class<?> type : mainClasses()) {
            if (type.isInterface() && type.getSimpleName().endsWith("Mapper")
                && type.getPackageName().endsWith(".mapper")) {
                packages.add(type.getPackageName());
            }
        }
        return packages;
    }

    private static Set<String> providableTypes(List<Class<?>> managed) {
        Set<String> providable = new LinkedHashSet<>(PLATFORM_TYPES);
        for (Class<?> type : managed) {
            collectHierarchy(type, providable);
            if (type.getAnnotation(org.springframework.context.annotation.Configuration.class) == null) continue;
            for (Method method : type.getDeclaredMethods()) {
                if (method.getAnnotation(org.springframework.context.annotation.Bean.class) != null) {
                    collectHierarchy(method.getReturnType(), providable);
                }
            }
        }
        return providable;
    }

    /** 注入按类型匹配，因此实现类同时满足其父类与接口。 */
    private static void collectHierarchy(Class<?> type, Set<String> sink) {
        if (type == null || type == Object.class || !sink.add(type.getName())) return;
        collectHierarchy(type.getSuperclass(), sink);
        for (Class<?> each : type.getInterfaces()) collectHierarchy(each, sink);
    }

    private static boolean isSatisfiable(java.lang.reflect.Parameter parameter, Set<String> providable) {
        // @Value 直接来自配置，不需要容器中存在对应类型的 bean。
        if (parameter.getAnnotation(org.springframework.beans.factory.annotation.Value.class) != null) return true;
        Class<?> type = parameter.getType();
        // Spring 集合注入（List<X>/Set<X>/Map<K,V>）：无匹配 bean 时注入空集合，始终可满足。
        if (type == java.util.List.class || type == java.util.Set.class || type == java.util.Map.class) {
            return true;
        }
        if (providable.contains(type.getName())) return true;
        // MyBatis Mapper 由 @MapperScan 注册，扫描覆盖即视为可注入。
        if (type.isInterface() && type.getSimpleName().endsWith("Mapper")) {
            try {
                return mapperScanPackages().stream().anyMatch(scan -> type.getPackageName().equals(scan)
                    || type.getPackageName().startsWith(scan + "."));
            } catch (Exception failure) {
                return false;
            }
        }
        return false;
    }

    private static Constructor<?> injectionConstructor(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        List<Constructor<?>> annotated = List.of(constructors).stream()
            .filter(each -> each.getAnnotation(org.springframework.beans.factory.annotation.Autowired.class) != null)
            .toList();
        if (annotated.size() == 1) return annotated.get(0);
        if (annotated.size() > 1) throw new IllegalStateException("存在多个 @Autowired 构造器");
        if (constructors.length == 1) return constructors[0];
        return List.of(constructors).stream().filter(each -> each.getParameterCount() == 0).findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "存在 " + constructors.length + " 个构造器但都未标注 @Autowired，且没有无参构造器"));
    }

    private static boolean isSpringManaged(Class<?> type) {
        if (type.isInterface() || type.isEnum() || type.isRecord() || Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        return type.getAnnotation(org.springframework.stereotype.Component.class) != null
            || type.getAnnotation(org.springframework.stereotype.Service.class) != null
            || type.getAnnotation(org.springframework.stereotype.Repository.class) != null
            || type.getAnnotation(org.springframework.stereotype.Controller.class) != null
            || type.getAnnotation(org.springframework.web.bind.annotation.RestController.class) != null
            || type.getAnnotation(org.springframework.context.annotation.Configuration.class) != null;
    }

    private static List<Class<?>> mainClasses() throws Exception {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            List<String> names = files.filter(path -> path.toString().endsWith(".java"))
                .map(path -> root.relativize(path).toString()
                    .replace(java.io.File.separatorChar, '.').replaceAll("\\.java$", ""))
                .sorted().toList();
            List<Class<?>> classes = new ArrayList<>();
            for (String name : names) {
                try {
                    classes.add(Class.forName(name));
                } catch (Throwable ignored) {
                    // 顶层类之外的辅助文件忽略，不影响装配判定。
                }
            }
            return classes;
        }
    }
}
