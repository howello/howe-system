package com.howe.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可重复打开的内容源
 *
 * <p>
 * 存储实现拿到的不再是单个 {@link InputStream}，而是「怎么取流」：AWS SDK 在重试或
 * 重新签名时需要二次读取请求体，而 Tomcat 交给我们的 multipart 流是
 * {@code sun.nio.ch.ChannelInputStream}（{@code Files.newInputStream} 的产物），
 * 既不支持 mark/reset 也不被 SDK 识别为文件流，二次读取会直接抛
 * {@code Content input stream does not support mark/reset}。
 * 交出内容源后，每次重试都能重新打开一份，无需把整个文件缓冲进内存。
 * </p>
 *
 * @author howe
 */
@FunctionalInterface
public interface ContentSource
{
    /**
     * 打开一个新的输入流
     *
     * <p>
     * 每次调用都必须返回从头开始的独立流，调用方负责关闭。
     * </p>
     *
     * @return 输入流
     * @throws IOException 打开失败
     */
    InputStream open() throws IOException;
}
