package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.vo.ExportVO;

import java.util.List;

/**
 * 导出服务
 *
 * <p>有读取权限的账号可申请自己的 ZIP 导出；仅申请者且仍有孕期读取权限时可下载。
 * 结果默认 READY 后 24 小时到期，过期返回 {@code EXPORT_EXPIRED}。</p>
 *
 * @author howe
 */
public interface IPregExportService {

    /**
     * 申请导出
     *
     * @param body 请求
     * @return 任务
     */
    ExportVO create(com.howe.pregnancy.domain.dto.ExportCreateBody body);

    /**
     * 查询申请者自己的导出任务
     *
     * @return 任务列表
     */
    List<ExportVO> list();

    /**
     * 查询导出详情
     *
     * @param id 任务 ID
     * @return 任务
     */
    ExportVO get(Long id);

    /**
     * 读取导出 ZIP
     *
     * @param id 任务 ID
     * @return 内容与元数据
     */
    ExportContent content(Long id);

    /**
     * 构建导出（由任务执行器调用）
     *
     * @param taskId     任务 ID
     * @param leaseToken 租约令牌
     * @return 是否成功
     */
    boolean build(Long taskId, String leaseToken);

    /**
     * 导出内容
     *
     * @param inputStream 内容流
     * @param fileName    文件名
     * @param sizeBytes   字节数
     */
    record ExportContent(java.io.InputStream inputStream, String fileName, long sizeBytes) {
    }
}
