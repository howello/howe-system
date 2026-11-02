package com.howe.ai.storage;

import com.howe.ai.api.AiException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

/** 验证生成图在读取与像素分配之前的安全边界。 */
class AiImageContentGuardTest {
    /** 超大流在上限加一字节处停止且关闭连接，不完整读取响应。 */
    @Test
    void stopsReadingOversizedStream() {
        class Source extends InputStream {
            int read;
            boolean closed;
            @Override public int read() { read++; return 1; }
            @Override public int read(byte[] bytes, int offset, int length) { read += length; return length; }
            @Override public void close() { closed = true; }
        }
        Source source = new Source();
        assertThrows(AiException.class, () -> AiImageContentGuard.readLimited(source));
        assertEquals(AiImageContentGuard.MAX_BYTES + 1, source.read);
        assertTrue(source.closed);
    }

    /** 正常图片保留宽高，非图片内容不能伪装为生成图。 */
    @Test
    void acceptsImageAndRejectsHtml() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertArrayEquals(new int[] { 2, 3 }, AiImageContentGuard.validate(bytes.toByteArray()));
        assertThrows(AiException.class, () -> AiImageContentGuard.validate("<html>error</html>".getBytes()));
    }

    /** 篡改 PNG 的宽高元数据即可拒绝解码，不能等分配像素后再缩图。 */
    @Test
    void rejectsHugeDimensionsBeforeDecoding() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        byte[] png = bytes.toByteArray();
        java.nio.ByteBuffer.wrap(png).putInt(16, 100000).putInt(20, 100000);
        assertThrows(AiException.class, () -> AiImageContentGuard.validate(png));
    }

    /** 没有 WebP 解码器时仍识别容器尺寸，并拒绝超大画布。 */
    @Test
    void checksWebpCanvasWithoutDecoder() {
        byte[] webp = new byte[30];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBPVP8X".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, webp, 8, 8);
        webp[24] = 1;
        webp[27] = 2;
        assertArrayEquals(new int[] { 2, 3 }, AiImageContentGuard.validate(webp));
        webp[26] = 1;
        webp[29] = 1;
        assertThrows(AiException.class, () -> AiImageContentGuard.validate(webp));
    }
}
