package com.howe.share.storage;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.common.config.YmlConfig;
import com.howe.common.exception.ServiceException;
import com.howe.common.storage.StorageService;
import com.howe.common.storage.StoredObject;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 分享文件存储门面
 *
 * <p>按 {@link ShareLink#getStorageType()} 把字节读写分发到本地磁盘或 R2，
 * 业务层不关心落点。本地落 {@code {cacheDir}/share/{code}/{fileName}}，
 * R2 落对象键 {@code share/{yyyyMMdd}/{code}/{fileName}}。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareStorageService {

    /** 分享文件在本地缓存目录 / R2 下的根目录名 */
    private static final String SHARE_DIR = "share";

    private final StorageService storageService;

    /**
     * 写入文件字节
     *
     * @param link  分享记录（已含 storageType / code / fileName / contentType）
     * @param bytes 文件字节
     * @return 实际使用的存储地址（本地相对路径或 R2 对象键）
     */
    public String store(ShareLink link, byte[] bytes) {
        String fileName = safeFileName(link.getFileName());
        if (ShareConstants.STORAGE_LOCAL.equals(link.getStorageType())) {
            String relPath = SHARE_DIR + "/" + link.getCode() + "/" + fileName;
            File target = new File(cacheRoot(), relPath);
            FileUtil.mkParentDirs(target);
            FileUtil.writeBytes(bytes, target);
            return relPath;
        }
        String key = SHARE_DIR + "/" + DateUtil.format(DateUtil.date(), "yyyyMMdd")
                + "/" + link.getCode() + "/" + fileName;
        storageService.store(key, () -> IoUtil.toStream(bytes), bytes.length, link.getContentType());
        return key;
    }

    /**
     * 打开文件字节流（调用方负责关闭）
     *
     * @param link 分享记录
     * @return 读取结果；文件不存在时抛业务异常
     */
    public StoredObject open(ShareLink link) {
        return open(link.getStorageType(), link.getStorageLocation(), link.getContentType(), link.getFileName());
    }

    /**
     * 打开文件字节流（调用方负责关闭）
     *
     * @param storageType     存储位置（0本地 1R2）
     * @param storageLocation 存储地址
     * @param contentType     MIME 类型
     * @param fileName        文件名
     * @return 读取结果；文件不存在时抛业务异常
     */
    public StoredObject open(String storageType, String storageLocation, String contentType, String fileName) {
        if (ShareConstants.STORAGE_LOCAL.equals(storageType)) {
            File file = new File(cacheRoot(), storageLocation);
            if (!file.exists()) {
                throw new ServiceException("文件不存在或已被清理");
            }
            try {
                return new StoredObject(storageLocation, file.getName(), contentType, file.length(),
                        new FileInputStream(file));
            } catch (IOException e) {
                throw new ServiceException("读取本地缓存文件失败：" + e.getMessage());
            }
        }
        StoredObject object = storageService.fetch(storageLocation);
        if (object == null) {
            throw new ServiceException("文件不存在或已被清理");
        }
        return object;
    }

    /**
     * 删除文件字节（本地文件或 R2 对象）
     *
     * @param link 分享记录
     */
    public void remove(ShareLink link) {
        if (StrUtil.isBlank(link.getStorageLocation())) {
            return;
        }
        try {
            if (ShareConstants.STORAGE_LOCAL.equals(link.getStorageType())) {
                File file = new File(cacheRoot(), link.getStorageLocation());
                if (file.exists()) {
                    FileUtil.del(file);
                }
                File parent = file.getParentFile();
                if (parent != null && parent.exists() && FileUtil.isEmpty(parent)) {
                    FileUtil.del(parent);
                }
            } else {
                storageService.remove(link.getStorageLocation());
            }
        } catch (Exception e) {
            log.warn("删除分享文件失败：linkId={}, location={}", link.getLinkId(), link.getStorageLocation(), e);
        }
    }

    /**
     * 取本地缓存根目录
     *
     * @return 目录
     */
    public File cacheRoot() {
        String dir = YmlConfig.getCacheDir();
        if (StrUtil.isBlank(dir)) {
            dir = YmlConfig.getProfile();
        }
        if (StrUtil.isBlank(dir)) {
            dir = System.getProperty("java.io.tmpdir");
        }
        return new File(dir);
    }

    /**
     * 取本地分享文件目录（用于残留清理）
     *
     * @return 目录
     */
    public File shareLocalDir() {
        return new File(cacheRoot(), SHARE_DIR);
    }

    /**
     * 过滤文件名中的路径分隔符，防目录穿越
     */
    private String safeFileName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "file";
        }
        String name = fileName.replace("\\", "/");
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.replace("..", "_");
        return name.isEmpty() ? "file" : name;
    }
}
