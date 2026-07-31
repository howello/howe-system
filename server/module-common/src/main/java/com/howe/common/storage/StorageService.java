package com.howe.common.storage;

import java.io.InputStream;

/**
 * 文件存储服务
 *
 * <p>
 * 把「文件字节存到哪里」与「怎么校验、怎么命名」解耦：命名与校验仍由
 * {@code FileUploadUtils} 负责，本接口只接收最终对象键并返回可访问 URL。
 * </p>
 *
 * @author howe
 */
public interface StorageService
{
    /**
     * 存储类型标识，与 howe.storage.type 取值对应
     *
     * @return 类型标识
     */
    String getType();

    /**
     * 存储文件
     *
     * @param key 对象键，形如 upload/2026/07/30/xxx.png，不以斜杠开头
     * @param in 文件输入流，由本方法负责关闭
     * @param size 字节数，小于 0 表示未知
     * @param contentType MIME 类型，可为空
     * @return 可公开访问的地址：R2 返回完整 URL，本地返回 /profile 前缀的相对路径
     */
    String store(String key, InputStream in, long size, String contentType);

    /**
     * 删除文件
     *
     * @param location 存储时返回的地址（完整 URL 或 /profile 相对路径），也接受裸对象键
     * @return 是否删除成功；文件本就不存在时返回 false
     */
    boolean remove(String location);
}
