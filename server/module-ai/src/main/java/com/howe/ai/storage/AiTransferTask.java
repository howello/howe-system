package com.howe.ai.storage;

import lombok.Data;

import java.io.Serializable;

/**
 * 生成图转存补偿任务
 *
 * <p>生成成功但上传对象存储失败时落一条：字节暂存在本地目录（{@link #file}），
 * 任务详情存 Redis。定时任务据此重传，成功后该任务对应的永久地址自然生效。</p>
 *
 * @author howe
 */
@Data
public class AiTransferTask implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 任务 ID，同时是本地暂存文件名 */
    private String taskId;

    /** 完整对象键，形如 ai/2026/10/12/xxx.png，直接传给存储服务 */
    private String objectKey;

    /** 上传时使用的 Content-Type */
    private String contentType;

    /** 本地暂存文件名，形如 {taskId}.bin */
    private String file;

    /** 已重传次数 */
    private int attempt;

    /** 创建时间戳（毫秒） */
    private long createTime;

    /** 过期时间戳（毫秒），超过即放弃 */
    private long expireTime;

    /** 最近一次失败原因 */
    private String lastError;
}
