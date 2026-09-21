package com.howe.ai.provider;

/**
 * 文生图参数
 *
 * <p>业务侧统一用 {@code 1024*1024} 这种尺寸写法，各协议在
 * {@link AiProviderAdapter#imageOptions} 里自行转换。</p>
 *
 * @param size           尺寸，如 {@code 1024*1024}
 * @param count          生成张数
 * @param negativePrompt 负向提示词
 * @param seed           随机种子
 * @author howe
 */
public record ImageSpec(String size, Integer count, String negativePrompt, Long seed)
{
    /**
     * 解析尺寸的宽
     *
     * @param fallback 解析失败时的默认值
     * @return 宽度
     */
    public int width(int fallback)
    {
        Integer[] parsed = parse();
        return parsed == null ? fallback : parsed[0];
    }

    /**
     * 解析尺寸的高
     *
     * @param fallback 解析失败时的默认值
     * @return 高度
     */
    public int height(int fallback)
    {
        Integer[] parsed = parse();
        return parsed == null ? fallback : parsed[1];
    }

    /**
     * 解析 {@code 宽*高} 或 {@code 宽x高}
     *
     * @return 宽高数组，解析失败返回 null
     */
    private Integer[] parse()
    {
        if (size == null)
        {
            return null;
        }
        String normalized = size.trim().toLowerCase().replace('x', '*').replace('×', '*');
        String[] parts = normalized.split("\\*");
        if (parts.length != 2)
        {
            return null;
        }
        try
        {
            return new Integer[] { Integer.valueOf(parts[0].trim()), Integer.valueOf(parts[1].trim()) };
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
