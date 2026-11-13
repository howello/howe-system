package com.howe.common.tool.spi;

import com.howe.common.tool.model.ToolDefinition;
import java.util.Collection;

/**
 * 业务工具提供者 SPI。
 *
 * <p>由各业务模块实现并在本模块的条件配置中注册；MCP 平台按本接口收集工具定义。
 * 实现必须返回稳定、唯一的工具定义集合，不得扫描或发布第三方工具。</p>
 */
public interface BusinessToolProvider {

    /**
     * 返回稳定的领域标识，仅用于日志与排查。
     *
     * @return 领域标识
     */
    String domain();

    /**
     * 返回本提供者发布的工具定义。
     *
     * @return 工具定义集合
     */
    Collection<ToolDefinition> definitions();
}
