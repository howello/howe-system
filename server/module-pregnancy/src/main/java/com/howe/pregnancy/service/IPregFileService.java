package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 私有原图服务
 *
 * @author howe
 */
public interface IPregFileService {

    /**
     * 上传原图
     *
     * @param file      原文件
     * @param requestId 请求 ID
     * @return 文件元数据（TEMP）
     */
    FileVO upload(MultipartFile file, String requestId);

    /**
     * 查询文件元数据
     *
     * @param id 文件 ID
     * @return 元数据
     */
    FileVO metadata(Long id);

    /**
     * 读取文件内容
     *
     * @param id 文件 ID
     * @return 内容流与元数据
     */
    FileContent content(Long id);

    /**
     * 删除未绑定的文件
     *
     * @param id 文件 ID
     */
    void delete(Long id);

    /**
     * 按所有者查询已绑定文件
     *
     * @param ownerType 业务类型
     * @param ownerId   业务 ID
     * @return 文件列表
     */
    List<FileVO> listByOwner(String ownerType, Long ownerId);

    /**
     * 把上传文件绑定到业务资源
     *
     * <p>顺序即展示顺序；文件必须属于当前上传者或为孕期管理员可管理的 TEMP，
     * 且一个文件只能属于一个业务资源。</p>
     *
     * @param ownerType 业务类型
     * @param ownerId   业务 ID
     * @param fileIds   文件 ID 列表，按顺序
     */
    void bind(String ownerType, Long ownerId, List<Long> fileIds);

    /**
     * 解除某业务资源的全部文件绑定（转入 ORPHAN）
     *
     * @param ownerType 业务类型
     * @param ownerId   业务 ID
     */
    void detachAll(String ownerType, Long ownerId);

    /**
     * 文件内容
     *
     * @param inputStream 内容流
     * @param mimeType    MIME 类型
     * @param sizeBytes   字节数
     * @param fileName    原始文件名
     */
    record FileContent(java.io.InputStream inputStream, String mimeType, long sizeBytes, String fileName) {
    }
}
