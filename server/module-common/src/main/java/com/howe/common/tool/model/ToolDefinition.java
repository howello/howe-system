package com.howe.common.tool.model;

import com.howe.common.tool.spi.ToolHandler;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 协议无关的工具定义。
 *
 * <p>描述工具的身份、能力分组、输入结构、访问要求、行为声明与执行限制，
 * 并携带参数校验器与处理器。本类型不引用 MCP SDK、OAuth 或协议会话。</p>
 *
 * @param name 工具名，小写 snake_case
 * @param version 工具版本
 * @param title 展示标题
 * @param description 用途说明
 * @param capabilityGroup 能力分组，如 {@code blog.read}
 * @param inputSchema 输入结构
 * @param access 业务访问要求
 * @param annotations 行为声明
 * @param timeout 执行超时
 * @param maxResultBytes 结果字节上限
 * @param validate 参数校验器，抛出 {@link IllegalArgumentException} 表示参数非法
 * @param handler 执行处理器
 */
public record ToolDefinition(String name, String version, String title, String description, String capabilityGroup,
        ToolInputSchema inputSchema, ToolAccess access, ToolAnnotations annotations, Duration timeout,
        int maxResultBytes, Consumer<Map<String, Object>> validate, ToolHandler handler) {

    /** 工具名允许的字符与长度。 */
    private static final String NAME_PATTERN = "[a-zA-Z0-9_-]{1,64}";

    /** 工具超时硬上限。 */
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(1);

    /** 校验必要字段与执行限制，拒绝无法安全发布的定义。 */
    public ToolDefinition {
        Objects.requireNonNull(name, "工具名不能为空");
        if (!name.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException("工具名不合法：" + name);
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("工具版本不能为空");
        }
        if (capabilityGroup == null || capabilityGroup.isBlank()) {
            throw new IllegalArgumentException("能力分组不能为空：" + name);
        }
        Objects.requireNonNull(inputSchema, "输入结构不能为空");
        Objects.requireNonNull(access, "访问要求不能为空");
        Objects.requireNonNull(annotations, "行为声明不能为空");
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("工具超时必须大于 0 且不超过 60 秒：" + name);
        }
        if (maxResultBytes < 1) {
            throw new IllegalArgumentException("工具结果上限必须为正数：" + name);
        }
        Objects.requireNonNull(validate, "参数校验器不能为空");
        Objects.requireNonNull(handler, "处理器不能为空");
    }

    /**
     * 工具行为声明，与协议注解逐项对应。
     *
     * <p>六项均为可空语义：为 {@code null} 表示未声明，不得在适配时静默默认化为 {@code false}。</p>
     *
     * @param title 展示标题
     * @param readOnlyHint 是否为只读操作
     * @param destructiveHint 是否可能破坏数据
     * @param idempotentHint 是否幂等
     * @param openWorldHint 是否与外部系统交互
     * @param returnDirect 结果是否应直接返回给用户
     */
    public record ToolAnnotations(String title, Boolean readOnlyHint, Boolean destructiveHint,
            Boolean idempotentHint, Boolean openWorldHint, Boolean returnDirect) {
    }
}
