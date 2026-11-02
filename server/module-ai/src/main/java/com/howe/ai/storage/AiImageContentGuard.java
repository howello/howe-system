package com.howe.ai.storage;

import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/** 生成图片的字节与解码尺寸边界，校验发生在分配完整图片之前。 */
@Slf4j
public final class AiImageContentGuard {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_777_216L;

    /** 禁止实例化工具类。 */
    private AiImageContentGuard() {
    }

    /** 有界读取响应流，最多读取上限加一个字节便拒绝，并关闭流。 */
    public static byte[] readLimited(InputStream input) throws IOException {
        if (input == null) throw invalid("图片响应为空");
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - out.size()))) != -1) {
                out.write(buffer, 0, count);
                if (out.size() > MAX_BYTES) throw invalid("生成图超过 20MB，已终止下载");
            }
            return out.toByteArray();
        }
    }

    /** 只读取图片元数据校验尺寸，不解码完整像素；返回宽高。 */
    public static int[] validate(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) throw invalid("图片内容为空或超过 20MB");
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return webpSize(data);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw invalid("图片像素数超过安全上限");
                }
                return new int[] { width, height };
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new AiException(AiErrorCode.TRANSFER_FAILED, "无法读取图片尺寸", e);
        }
    }

    /** 读取 WebP 容器尺寸；JDK 没有解码器时仍可安全转存，避免破坏原有 WebP 支持。 */
    private static int[] webpSize(byte[] data) {
        if (data.length < 25 || !new String(data, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1).equals("RIFF")
                || !new String(data, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1).equals("WEBP")) {
            throw invalid("图片格式不受支持或内容已损坏");
        }
        String chunk = new String(data, 12, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        int width;
        int height;
        if ("VP8X".equals(chunk) && data.length >= 30) {
            width = 1 + littleEndian(data, 24, 3);
            height = 1 + littleEndian(data, 27, 3);
        } else if ("VP8 ".equals(chunk) && data.length >= 30 && (data[23] & 255) == 157 && data[24] == 1 && data[25] == 42) {
            width = littleEndian(data, 26, 2) & 16383;
            height = littleEndian(data, 28, 2) & 16383;
        } else if ("VP8L".equals(chunk) && data[20] == 47) {
            int bits = littleEndian(data, 21, 4);
            width = 1 + (bits & 16383);
            height = 1 + ((bits >>> 14) & 16383);
        } else {
            throw invalid("WebP 图片头不合法");
        }
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) throw invalid("图片像素数超过安全上限");
        return new int[] { width, height };
    }

    /** 读取已校验范围内的小端无符号字段。 */
    private static int littleEndian(byte[] bytes, int offset, int count) {
        int value = 0;
        for (int i = 0; i < count; i++) value |= (bytes[offset + i] & 255) << (8 * i);
        return value;
    }

    /** 构造不允许继续下载或解码的业务异常。 */
    private static AiException invalid(String message) {
        return new AiException(AiErrorCode.TRANSFER_FAILED, message);
    }
}
