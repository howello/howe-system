package com.howe.common.config;

/**
 * 参数配置读取
 *
 * <p>
 * 参数配置表在 module-system，而 module-common 是最底层模块不能反向依赖它。
 * 这里用依赖倒置：接口留在 module-common，由 module-system 的
 * {@code SysConfigServiceImpl} 实现，底层工具类通过 Spring 容器拿到实现。
 * </p>
 *
 * @author howe
 */
public interface ConfigProvider
{
    /**
     * 按键名读取参数值
     *
     * @param configKey 参数键名
     * @return 参数值；不存在时返回空串
     */
    String getConfigValue(String configKey);
}
