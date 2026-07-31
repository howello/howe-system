package com.howe.common.storage;

import cn.hutool.core.io.IoUtil;

import java.io.Closeable;
import java.io.InputStream;

/**
 * 已存储文件的读取结果
 *
 * <p>
 * 下载时用：不同存储实现拿到的元信息不一样（本地是文件属性，R2 是响应头），
 * 统一成这个结构交给控制层写响应。持有的是未读取的流，用完必须关闭，
 * 因此实现了 {@link Closeable}，配合 try-with-resources 使用。
 * </p>
 *
 * @param key 实际使用的对象键或相对路径，仅用于日志排查
 * @param fileName 文件名，不含目录
 * @param contentType MIME 类型，可能为空
 * @param size 字节数，小于 0 表示未知
 * @param stream 内容流，由调用方负责关闭
 * @author howe
 */
public record StoredObject(String key, String fileName, String contentType, long size, InputStream stream)
        implements Closeable
{
    @Override
    public void close()
    {
        IoUtil.close(stream);
    }
}
