package com.howe.ai.storage;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 生成图压缩
 *
 * <p>文生图厂商默认输出的是 2~3MB 的 PNG，直接转存既浪费对象存储又拖慢前端加载。
 * 这里在转存前统一处理：长边缩到 {@value #MAX_EDGE}px 再按 JPEG 质量阶梯压缩，
 * 常规菜品图落在 160KB 上下，肉眼基本无损。</p>
 *
 * <p>有透明通道的图保留 PNG，只能靠缩边减体积；仍超限时继续按比例缩边重试。
 * 任何一步解码失败都原样返回——压缩只做「锦上添花」，绝不能让转存失败。</p>
 *
 * @author howe
 */
@Slf4j
public final class AiImageCompressor
{
    /** 压缩目标上限（字节），约 500KB，作为兜底防线 */
    private static final long TARGET_BYTES = 500 * 1024;

    /** 长边缩放上限（像素），超过则等比缩小 */
    private static final int MAX_EDGE = 1024;

    /** JPEG 质量阶梯，从高到低尝试，够小就停（首档即常规配方） */
    private static final float[] QUALITY_LADDER = { 0.80f, 0.75f, 0.70f, 0.60f, 0.50f };

    /** 质量降到最低仍超限时，每轮缩边的比例 */
    private static final float SCALE_STEP = 0.8f;

    /** 最多缩边轮数，避免无限循环 */
    private static final int MAX_SCALE_ROUNDS = 4;

    private AiImageCompressor()
    {
    }

    /**
     * 压缩生成图
     *
     * @param source          原始图片字节
     * @param sourceExtension 原始后缀名（用于未压缩时的兜底）
     * @return 压缩结果，含字节、后缀与尺寸（尺寸读取失败时为 null）
     */
    public static Compressed compress(byte[] source, String sourceExtension)
    {
        if (source == null || source.length <= TARGET_BYTES)
        {
            return new Compressed(source, sourceExtension, null, null);
        }
        BufferedImage image = read(source);
        if (image == null)
        {
            // 无法解码（如缺 WebP 插件）就原样转存
            return new Compressed(source, sourceExtension, null, null);
        }
        boolean alpha = image.getColorModel().hasAlpha();
        byte[] best = source;
        String bestExtension = sourceExtension;
        BufferedImage bestImage = image;
        // 首轮先缩到长边 1024，之后每轮继续缩小
        float scale = Math.min(1f, MAX_EDGE / (float) Math.max(image.getWidth(), image.getHeight()));
        for (int round = 0; round <= MAX_SCALE_ROUNDS; round++)
        {
            BufferedImage canvas = scale >= 1f ? image : scale(image, scale);
            if (alpha)
            {
                byte[] png = encode(canvas, "png", 1f);
                if (isBetter(png, best))
                {
                    best = png;
                    bestExtension = "png";
                    bestImage = canvas;
                }
                if (png != null && png.length <= TARGET_BYTES)
                {
                    return result(png, "png", canvas);
                }
            }
            else
            {
                for (float quality : QUALITY_LADDER)
                {
                    byte[] jpg = encode(canvas, "jpg", quality);
                    if (isBetter(jpg, best))
                    {
                        best = jpg;
                        bestExtension = "jpg";
                        bestImage = canvas;
                    }
                    if (jpg != null && jpg.length <= TARGET_BYTES)
                    {
                        return result(jpg, "jpg", canvas);
                    }
                }
            }
            scale *= SCALE_STEP;
        }
        // 缩到最小仍超限，返回已知最小的一版
        log.warn("生成图压缩后仍超过 {}KB，已按最小体积转存", TARGET_BYTES / 1024);
        return new Compressed(best, bestExtension, bestImage.getWidth(), bestImage.getHeight());
    }

    /**
     * 组装压缩结果
     */
    private static Compressed result(byte[] bytes, String extension, BufferedImage image)
    {
        return new Compressed(bytes, extension, image.getWidth(), image.getHeight());
    }

    /**
     * 候选是否比当前最优更小
     */
    private static boolean isBetter(byte[] candidate, byte[] best)
    {
        return candidate != null && candidate.length < best.length;
    }

    /**
     * 读取图片，失败返回 null
     */
    private static BufferedImage read(byte[] data)
    {
        try (ByteArrayInputStream input = new ByteArrayInputStream(data))
        {
            return ImageIO.read(input);
        }
        catch (Exception e)
        {
            log.warn("读取生成图失败，跳过压缩：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 按比例缩放
     */
    private static BufferedImage scale(BufferedImage source, float ratio)
    {
        int width = Math.max(1, Math.round(source.getWidth() * ratio));
        int height = Math.max(1, Math.round(source.getHeight() * ratio));
        int type = source.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : source.getType();
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    /**
     * 编码为指定格式，失败返回 null
     */
    private static byte[] encode(BufferedImage image, String format, float quality)
    {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext())
        {
            return null;
        }
        BufferedImage canvas = "jpg".equals(format) ? toRgb(image) : image;
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out))
        {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed())
            {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(canvas, null, null), param);
        }
        catch (Exception e)
        {
            log.warn("生成图压缩编码失败：{}", e.getMessage());
            return null;
        }
        finally
        {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * 去掉透明通道并铺白底，避免 JPEG 出现黑块
     */
    private static BufferedImage toRgb(BufferedImage source)
    {
        if (source.getType() == BufferedImage.TYPE_INT_RGB)
        {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(source, 0, 0, Color.WHITE, null);
        graphics.dispose();
        return rgb;
    }

    /**
     * 压缩结果
     *
     * @param bytes     图片字节
     * @param extension 后缀名
     * @param width     宽度，未解码时为 null
     * @param height    高度，未解码时为 null
     */
    public record Compressed(byte[] bytes, String extension, Integer width, Integer height)
    {
    }
}
