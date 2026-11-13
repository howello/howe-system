package com.howe.framework.tool.provider;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import com.howe.framework.tool.dto.SystemHealthDtos;
import com.howe.framework.tool.service.ISystemHealthReadService;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 服务器健康摘要工具提供者。
 *
 * <p>只发布健康摘要一个工具，采集逻辑由 framework 内的读取服务承担，
 * 不调用 admin 的 ServerController，也不复用包含主机信息的 {@code Server} 对象。</p>
 *
 * <p>本类只依赖 common 工具契约与本模块服务，不引用 MCP、SDK 或 OAuth 类型。</p>
 */
public final class SystemHealthToolProvider implements BusinessToolProvider {

    /** 能力分组。 */
    private static final String CAPABILITY_GROUP = "system.audit.read";

    /** 结果预算。 */
    private static final int RESULT_BYTES = 131072;

    private final ISystemHealthReadService health;

    /**
     * 构造健康摘要工具提供者。
     *
     * @param health 健康读取服务
     */
    public SystemHealthToolProvider(ISystemHealthReadService health) {
        this.health = Objects.requireNonNull(health);
    }

    /**
     * 返回领域标识。
     *
     * @return 领域标识
     */
    @Override
    public String domain() {
        return "framework";
    }

    /**
     * 返回健康摘要工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(healthGet());
    }

    /**
     * 健康摘要。
     *
     * @return 工具定义
     */
    private ToolDefinition healthGet() {
        return new ToolDefinition("system_health_get", "1", "读取服务器健康摘要",
                "返回采样时间、CPU 百分比、内存使用比例、本地磁盘使用比例与 JVM 运行时长；"
                        + "不返回主机名、IP、挂载点、路径或 JVM 参数。",
                CAPABILITY_GROUP, ToolInputSchema.emptyObject(),
                ToolAccess.ofPermissions(Set.of("monitor:server:list")),
                new ToolDefinition.ToolAnnotations("读取服务器健康摘要", true, false, true, false, true),
                Duration.ofSeconds(5), RESULT_BYTES,
                ToolArguments::requireEmpty,
                (context, arguments) -> {
                    SystemHealthDtos healthData;
                    try {
                        healthData = health.sample();
                    } catch (ServiceException exception) {
                        throw new ToolException("internal_error", "健康采样失败");
                    } catch (RuntimeException exception) {
                        throw new ToolException("internal_error", "健康采样失败");
                    }
                    if (healthData == null) {
                        throw new ToolException("internal_error", "健康采样失败");
                    }
                    return ToolResults.detail(healthData, "已返回服务器健康摘要");
                });
    }
}
