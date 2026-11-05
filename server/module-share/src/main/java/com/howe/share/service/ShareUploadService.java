package com.howe.share.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.http.RemoteFileClient;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareUpload;
import com.howe.share.domain.vo.ShareRemoteCheckVO;
import com.howe.share.domain.vo.ShareUploadVO;
import com.howe.share.mapper.ShareUploadMapper;
import com.howe.share.storage.ShareStorageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

/**
 * 本地上传暂存服务
 *
 * <p>上传的文件先落在暂存目录，创建分享时按令牌认领；缓存成功后才删暂存。
 * 令牌明文只返回一次，库里存 SHA-256，避免拖库后能直接冒用。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareUploadService {

    /** 暂存有效期（小时） */
    private static final long EXPIRE_HOURS = 24L;

    /** 单批清理上限 */
    private static final int CLEAN_BATCH = 50;

    private final ShareUploadMapper shareUploadMapper;

    /**
     * 上传文件到暂存
     *
     * @param file    文件
     * @param userId  上传者
     * @return 上传结果（含一次性令牌）
     */
    public ShareUploadVO upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择要上传的文件");
        }
        if (userId == null) {
            throw new ServiceException("上传需要登录");
        }
        long maxSize = maxSize();
        if (file.getSize() > maxSize) {
            throw new ServiceException("文件超过上传上限（" + maxSize + " 字节）");
        }
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "file");
        // 只取文件名部分，防客户端传路径穿越名
        String fileName = Paths.get(originalName).getFileName().toString();
        String ext = ShareStorageRouter.extOf(fileName);
        String token = "u_" + IdUtil.fastSimpleUUID();
        String stagingDir = IdUtil.fastSimpleUUID();
        String stagingLocation = stagingDir + "/" + fileName;
        Path target = Paths.get(stagingRoot(), stagingLocation).normalize();
        if (!target.startsWith(Paths.get(stagingRoot()).normalize())) {
            throw new ServiceException("文件名不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new ServiceException("保存上传文件失败：" + e.getMessage());
        }
        ShareUpload upload = new ShareUpload();
        upload.setTokenHash(DigestUtil.sha256Hex(token));
        upload.setOwnerUserId(userId);
        upload.setFileName(fileName);
        upload.setFileSize(file.getSize());
        upload.setContentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"));
        upload.setFileExt(ext);
        upload.setStagingLocation(stagingLocation);
        upload.setState(ShareConstants.UPLOAD_TEMP);
        upload.setExpireAt(new Date(System.currentTimeMillis() + EXPIRE_HOURS * 3600_000L));
        shareUploadMapper.insertShareUpload(upload);

        ShareUploadVO vo = new ShareUploadVO();
        vo.setUploadToken(token);
        vo.setFileName(fileName);
        vo.setFileSize(file.getSize());
        vo.setFileExt(ext);
        vo.setExpireAt(upload.getExpireAt());
        return vo;
    }

    /**
     * 校验并认领上传
     *
     * @param token  上传令牌
     * @param userId 当前用户
     * @param linkId 分享ID
     * @return 已认领的暂存记录
     */
    public ShareUpload claim(String token, Long userId, Long linkId) {
        if (StrUtil.isBlank(token)) {
            throw new ServiceException("上传令牌不能为空");
        }
        ShareUpload upload = shareUploadMapper.selectByTokenHash(DigestUtil.sha256Hex(token.trim()));
        if (upload == null) {
            throw new ServiceException("上传记录不存在或令牌已失效");
        }
        if (!upload.getOwnerUserId().equals(userId)) {
            throw new ServiceException("该上传不属于当前用户");
        }
        if (!ShareConstants.UPLOAD_TEMP.equals(upload.getState())) {
            throw new ServiceException("该上传已被使用或已过期，请重新上传");
        }
        if (upload.getExpireAt() != null && upload.getExpireAt().before(new Date())) {
            throw new ServiceException("上传已过期，请重新上传");
        }
        int claimed = shareUploadMapper.claim(upload.getUploadId(), userId, linkId, new Date());
        if (claimed == 0) {
            throw new ServiceException("上传认领失败，可能已被其他请求使用，请重新上传");
        }
        upload.setState(ShareConstants.UPLOAD_CLAIMED);
        upload.setLinkId(linkId);
        return upload;
    }

    /**
     * 按主键查询暂存记录
     *
     * @param uploadId 上传ID
     * @return 暂存记录
     */
    public ShareUpload selectById(Long uploadId) {
        return uploadId == null ? null : shareUploadMapper.selectShareUploadById(uploadId);
    }

    /**
     * 标记缓存成功并删除暂存字节
     *
     * @param uploadId 上传ID
     */
    public void markBound(Long uploadId) {
        ShareUpload upload = shareUploadMapper.selectShareUploadById(uploadId);
        if (upload == null) {
            return;
        }
        shareUploadMapper.markBound(uploadId);
        deleteStagingFile(upload);
    }

    /**
     * 校验远程链接
     *
     * <p>与 APP 资源包共用同一套安全与有界下载规则；这里只验证可达与大小，
     * 不解析文件类型（分享支持任意格式）。</p>
     *
     * @param url 远程地址
     * @return 校验结果
     */
    public ShareRemoteCheckVO checkRemote(String url) {
        ShareRemoteCheckVO vo = new ShareRemoteCheckVO();
        java.nio.file.Path temp = null;
        try {
            temp = java.nio.file.Files.createTempFile("share-check-", ".tmp");
            RemoteFileClient.ProbeResult result = RemoteFileClient.download(url, maxSize(), 30000, 3, temp);
            vo.setReachable(result.reachable());
            vo.setFileSize(result.contentLength());
            vo.setFileName(inferFileName(url));
            vo.setCheckedAt(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, new Date()));
            if (!result.reachable()) {
                vo.setReason(result.reason());
            }
            return vo;
        } catch (ServiceException e) {
            vo.setReachable(false);
            vo.setReason(e.getMessage());
            return vo;
        } catch (Exception e) {
            vo.setReachable(false);
            vo.setReason("校验失败：" + e.getMessage());
            return vo;
        } finally {
            if (temp != null) {
                FileUtil.del(temp.toFile());
            }
        }
    }

    /**
     * 从地址推断文件名
     *
     * @param url 地址
     * @return 文件名
     */
    private String inferFileName(String url) {
        try {
            String path = java.net.URI.create(url.trim()).getPath();
            if (StrUtil.isBlank(path)) {
                return "remote-file";
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            return StrUtil.isBlank(name) ? "remote-file" : name;
        } catch (Exception e) {
            return "remote-file";
        }
    }

    /**
     * 清理过期与长期未完成的上传
     *
     * <p>只清理 TERM 已过期与 CLAIMED 超过一天的记录：正在缓存中的文件不能被误删。</p>
     */
    public void cleanupExpired() {
        Date now = new Date();
        List<ShareUpload> temps = shareUploadMapper.selectExpiredTemps(now, CLEAN_BATCH);
        for (ShareUpload upload : temps) {
            shareUploadMapper.markExpired(upload.getUploadId());
            deleteStagingFile(upload);
        }
        // 认领后超过 24 小时仍未 BOUND：分享已删或缓存持续失败
        Date staleBefore = new Date(now.getTime() - EXPIRE_HOURS * 3600_000L);
        for (ShareUpload upload : shareUploadMapper.selectStaleClaimed(staleBefore, CLEAN_BATCH)) {
            shareUploadMapper.markExpired(upload.getUploadId());
            deleteStagingFile(upload);
        }
    }

    /**
     * 删除暂存字节（失败只记日志，下次清理再试）
     *
     * @param upload 暂存记录
     */
    private void deleteStagingFile(ShareUpload upload) {
        if (upload == null || StrUtil.isBlank(upload.getStagingLocation())) {
            return;
        }
        Path path = Paths.get(stagingRoot(), upload.getStagingLocation()).normalize();
        if (!path.startsWith(Paths.get(stagingRoot()).normalize())) {
            return;
        }
        try {
            FileUtil.del(path.toFile());
            // 顺手清理空的目录，避免暂存目录堆积
            FileUtil.del(path.getParent().toFile());
        } catch (Exception e) {
            log.warn("删除暂存文件失败：{}", path, e);
        }
    }

    /**
     * 暂存根目录
     *
     * @return 路径
     */
    private String stagingRoot() {
        return ConfigUtils.getString(ConfigConstants.SHARE_UPLOAD_DIR, FileUtil.getTmpDirPath() + "share-upload");
    }

    /**
     * 上传大小上限
     *
     * @return 字节数
     */
    private long maxSize() {
        int value = ConfigUtils.getInt(ConfigConstants.SHARE_DOWNLOAD_MAX_SIZE, 52428800);
        return value <= 0 ? 52428800L : value;
    }
}
