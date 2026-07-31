package com.howe.framework.captcha;

import cn.hutool.captcha.AbstractCaptcha;
import cn.hutool.core.math.Calculator;
import cn.hutool.core.util.IdUtil;
import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.constant.Constants;
import com.howe.common.config.YmlConfig;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.user.CaptchaException;
import com.howe.common.exception.user.CaptchaExpireException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 图形验证码服务
 *
 * <p>
 * 生成与校验都收在这里，答案不出这个类：控制器只拿得到 uuid 与图片，
 * 免得答案被顺手塞进响应体。底层换成了 hutool-captcha，支持字符/算术/线段/圆圈/扭曲/GIF 六种样式。
 * </p>
 *
 * @author howe
 */
@Slf4j
@Component
public class CaptchaService
{
    /** 验证码图片宽 */
    private static final int WIDTH = 160;

    /** 验证码图片高 */
    private static final int HEIGHT = 60;

    @Autowired
    private RedisCache redisCache;

    /**
     * 验证码是否启用
     *
     * <p>
     * 键名沿用原来的 {@code sys.account.captchaEnabled}，存量参数配置不用改。
     * </p>
     */
    public boolean isEnabled()
    {
        return ConfigUtils.getBoolean(ConfigConstants.CAPTCHA_ENABLED, true);
    }

    /**
     * 当前生效的验证码类型
     *
     * <p>
     * 优先读参数配置表，留空时回落到 yml 的 {@code howe.captchaType}，保证升级前的部署不受影响。
     * </p>
     */
    public CaptchaType resolveType()
    {
        String configured = ConfigUtils.getString(ConfigConstants.CAPTCHA_TYPE);
        if (StringUtils.isEmpty(configured))
        {
            configured = YmlConfig.getCaptchaType();
        }
        return CaptchaType.of(configured);
    }

    /**
     * 生成一张验证码，答案写入 Redis
     *
     * @return 前端渲染所需的信息，不含答案
     */
    public CaptchaVo create()
    {
        CaptchaType type = resolveType();
        AbstractCaptcha captcha = type.create(WIDTH, HEIGHT);
        String answer = resolveAnswer(type, captcha.getCode());

        String uuid = IdUtil.simpleUUID();
        redisCache.setCacheObject(CacheConstants.CAPTCHA_CODE_KEY + uuid, answer,
                Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);
        return new CaptchaVo(uuid, captcha.getImageBase64(), type.getImageType(), type.getCode());
    }

    /**
     * 校验验证码
     *
     * <p>
     * 无论对错都会先把 Redis 里的答案删掉，防止同一张图被反复试。
     * </p>
     *
     * @param uuid 生成时下发的标识
     * @param code 用户输入
     * @throws CaptchaExpireException 验证码不存在或已过期
     * @throws CaptchaException 验证码不匹配
     */
    public void validate(String uuid, String code)
    {
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
        String answer = redisCache.getCacheObject(verifyKey);
        if (answer == null)
        {
            throw new CaptchaExpireException();
        }
        redisCache.deleteObject(verifyKey);
        if (StringUtils.isEmpty(code) || !answer.equalsIgnoreCase(code.trim()))
        {
            throw new CaptchaException();
        }
    }

    /**
     * 算出该存进 Redis 的答案
     *
     * <p>
     * 算术验证码的 {@code getCode()} 拿到的是算式（形如 {@code 3*5=}），
     * 存进去的必须是运算结果；其余类型的 code 本身就是答案。
     * </p>
     */
    private String resolveAnswer(CaptchaType type, String code)
    {
        if (type != CaptchaType.MATH)
        {
            return code;
        }
        try
        {
            return String.valueOf((int) Calculator.conversion(code));
        }
        catch (Exception e)
        {
            // 理论上不会发生，真发生了也不能让登录页崩掉，退化成比对算式本身
            log.error("算术验证码解析失败：{}", code, e);
            return code;
        }
    }
}
