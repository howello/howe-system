package com.howe.framework.captcha;

import cn.hutool.captcha.AbstractCaptcha;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.RandomUtil;

/**
 * 验证码类型
 *
 * <p>
 * 原先只有 kaptcha 一种扭曲字符图，这里换成 hutool-captcha 提供的多种样式。
 * 每种类型自己负责造出 hutool 的验证码对象，避免在服务里堆一长串 switch。
 * </p>
 *
 * @author howe
 */
public enum CaptchaType
{
    /** 扭曲字符：默认样式，可读性与抗识别折中得比较好 */
    CHAR("char", "扭曲字符")
    {
        @Override
        public AbstractCaptcha create(int width, int height)
        {
            AbstractCaptcha captcha = CaptchaUtil.createShearCaptcha(width, height, 4, 4);
            captcha.setGenerator(new RandomGenerator(RANDOM_CHARS, 4));
            return captcha;
        }
    },

    /** 算术运算：答案是数字，移动端输入最省事 */
    MATH("math", "算术运算")
    {
        @Override
        public AbstractCaptcha create(int width, int height)
        {
            AbstractCaptcha captcha = CaptchaUtil.createLineCaptcha(width, height, 4, 20);
            captcha.setGenerator(new MathGenerator(1));
            return captcha;
        }
    },

    /** 线段干扰 */
    LINE("line", "线段干扰")
    {
        @Override
        public AbstractCaptcha create(int width, int height)
        {
            AbstractCaptcha captcha = CaptchaUtil.createLineCaptcha(width, height, 4, 30);
            captcha.setGenerator(new RandomGenerator(RANDOM_CHARS, 4));
            return captcha;
        }
    },

    /** 圆圈干扰 */
    CIRCLE("circle", "圆圈干扰")
    {
        @Override
        public AbstractCaptcha create(int width, int height)
        {
            AbstractCaptcha captcha = CaptchaUtil.createCircleCaptcha(width, height, 4, 20);
            captcha.setGenerator(new RandomGenerator(RANDOM_CHARS, 4));
            return captcha;
        }
    },

    /** 扭曲干扰 */
    SHEAR("shear", "扭曲干扰")
    {
        @Override
        public AbstractCaptcha create(int width, int height)
        {
            AbstractCaptcha captcha = CaptchaUtil.createShearCaptcha(width, height, 4, 6);
            captcha.setGenerator(new RandomGenerator(RANDOM_CHARS, 4));
            return captcha;
        }
    },

    /** 动态 GIF：帧间错位，对 OCR 最不友好 */
    GIF("gif", "动态图形")
    {
        @Override
        public AbstractCaptcha create(int width, int height)
        {
            AbstractCaptcha captcha = CaptchaUtil.createGifCaptcha(width, height, 4);
            captcha.setGenerator(new RandomGenerator(RANDOM_CHARS, 4));
            return captcha;
        }

        @Override
        public String getImageType()
        {
            return "gif";
        }
    };

    /**
     * 随机字符池
     *
     * <p>
     * 去掉了 0/o/O、1/l/i 这些肉眼分不清的字符，减少「看对了却输错」的误判。
     * </p>
     */
    private static final String RANDOM_CHARS = "abcdefghjkmnpqrstuvwxy23456789";

    /** 配置里写的值 */
    private final String code;

    /** 中文描述 */
    private final String label;

    CaptchaType(String code, String label)
    {
        this.code = code;
        this.label = label;
    }

    public String getCode()
    {
        return code;
    }

    public String getLabel()
    {
        return label;
    }

    /**
     * 图片格式，决定前端 data URI 前缀
     */
    public String getImageType()
    {
        return "png";
    }

    /**
     * 造一个 hutool 验证码对象
     *
     * @param width 宽
     * @param height 高
     * @return 验证码对象，调用方直接取 code 与 base64
     */
    public abstract AbstractCaptcha create(int width, int height);

    /**
     * 按配置值解析类型
     *
     * <p>
     * {@code random} 表示每次随机挑一种；无法识别的值一律回落到 {@link #MATH}，
     * 避免参数配置写错就把登录页整个打死。
     * </p>
     *
     * @param code 配置值
     * @return 验证码类型
     */
    public static CaptchaType of(String code)
    {
        if (code == null || code.isBlank())
        {
            return MATH;
        }
        String normalized = code.trim().toLowerCase();
        if ("random".equals(normalized))
        {
            return RandomUtil.randomEle(values());
        }
        for (CaptchaType type : values())
        {
            if (type.code.equals(normalized))
            {
                return type;
            }
        }
        return MATH;
    }
}
