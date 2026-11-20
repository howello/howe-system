package com.howe.pregnancy.service.impl;

import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregFile;
import com.howe.pregnancy.domain.vo.FileVO;
import com.howe.pregnancy.mapper.PregFileMapper;
import com.howe.pregnancy.service.IPregFileService;
import com.howe.pregnancy.storage.PrivateFileStorage;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * 私有原图服务实现
 *
 * <p>上传登记为 TEMP；关联时锁文件行并校验归属；解绑转 ORPHAN，保留期后由
 * 清理任务领取为 DELETING 再删除字节。文件字节不可变，替换图片产生新文件 ID。</p>
 *
 * <p>支持 JPEG、PNG、WebP 原文件，单文件上限与每条记录图片数由配置控制，
 * 明确拒绝不支持的格式与超限文件，不做自动转码。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PregFileServiceImpl implements IPregFileService {

    /** 存储键日期目录格式 */
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    private final PregFileMapper fileMapper;

    private final PrivateFileStorage storage;

    private final PregnancyAccessGuard accessGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO upload(MultipartFile file, String requestId) {
        accessGuard.requireWritable();
        if (file == null || file.isEmpty()) {
            throw PregnancyException.invalid("请选择要上传的原文件");
        }
        long maxBytes = PregnancyConfig.fileMaxBytes();
        if (file.getSize() > maxBytes) {
            throw PregnancyException.fileTooLarge(maxBytes);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw PregnancyException.invalid("读取上传文件失败");
        }
        if (bytes.length > maxBytes) {
            throw PregnancyException.fileTooLarge(maxBytes);
        }
        String sha256 = sha256(bytes);
        String mimeType = detectMime(bytes);
        String extension = extensionOf(mimeType);
        String originalName = safeName(file.getOriginalFilename(), extension);
        Long uploader = accessGuard.currentUserId();
        String idemKey = requestId == null ? null : requestId.trim();
        if (idemKey != null && !idemKey.isEmpty()) {
            PregFile existing = fileMapper.selectByRequest(uploader, idemKey);
            if (existing != null) {
                if (!sha256.equals(existing.getSha256()) || !originalName.equals(existing.getOriginalName())) {
                    throw PregnancyException.idempotencyConflict(idemKey);
                }
                if (PregnancyConstants.FILE_DELETING.equals(existing.getState())) {
                    throw PregnancyException.requestConsumed(idemKey);
                }
                return toVO(existing);
            }
        }
        String storageKey = KEY_DATE.format(PregnancyTimeUtils.now()) + "/"
                + UUID.randomUUID().toString().replace("-", "") + extension;

        try (InputStream input = new ByteArrayInputStream(bytes)) {
            storage.write(storageKey, input);
        } catch (IOException e) {
            log.error("写入孕期私有文件失败", e);
            throw PregnancyException.invalid("保存原文件失败，请重试");
        }

        PregFile record = new PregFile();
        record.setUploaderUserId(uploader);
        record.setOriginalName(originalName);
        record.setStorageKey(storageKey);
        record.setMimeType(mimeType);
        record.setSizeBytes((long) bytes.length);
        record.setSha256(sha256);
        int[] size = imageSize(bytes);
        record.setWidth(size[0] > 0 ? size[0] : null);
        record.setHeight(size[1] > 0 ? size[1] : null);
        record.setState(PregnancyConstants.FILE_TEMP);
        record.setRequestId(idemKey == null || idemKey.isEmpty() ? null : idemKey);
        try {
            fileMapper.insert(record);
        } catch (RuntimeException e) {
            // 登记失败时补偿删除已落盘字节
            storage.delete(storageKey);
            throw e;
        }
        return toVO(fileMapper.selectById(record.getId()));
    }

    @Override
    public FileVO metadata(Long id) {
        Set<String> roles = accessGuard.requireReadable();
        PregFile file = requireVisible(id, roles);
        return toVO(file);
    }

    @Override
    public FileContent content(Long id) {
        Set<String> roles = accessGuard.requireReadable();
        PregFile file = requireVisible(id, roles);
        if (!storage.exists(file.getStorageKey())) {
            log.error("孕期私有文件字节缺失：id={}", id);
            throw PregnancyException.notFound();
        }
        try {
            return new FileContent(storage.open(file.getStorageKey()), file.getMimeType(),
                    file.getSizeBytes(), file.getOriginalName());
        } catch (IOException e) {
            log.error("读取孕期私有文件失败：id={}", id, e);
            throw PregnancyException.notFound();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Set<String> roles = accessGuard.requireWritable();
        PregFile file = fileMapper.selectByIdForUpdate(id);
        if (file == null) {
            throw PregnancyException.notFound();
        }
        if (PregnancyConstants.FILE_BOUND.equals(file.getState())) {
            throw PregnancyException.resourceInUse("文件已绑定业务记录，请先在业务中解除");
        }
        if (!PregnancyConstants.FILE_TEMP.equals(file.getState())
                && !PregnancyConstants.FILE_ORPHAN.equals(file.getState())) {
            throw PregnancyException.invalidState("文件当前状态不可删除");
        }
        assertTempManageable(file, roles);
        if (fileMapper.markDeleting(id) == 0) {
            throw PregnancyException.invalidState("文件已被处理，请刷新");
        }
        storage.delete(file.getStorageKey());
        fileMapper.deleteById(id);
    }

    @Override
    public List<FileVO> listByOwner(String ownerType, Long ownerId) {
        accessGuard.requireReadable();
        return fileMapper.selectByOwner(ownerType, ownerId).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(String ownerType, Long ownerId, List<Long> fileIds) {
        Set<String> roles = accessGuard.requireWritable();
        if (fileIds == null) {
            return;
        }
        int maxPerOwner = PregnancyConfig.fileMaxPerOwner();
        if (fileIds.size() > maxPerOwner) {
            throw PregnancyException.invalid("每条记录最多绑定 " + maxPerOwner + " 张原图");
        }
        if (new java.util.HashSet<>(fileIds).size() != fileIds.size()) {
            throw PregnancyException.invalid("文件列表不能重复");
        }
        List<PregFile> locked = new ArrayList<>();
        for (Long fileId : fileIds) {
            PregFile file = fileMapper.selectByIdForUpdate(fileId);
            if (file == null) {
                throw PregnancyException.notFound();
            }
            if (PregnancyConstants.FILE_DELETING.equals(file.getState())) {
                throw PregnancyException.invalidState("文件正在回收，不能绑定");
            }
            if (PregnancyConstants.FILE_BOUND.equals(file.getState())
                    && !(ownerType.equals(file.getOwnerType()) && ownerId.equals(file.getOwnerId()))) {
                throw PregnancyException.resourceInUse("文件已属于其他业务记录");
            }
            assertTempManageable(file, roles);
            locked.add(file);
        }
        // 先解除本资源原有绑定，再按传入顺序重建
        detachBound(ownerType, ownerId);
        int order = 0;
        for (PregFile file : locked) {
            if (fileMapper.bind(file.getId(), ownerType, ownerId, order++) == 0) {
                throw PregnancyException.invalidState("文件状态已变化，请刷新后重试");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void detachAll(String ownerType, Long ownerId) {
        detachBound(ownerType, ownerId);
    }

    /**
     * 解除绑定并转入 ORPHAN
     *
     * @param ownerType 业务类型
     * @param ownerId   业务 ID
     */
    private void detachBound(String ownerType, Long ownerId) {
        OffsetDateTime reclaimAt = PregnancyTimeUtils.now()
                .plusHours(PregnancyConfig.fileRetainHours());
        fileMapper.detachByOwner(ownerType, ownerId,
                reclaimAt.toLocalDateTime());
    }

    /**
     * 校验 TEMP 或未绑定文件的可见性
     *
     * <p>TEMP 仅上传者或孕期管理员可管理；已绑定文件对全部孕期读取角色可见。</p>
     *
     * @param file  文件
     * @param roles 当前角色
     */
    private void assertTempManageable(PregFile file, Set<String> roles) {
        if (PregnancyConstants.FILE_BOUND.equals(file.getState())) {
            return;
        }
        Long current = accessGuard.currentUserId();
        boolean owner = current.equals(file.getUploaderUserId());
        boolean manager = roles.contains(PregnancyConstants.ROLE_MANAGER);
        if (!owner && !manager) {
            throw PregnancyException.notFound();
        }
    }

    /**
     * 校验当前账号可读取该文件
     *
     * @param id    文件 ID
     * @param roles 当前角色
     * @return 文件
     */
    private PregFile requireVisible(Long id, Set<String> roles) {
        PregFile file = fileMapper.selectById(id);
        if (file == null) {
            throw PregnancyException.notFound();
        }
        if (PregnancyConstants.FILE_DELETING.equals(file.getState())) {
            throw PregnancyException.notFound();
        }
        assertTempManageable(file, roles);
        return file;
    }

    /**
     * 转换展示对象
     *
     * @param file 文件
     * @return 展示对象
     */
    private FileVO toVO(PregFile file) {
        FileVO vo = new FileVO();
        vo.setId(String.valueOf(file.getId()));
        vo.setState(file.getState());
        vo.setOriginalName(file.getOriginalName());
        vo.setMimeType(file.getMimeType());
        vo.setSizeBytes(file.getSizeBytes() == null ? null : String.valueOf(file.getSizeBytes()));
        vo.setSha256(file.getSha256());
        vo.setWidth(file.getWidth());
        vo.setHeight(file.getHeight());
        vo.setOwnerType(file.getOwnerType());
        vo.setOwnerId(file.getOwnerId() == null ? null : String.valueOf(file.getOwnerId()));
        vo.setSortOrder(file.getSortOrder());
        vo.setContentUrl("/pregnancy/files/" + file.getId() + "/content");
        return vo;
    }

    /**
     * 按原始字节签名识别 JPEG、PNG 和 WebP，避免客户端声明覆盖实际类型。
     *
     * @param bytes 原文件
     * @return 图片 MIME
     */
    private String detectMime(byte[] bytes) {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= png.length && Arrays.equals(bytes, 0, png.length, png, 0, png.length)) {
            return "image/png";
        }
        if (bytes.length >= 20 && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))
                && Set.of("VP8 ", "VP8L", "VP8X").contains(new String(bytes, 12, 4, StandardCharsets.US_ASCII))) {
            return "image/webp";
        }
        throw PregnancyException.unsupportedFileType("未知格式");
    }

    /**
     * 取扩展名
     *
     * @param mimeType MIME
     * @return 扩展名
     */
    private String extensionOf(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /**
     * 取安全文件名
     *
     * @param original  原始文件名
     * @param extension 扩展名
     * @return 安全文件名
     */
    private String safeName(String original, String extension) {
        String name = original == null ? "image" : original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", "").trim();
        if (name.isEmpty()) {
            name = "image" + extension;
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    /**
     * 计算 SHA-256
     *
     * @param bytes 字节
     * @return 十六进制摘要
     */
    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 读取图片尺寸
     *
     * @param bytes 字节
     * @return 宽度与高度，失败返回 0,0
     */
    private int[] imageSize(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return new int[] {0, 0};
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return new int[] {0, 0};
        }
    }

    /**
     * 工具：读取文件大小（供清理任务复用）
     *
     * @param key 存储键
     * @return 字节数
     */
    public long sizeOf(String key) {
        if (!storage.exists(key)) {
            return 0L;
        }
        try {
            return Files.size(storage.resolve(key));
        } catch (IOException e) {
            return 0L;
        }
    }
}
