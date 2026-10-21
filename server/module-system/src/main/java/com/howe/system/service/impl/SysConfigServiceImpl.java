package com.howe.system.service.impl;

import java.util.Collection;
import java.util.List;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.constant.UserConstants;
import com.howe.common.config.ConfigProvider;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.core.text.Convert;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.crypto.SecretCipher;
import com.howe.system.domain.SysConfig;
import com.howe.system.mapper.SysConfigMapper;
import com.howe.system.service.ISysConfigService;

/**
 * 参数配置 服务层实现
 *
 * <p>
 * 敏感参数（{@code sensitive='Y'}）有两条完全分开的读写路径，不能混用：
 * </p>
 * <ul>
 *   <li><b>对外路径</b> {@link #selectConfigByKey} / {@link #selectConfigById} /
 *       {@link #selectConfigList}：敏感值一律换成掩码，供 HTTP 接口使用；</li>
 *   <li><b>内部路径</b> {@link #getPlainValue}：解密后返回明文，供 {@code ConfigUtils} 等
 *       服务内部调用。</li>
 * </ul>
 * <p>
 * 之所以不在同一条路径上做判断：{@code selectConfigByKey} 被 HTTP 与内部共用，
 * 一旦它返回掩码，R2 上传、GitHub 发文这些内部链路会拿到 {@code sk-****abcd} 去发请求，
 * 故障表现为「认证失败」而不是「配置读错」，排查成本极高。
 * </p>
 *
 * @author howe
 */
@Slf4j
@Service
public class SysConfigServiceImpl implements ISysConfigService, ConfigProvider
{
    /** 掩码中的固定星号段 */
    private static final String MASK = "****";

    /** 掩码值的最短长度：再短就没什么可隐藏的，统一给固定占位 */
    private static final int MASK_MIN_LENGTH = 8;

    /** 掩码保留的首部字符数 */
    private static final int MASK_HEAD = 3;

    /** 掩码保留的尾部字符数 */
    private static final int MASK_TAIL = 4;

    @Autowired
    private SysConfigMapper configMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private SecretCipher secretCipher;

    /**
     * 项目启动时，初始化参数到缓存
     */
    @PostConstruct
    public void init()
    {
        loadingConfigCache();
    }

    /**
     * 供 module-common 的底层工具类读取参数配置
     *
     * <p>
     * 敏感参数在这里解密，因此本方法只能被服务内部调用。
     * </p>
     *
     * @param configKey 参数键名
     * @return 参数明文
     */
    @Override
    public String getPlainValue(String configKey)
    {
        return resolvePlain(configKey);
    }

    /**
     * 查询参数配置信息
     *
     * <p>
     * 返回给参数详情页，敏感值做掩码处理。
     * </p>
     *
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    public SysConfig selectConfigById(Long configId)
    {
        SysConfig config = new SysConfig();
        config.setConfigId(configId);
        SysConfig ret = configMapper.selectConfig(config);
        maskIfSensitive(ret);
        return ret;
    }

    /**
     * 根据键名查询参数配置信息
     *
     * <p>
     * 敏感参数返回掩码而不是明文：这个方法的调用方包含
     * {@code GET /system/config/configKey/{configKey}}，是本次渗透的实际泄露出口。
     * </p>
     *
     * @param configKey 参数key
     * @return 参数键值；敏感键为掩码
     */
    @Override
    public String selectConfigByKey(String configKey)
    {
        if (StringUtils.isEmpty(configKey))
        {
            return StringUtils.EMPTY;
        }
        String cached = Convert.toStr(redisCache.getCacheObject(getCacheKey(configKey)));
        if (StringUtils.isNotEmpty(cached))
        {
            // 缓存里是密文 ⇒ 必然是敏感项，无需再查库
            if (secretCipher.isEncrypted(cached))
            {
                return maskValue(secretCipher.decrypt(cached));
            }
            // 明文：可能是非敏感项，也可能是尚未加密迁移的存量敏感项，查库确认标记
            SysConfig row = queryConfigByKey(configKey);
            return row != null && row.sensitiveEnabled() ? maskValue(cached) : cached;
        }
        SysConfig row = queryConfigByKey(configKey);
        if (row == null)
        {
            return StringUtils.EMPTY;
        }
        redisCache.setCacheObject(getCacheKey(configKey), row.getConfigValue());
        String plain = decryptIfNeeded(row.getConfigValue());
        return row.sensitiveEnabled() ? maskValue(plain) : plain;
    }

    /**
     * 获取验证码开关
     *
     * @return true开启，false关闭
     */
    @Override
    public boolean selectCaptchaEnabled()
    {
        // 走内部路径取明文：这里要的是 true/false 本身，掩码会让判断恒为 false
        String captchaEnabled = getPlainValue(ConfigConstants.CAPTCHA_ENABLED);
        if (StringUtils.isEmpty(captchaEnabled))
        {
            return true;
        }
        return Convert.toBool(captchaEnabled);
    }

    /**
     * 查询参数配置列表
     *
     * <p>
     * 列表与导出共用本方法，敏感值统一做掩码。
     * </p>
     *
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<SysConfig> selectConfigList(SysConfig config)
    {
        List<SysConfig> list = configMapper.selectConfigList(config);
        if (StringUtils.isNotEmpty(list))
        {
            list.forEach(this::maskIfSensitive);
        }
        return list;
    }

    /**
     * 新增参数配置
     *
     * <p>
     * 敏感值先加密再入库，缓存里也放密文——缓存与库保持同一形态，
     * 避免出现「重启后能读、热更新后读不到」这类只在生产才复现的问题。
     * </p>
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public int insertConfig(SysConfig config)
    {
        normalizeSensitiveFlag(config);
        String stored = encryptIfSensitive(config.getConfigKey(), config.getSensitive(), config.getConfigValue());
        config.setConfigValue(stored);
        int row = configMapper.insertConfig(config);
        if (row > 0)
        {
            redisCache.setCacheObject(getCacheKey(config.getConfigKey()), stored);
        }
        return row;
    }

    /**
     * 修改参数配置
     *
     * <p>
     * 敏感参数支持「留空不改」：前端拿不到明文，编辑框里是掩码，
     * 用户不动它就直接提交掩码回来。这里识别出掩码或空值后沿用库中原值，
     * 否则会把 {@code sk-****abcd} 当成新密钥存进去。
     * </p>
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public int updateConfig(SysConfig config)
    {
        SysConfig temp = configMapper.selectConfigById(config.getConfigId());
        if (temp == null)
        {
            throw new ServiceException("参数不存在或已被删除");
        }
        if (!StringUtils.equals(temp.getConfigKey(), config.getConfigKey()))
        {
            redisCache.deleteObject(getCacheKey(temp.getConfigKey()));
        }

        // 前端未回传 sensitive 时按库中原值处理，避免「改个备注把敏感标记丢了」
        if (StringUtils.isEmpty(config.getSensitive()))
        {
            config.setSensitive(temp.getSensitive());
        }
        // 键名被改动时同样按原键的标记兜底
        String configKey = StringUtils.isEmpty(config.getConfigKey()) ? temp.getConfigKey() : config.getConfigKey();
        boolean sensitive = isSensitive(configKey, config.getSensitive());
        // 值没提交或提交了掩码 ⇒ 沿用原值，避免把 sk-****abcd 当成新密钥存进去
        if (sensitive && (StringUtils.isEmpty(config.getConfigValue()) || containsMask(config.getConfigValue())))
        {
            config.setConfigValue(temp.getConfigValue());
        }
        else
        {
            config.setConfigValue(encryptIfSensitive(configKey, config.getSensitive(), config.getConfigValue()));
        }

        int row = configMapper.updateConfig(config);
        if (row > 0)
        {
            redisCache.setCacheObject(getCacheKey(configKey), config.getConfigValue());
        }
        return row;
    }

    /**
     * 批量删除参数信息
     *
     * @param configIds 需要删除的参数ID
     */
    @Override
    public void deleteConfigByIds(Long[] configIds)
    {
        for (Long configId : configIds)
        {
            // 直接查库：selectConfigById 会把值换成掩码，删除只需要键名与内置标记
            SysConfig config = configMapper.selectConfigById(configId);
            if (config == null)
            {
                continue;
            }
            if (StringUtils.equals(UserConstants.YES, config.getConfigType()))
            {
                throw new ServiceException(String.format("内置参数【%1$s】不能删除 ", config.getConfigKey()));
            }
            configMapper.deleteConfigById(configId);
            redisCache.deleteObject(getCacheKey(config.getConfigKey()));
        }
    }

    /**
     * 加载参数缓存数据
     *
     * <p>
     * 缓存存的是库里的原值：敏感项即密文。读的时候统一解密，
     * 这样缓存与库永远同构，不需要区分「缓存里是明文还是密文」。
     * </p>
     */
    @Override
    public void loadingConfigCache()
    {
        List<SysConfig> configsList = configMapper.selectConfigList(new SysConfig());
        for (SysConfig config : configsList)
        {
            redisCache.setCacheObject(getCacheKey(config.getConfigKey()), config.getConfigValue());
        }
    }

    /**
     * 清空参数缓存数据
     */
    @Override
    public void clearConfigCache()
    {
        Collection<String> keys = redisCache.keys(CacheConstants.SYS_CONFIG_KEY + "*");
        redisCache.deleteObject(keys);
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    public void resetConfigCache()
    {
        clearConfigCache();
        loadingConfigCache();
    }

    /**
     * 校验参数键名是否唯一
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public boolean checkConfigKeyUnique(SysConfig config)
    {
        Long configId = StringUtils.isNull(config.getConfigId()) ? -1L : config.getConfigId();
        SysConfig info = configMapper.checkConfigKeyUnique(config.getConfigKey());
        if (StringUtils.isNotNull(info) && info.getConfigId().longValue() != configId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 读取参数明文
     *
     * <p>
     * 缓存优先；缓存未命中回落数据库并回填。敏感项的缓存值可能是密文
     * （新写入的），也可能是历史明文（未迁移的存量数据），这里按前缀自动识别。
     * </p>
     *
     * @param configKey 参数键名
     * @return 明文；不存在时返回空串
     */
    private String resolvePlain(String configKey)
    {
        if (StringUtils.isEmpty(configKey))
        {
            return StringUtils.EMPTY;
        }
        String cached = Convert.toStr(redisCache.getCacheObject(getCacheKey(configKey)));
        if (StringUtils.isNotEmpty(cached))
        {
            return decryptIfNeeded(cached);
        }
        SysConfig retConfig = queryConfigByKey(configKey);
        if (retConfig == null)
        {
            return StringUtils.EMPTY;
        }
        String stored = retConfig.getConfigValue();
        redisCache.setCacheObject(getCacheKey(configKey), stored);
        return decryptIfNeeded(stored);
    }

    /**
     * 按键名查询参数配置行
     *
     * @param configKey 参数键名
     * @return 参数配置；不存在时返回 null
     */
    private SysConfig queryConfigByKey(String configKey)
    {
        SysConfig query = new SysConfig();
        query.setConfigKey(configKey);
        return configMapper.selectConfig(query);
    }

    /**
     * 值已是密文则解密，否则原样返回
     *
     * <p>
     * 兼容历史明文：上线时存量敏感项还没重新保存，库里是明文。
     * 直接当密文解会抛异常，把整条链路打断。
     * </p>
     *
     * @param stored 库中或缓存中的值
     * @return 明文
     */
    private String decryptIfNeeded(String stored)
    {
        if (!secretCipher.isEncrypted(stored))
        {
            return stored;
        }
        return secretCipher.decrypt(stored);
    }

    /**
     * 敏感值加密，非敏感值原样返回
     *
     * <p>
     * 已经带 {@code enc:v1:} 前缀的值直接透传，保证重复提交不会二次加密。
     * </p>
     *
     * @param configKey 参数键名
     * @param sensitive 敏感标记
     * @param value 待入库的值
     * @return 实际入库的值
     */
    private String encryptIfSensitive(String configKey, String sensitive, String value)
    {
        if (!isSensitive(configKey, sensitive) || StringUtils.isEmpty(value))
        {
            return value;
        }
        if (secretCipher.isEncrypted(value))
        {
            return value;
        }
        return secretCipher.encrypt(value);
    }

    /**
     * 给敏感项打掩码
     *
     * <p>
     * 判定条件放宽到「标记为敏感」或「值已是密文」：后者覆盖标记被误改成 N、
     * 但库里还留着密文的情况，避免把 {@code enc:v1:...} 原样回给前端。
     * </p>
     *
     * @param config 参数配置；为空或非敏感时不做处理
     */
    private void maskIfSensitive(SysConfig config)
    {
        if (config == null || StringUtils.isEmpty(config.getConfigValue()))
        {
            return;
        }
        String stored = config.getConfigValue();
        if (!config.sensitiveEnabled() && !secretCipher.isEncrypted(stored))
        {
            return;
        }
        try
        {
            config.setConfigValue(maskValue(decryptIfNeeded(stored)));
        }
        catch (ServiceException e)
        {
            // 主密钥没配或换过：列表页不能因此整体打不开，给固定占位并保留原始告警
            log.warn("参数 {} 解密失败，掩码降级为固定占位：{}", config.getConfigKey(), e.getMessage());
            config.setConfigValue(MASK);
        }
    }

    /**
     * 生成掩码
     *
     * <p>
     * 保留首 3 位与末 4 位，便于管理员核对「填的是哪一把」，
     * 中间统一星号。太短的值直接给固定占位，避免掩码反而暴露长度与全部字符。
     * </p>
     *
     * @param plain 明文
     * @return 掩码
     */
    private String maskValue(String plain)
    {
        if (StringUtils.isEmpty(plain))
        {
            return StringUtils.EMPTY;
        }
        if (plain.length() <= MASK_MIN_LENGTH)
        {
            return MASK;
        }
        return plain.substring(0, MASK_HEAD) + MASK + plain.substring(plain.length() - MASK_TAIL);
    }

    /**
     * 判断提交值里是否含掩码
     *
     * @param value 前端提交的值
     * @return true 表示这是掩码而不是真实密钥
     */
    private boolean containsMask(String value)
    {
        return StringUtils.contains(value, MASK);
    }

    /**
     * 判断参数是否敏感
     *
     * <p>
     * 以数据库标记为准；标记缺失时回落到 {@link ConfigConstants#SENSITIVE_KEYS}，
     * 防止新增参数漏勾「是否敏感」导致明文落库。
     * </p>
     *
     * @param configKey 参数键名
     * @param sensitive 库中或请求中的敏感标记
     * @return true 表示按敏感参数处理
     */
    private boolean isSensitive(String configKey, String sensitive)
    {
        if (StringUtils.isNotEmpty(sensitive))
        {
            return UserConstants.YES.equals(sensitive);
        }
        return ConfigConstants.isSensitiveKey(configKey);
    }

    /**
     * 补全新增时的敏感标记
     *
     * <p>
     * 前端未传时按 {@link ConfigConstants#SENSITIVE_KEYS} 兜底，
     * 清单外的参数默认 N。
     * </p>
     *
     * @param config 参数配置
     */
    private void normalizeSensitiveFlag(SysConfig config)
    {
        if (StringUtils.isNotEmpty(config.getSensitive()))
        {
            return;
        }
        config.setSensitive(ConfigConstants.isSensitiveKey(config.getConfigKey()) ? UserConstants.YES : "N");
    }

    /**
     * 设置cache key
     *
     * @param configKey 参数键
     * @return 缓存键key
     */
    private String getCacheKey(String configKey)
    {
        return CacheConstants.SYS_CONFIG_KEY + configKey;
    }
}
