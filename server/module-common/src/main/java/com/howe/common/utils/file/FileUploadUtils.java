package com.howe.common.utils.file;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import org.springframework.web.multipart.MultipartFile;
import com.howe.common.config.YmlConfig;
import com.howe.common.exception.file.FileNameLengthLimitExceededException;
import com.howe.common.exception.file.FileSizeLimitExceededException;
import com.howe.common.exception.file.InvalidExtensionException;
import com.howe.common.storage.StorageService;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.spring.SpringUtils;
import com.howe.common.utils.uuid.IdUtils;
import com.howe.common.utils.uuid.Seq;

/**
 * 文件上传工具类
 *
 * <p>
 * 只负责校验与命名，字节落到哪里交由 {@link StorageService} 决定（默认 Cloudflare R2）。
 * 返回值不再是固定的 /profile 相对路径：R2 模式下是图床完整 URL，local 模式下才是 /profile 路径，
 * 调用方需要拼接站点地址时请用 {@link #toAbsoluteUrl(String, String)}。
 * </p>
 *
 * @author howe
 */
public class FileUploadUtils {
    /**
     * 默认大小 50M
     */
    public static final long DEFAULT_MAX_SIZE = 50 * 1024 * 1024L;

    /**
     * 默认的文件名最大长度 100
     */
    public static final int DEFAULT_FILE_NAME_LENGTH = 100;

    /**
     * 默认上传的地址
     */
    private static String defaultBaseDir = YmlConfig.getProfile();

    public static void setDefaultBaseDir(String defaultBaseDir) {
        FileUploadUtils.defaultBaseDir = defaultBaseDir;
    }

    public static String getDefaultBaseDir() {
        return defaultBaseDir;
    }

    /**
     * 以默认配置进行文件上传
     *
     * @param file 上传的文件
     * @return 文件名称
     * @throws Exception
     */
    public static final String upload(MultipartFile file) throws IOException {
        try {
            return upload(getDefaultBaseDir(), file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * 根据文件路径上传
     *
     * @param baseDir 相对应用的基目录
     * @param file 上传的文件
     * @return 文件名称
     * @throws IOException
     */
    public static final String upload(String baseDir, MultipartFile file) throws IOException {
        try {
            return upload(baseDir, file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * 文件上传
     *
     * @param baseDir 相对应用的基目录
     * @param file 上传的文件
     * @param allowedExtension 上传文件类型
     * @return 返回上传成功的文件名
     * @throws FileSizeLimitExceededException 如果超出最大大小
     * @throws FileNameLengthLimitExceededException 文件名太长
     * @throws IOException 比如读写文件出错时
     * @throws InvalidExtensionException 文件校验异常
     */
    public static final String upload(String baseDir, MultipartFile file, String[] allowedExtension)
            throws FileSizeLimitExceededException, IOException, FileNameLengthLimitExceededException,
            InvalidExtensionException {
        return upload(baseDir, file, allowedExtension, false);
    }

    /**
     * 文件上传
     *
     * @param baseDir 相对应用的基目录
     * @param file 上传的文件
     * @param useCustomNaming 系统自定义文件名
     * @param allowedExtension 上传文件类型
     * @return 返回上传成功的文件名
     * @throws FileSizeLimitExceededException 如果超出最大大小
     * @throws FileNameLengthLimitExceededException 文件名太长
     * @throws IOException 比如读写文件出错时
     * @throws InvalidExtensionException 文件校验异常
     */
    public static final String upload(String baseDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming)
            throws FileSizeLimitExceededException, IOException, FileNameLengthLimitExceededException,
            InvalidExtensionException {
        return uploadTo(toBizDir(baseDir), file, allowedExtension, useCustomNaming);
    }

    /**
     * 文件上传（直接指定业务目录，推荐新代码使用）
     *
     * @param bizDir 业务目录，形如 upload / avatar，不带前后斜杠
     * @param file 上传的文件
     * @param allowedExtension 允许的文件类型
     * @param useCustomNaming 是否使用 UUID 重命名
     * @return 可访问的文件地址
     * @throws FileSizeLimitExceededException 如果超出最大大小
     * @throws FileNameLengthLimitExceededException 文件名太长
     * @throws IOException 读取上传流出错
     * @throws InvalidExtensionException 文件校验异常
     */
    public static final String uploadTo(String bizDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming)
            throws FileSizeLimitExceededException, IOException, FileNameLengthLimitExceededException,
            InvalidExtensionException {
        int fileNameLength = Objects.requireNonNull(file.getOriginalFilename()).length();
        if (fileNameLength > FileUploadUtils.DEFAULT_FILE_NAME_LENGTH) {
            throw new FileNameLengthLimitExceededException(FileUploadUtils.DEFAULT_FILE_NAME_LENGTH);
        }

        assertAllowed(file, allowedExtension);

        String fileName = useCustomNaming ? uuidFilename(file) : extractFilename(file);
        String objectKey = buildObjectKey(bizDir, fileName);
        // 传内容源而非单个流：上传重试需要重新读取内容，MultipartFile 每次都能给出新流
        return storage().store(objectKey, file::getInputStream, file.getSize(), resolveContentType(file));
    }

    /**
     * 存储字节数组
     *
     * @param bizDir 业务目录，形如 import
     * @param objectKey 对象键（相对 bizDir 的路径）
     * @param data 数据
     * @param contentType MIME 类型，可为空
     * @return 可访问的文件地址
     */
    public static final String uploadBytes(String bizDir, String objectKey, byte[] data, String contentType) {
        return storage().store(buildObjectKey(bizDir, objectKey), () -> IoUtil.toStream(data), data.length, contentType);
    }

    /**
     * 编码文件名(日期格式目录 + 原文件名 + 序列值 + 后缀)
     */
    public static final String extractFilename(MultipartFile file) {
        return StringUtils.format("{}/{}_{}.{}", DateUtils.datePath(), FileUtil.mainName(file.getOriginalFilename()), Seq.getId(Seq.uploadSeqType),
                getExtension(file));
    }

    /**
     * 编编码文件名(日期格式目录 + UUID + 后缀)
     */
    public static final String uuidFilename(MultipartFile file) {
        return StringUtils.format("{}/{}.{}", DateUtils.datePath(), IdUtils.fastSimpleUUID(), getExtension(file));
    }

    public static final File getAbsoluteFile(String uploadDir, String fileName) throws IOException {
        return FileUtil.touch(uploadDir + File.separator + fileName);
    }

    /**
     * 拼接对象键
     *
     * @param bizDir 业务目录，形如 upload
     * @param fileName 日期目录 + 文件名，形如 2026/07/30/xxx.png
     * @return 对象键，形如 upload/2026/07/30/xxx.png，统一用正斜杠且不以斜杠开头
     */
    public static final String buildObjectKey(String bizDir, String fileName) {
        String dir = StringUtils.strip(StringUtils.defaultString(bizDir).replace('\\', '/'), "/");
        String name = StringUtils.stripStart(fileName.replace('\\', '/'), "/");
        return dir.isEmpty() ? name : dir + "/" + name;
    }

    /**
     * 把 profile 下的绝对目录还原成业务目录
     *
     * <p>
     * 兼容 {@code YmlConfig.getUploadPath()} 这类返回绝对路径的老调用方式：
     * {@code <profile>/upload} 截掉 profile 前缀后得到 {@code upload}。
     * 传入的本就是相对目录时原样返回。
     * </p>
     */
    public static final String toBizDir(String baseDir) {
        String profile = YmlConfig.getProfile();
        String dir = StringUtils.defaultString(baseDir).replace('\\', '/');
        if (StringUtils.isNotEmpty(profile)) {
            String normalizedProfile = profile.replace('\\', '/');
            if (dir.startsWith(normalizedProfile)) {
                dir = dir.substring(normalizedProfile.length());
            }
        }
        return StringUtils.strip(dir, "/");
    }

    /**
     * 把存储返回的地址转成可直接访问的绝对 URL
     *
     * <p>
     * R2 模式下存储层已经返回完整 URL，原样返回；local 模式返回的是 /profile 相对路径，
     * 需要拼上站点地址。
     * </p>
     *
     * @param siteUrl 站点地址，形如 http://localhost:9527
     * @param location 存储返回的地址
     * @return 绝对 URL
     */
    public static final String toAbsoluteUrl(String siteUrl, String location) {
        if (StringUtils.isEmpty(location) || StringUtils.startsWithIgnoreCase(location, "http://")
                || StringUtils.startsWithIgnoreCase(location, "https://")) {
            return location;
        }
        return StringUtils.stripEnd(StringUtils.defaultString(siteUrl), "/") + location;
    }

    /**
     * 推断上传文件的 MIME 类型
     *
     * <p>
     * 浏览器给的 contentType 偶尔是 application/octet-stream，会导致图片在图床上被当作下载
     * 而不是内联展示，所以这种情况按扩展名兜底。
     * </p>
     */
    private static String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.isNotEmpty(contentType) && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        String byExtension = MimeTypeUtils.getContentType(getExtension(file));
        return StringUtils.isNotEmpty(byExtension) ? byExtension : contentType;
    }

    /**
     * 获取存储服务
     *
     * <p>
     * 延迟到调用时才取 bean：本类是静态工具类，类加载时机早于 Spring 容器就绪。
     * </p>
     */
    private static StorageService storage() {
        return SpringUtils.getBean(StorageService.class);
    }

    /**
     * 文件大小校验
     *
     * @param file 上传的文件
     * @return
     * @throws FileSizeLimitExceededException 如果超出最大大小
     * @throws InvalidExtensionException
     */
    public static final void assertAllowed(MultipartFile file, String[] allowedExtension)
            throws FileSizeLimitExceededException, InvalidExtensionException {
        long size = file.getSize();
        if (size > DEFAULT_MAX_SIZE) {
            throw new FileSizeLimitExceededException(DEFAULT_MAX_SIZE / 1024 / 1024);
        }

        String fileName = file.getOriginalFilename();
        String extension = getExtension(file);
        if (allowedExtension != null && !isAllowedExtension(extension, allowedExtension)) {
            if (allowedExtension == MimeTypeUtils.IMAGE_EXTENSION) {
                throw new InvalidExtensionException.InvalidImageExtensionException(allowedExtension, extension,
                        fileName);
            } else if (allowedExtension == MimeTypeUtils.FLASH_EXTENSION) {
                throw new InvalidExtensionException.InvalidFlashExtensionException(allowedExtension, extension,
                        fileName);
            } else if (allowedExtension == MimeTypeUtils.MEDIA_EXTENSION) {
                throw new InvalidExtensionException.InvalidMediaExtensionException(allowedExtension, extension,
                        fileName);
            } else if (allowedExtension == MimeTypeUtils.VIDEO_EXTENSION) {
                throw new InvalidExtensionException.InvalidVideoExtensionException(allowedExtension, extension,
                        fileName);
            } else {
                throw new InvalidExtensionException(allowedExtension, extension, fileName);
            }
        }
    }

    /**
     * 判断MIME类型是否是允许的MIME类型
     *
     * @param extension
     * @param allowedExtension
     * @return
     */
    public static final boolean isAllowedExtension(String extension, String[] allowedExtension) {
        for (String str : allowedExtension) {
            if (str.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文件名的后缀
     *
     * @param file 表单文件
     * @return 后缀名
     */
    public static final String getExtension(MultipartFile file) {
        String extension = FileUtil.extName(file.getOriginalFilename());
        if (StringUtils.isEmpty(extension)) {
            extension = MimeTypeUtils.getExtension(Objects.requireNonNull(file.getContentType()));
        }
        return extension;
    }
}
