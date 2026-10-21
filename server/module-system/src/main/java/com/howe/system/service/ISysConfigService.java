package com.howe.system.service;

import java.util.List;
import com.howe.system.domain.SysConfig;

/**
 * 参数配置 服务层
 *
 * @author howe
 */
public interface ISysConfigService
{
    /**
     * 查询参数配置信息
     *
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    public SysConfig selectConfigById(Long configId);

    /**
     * 根据键名查询参数配置信息
     *
     * <p>
     * <b>这是对外路径</b>：敏感参数只返回掩码（形如 {@code sk-****abcd}），
     * 不会把明文交给调用方。服务内部需要真实值时请用 {@link #getPlainValue(String)}。
     * </p>
     *
     * @param configKey 参数键名
     * @return 参数键值；敏感键返回掩码
     */
    public String selectConfigByKey(String configKey);

    /**
     * 按键名读取参数明文
     *
     * <p>
     * <b>仅供服务内部使用</b>：敏感参数会在这里解密成明文。
     * 任何返回给前端的接口都不要直接用它，否则掩码机制形同虚设。
     * </p>
     *
     * @param configKey 参数键名
     * @return 参数明文；不存在时返回空串
     */
    public String getPlainValue(String configKey);

    /**
     * 获取验证码开关
     *
     * @return true开启，false关闭
     */
    public boolean selectCaptchaEnabled();

    /**
     * 查询参数配置列表
     *
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    public List<SysConfig> selectConfigList(SysConfig config);

    /**
     * 新增参数配置
     *
     * @param config 参数配置信息
     * @return 结果
     */
    public int insertConfig(SysConfig config);

    /**
     * 修改参数配置
     *
     * @param config 参数配置信息
     * @return 结果
     */
    public int updateConfig(SysConfig config);

    /**
     * 批量删除参数信息
     *
     * @param configIds 需要删除的参数ID
     */
    public void deleteConfigByIds(Long[] configIds);

    /**
     * 加载参数缓存数据
     */
    public void loadingConfigCache();

    /**
     * 清空参数缓存数据
     */
    public void clearConfigCache();

    /**
     * 重置参数缓存数据
     */
    public void resetConfigCache();

    /**
     * 校验参数键名是否唯一
     *
     * @param config 参数信息
     * @return 结果
     */
    public boolean checkConfigKeyUnique(SysConfig config);
}
