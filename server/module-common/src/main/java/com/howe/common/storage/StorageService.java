package com.howe.common.storage;

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
     * <p>
     * 收的是内容源而不是单个流：上传可能因重试而需要重新读取内容，
     * 详见 {@link ContentSource}。本方法负责关闭自己打开的每一个流。
     * </p>
     *
     * @param key 对象键，形如 upload/2026/07/30/xxx.png，不以斜杠开头
     * @param source 文件内容源，可被重复打开
     * @param size 字节数，小于 0 表示未知
     * @param contentType MIME 类型，可为空
     * @return 可公开访问的地址：R2 返回完整 URL，本地返回 /profile 前缀的相对路径
     */
    String store(String key, ContentSource source, long size, String contentType);

    /**
     * 读取文件
     *
     * <p>
     * 下载走这里而不是直接读磁盘：上传早就全量落到对象存储了，本地盘上根本没有文件。
     * </p>
     *
     * @param location 存储时返回的地址（完整 URL 或 /profile 相对路径），也接受裸对象键
     * @return 文件内容与元信息；文件不存在时返回 {@code null}。调用方必须关闭返回值
     */
    StoredObject fetch(String location);

    /**
     * 删除文件
     *
     * @param location 存储时返回的地址（完整 URL 或 /profile 相对路径），也接受裸对象键
     * @return 是否删除成功；文件本就不存在时返回 false
     */
    boolean remove(String location);
}
