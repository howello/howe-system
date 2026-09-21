package com.howe.ai.provider;

import org.springframework.ai.image.ImageOptions;

/**
 * raw-http 协议的文生图选项
 *
 * <p>Spring AI 的 {@link ImageOptions} 没有负向提示词、种子和统一尺寸字段，
 * 这里补上，供 {@link RawHttpImageModel} 直接取用。</p>
 *
 * @param n              生成张数
 * @param model          模型名
 * @param width          宽度
 * @param height         高度
 * @param size           厂商原始尺寸写法，如 {@code 1024*1024}
 * @param negativePrompt 负向提示词
 * @param seed           随机种子
 * @author howe
 */
public record RawImageOptions(Integer n, String model, Integer width, Integer height, String size,
        String negativePrompt, Long seed) implements ImageOptions
{
    @Override
    public Integer getN()
    {
        return n;
    }

    @Override
    public String getModel()
    {
        return model;
    }

    @Override
    public Integer getWidth()
    {
        return width;
    }

    @Override
    public Integer getHeight()
    {
        return height;
    }

    @Override
    public String getResponseFormat()
    {
        return null;
    }

    @Override
    public String getStyle()
    {
        return null;
    }
}
