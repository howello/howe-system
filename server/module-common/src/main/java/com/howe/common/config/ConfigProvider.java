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
     * <p>
     * <b>仅供服务内部使用</b>：敏感参数（如 R2 密钥、GitHub 令牌）会在这里解密成明文。
     * HTTP 接口不要调用它，否则等于把掩码机制绕过去了——请改用
     * {@code ISysConfigService#selectConfigByKey}，敏感键在那里会返回掩码。
     * </p>
     *
     * @param configKey 参数键名
     * @return 参数值；不存在时返回空串
     */
    String getPlainValue(String configKey);
}
