package com.howe.ai.task;

import cn.hutool.core.io.IoUtil;
import com.howe.ai.persistence.domain.AiImageAsset;
import com.howe.ai.persistence.mapper.AiImageAssetMapper;
import com.howe.ai.storage.AiImageCompensationStore;
import com.howe.ai.storage.AiTransferTask;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.storage.StorageService;
import com.howe.common.utils.ConfigUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 生成图转存补偿任务
 *
 * <p>每分钟扫一次待重传队列，把此前「已生成但上传失败」的图片重新上传到对象存储。
 * 成功后业务侧先前写入的预写地址随之生效，无需回填；超过次数或超过保留时长的任务直接放弃
 * （接受业务里留下一个 404 地址）。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImageCompensateTask
{
    /** 单批最多处理条数，避免一次调度占用过久 */
    private static final int BATCH = 20;

    private final AiImageCompensationStore compensationStore;

    private final StorageService storageService;

    private final AiImageAssetMapper aiImageAssetMapper;

    /**
     * 重传待补偿的生成图（每分钟一次；开关关闭时跳过）
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void compensate()
    {
        if (!ConfigUtils.getBoolean(ConfigConstants.AI_IMAGE_TRANSFER_COMPENSATE_ENABLED, true))
        {
            return;
        }
        int maxAttempt = Math.max(1,
                ConfigUtils.getInt(ConfigConstants.AI_IMAGE_TRANSFER_COMPENSATE_MAX_ATTEMPT, 30));
        List<AiTransferTask> tasks = compensationStore.dueTasks(BATCH);
        if (tasks.isEmpty())
        {
            return;
        }
        for (AiTransferTask task : tasks)
        {
            try
            {
                retry(task, maxAttempt);
            }
            catch (Exception e)
            {
                log.warn("补偿重传任务异常：taskId={}", task.getTaskId(), e);
            }
        }
    }

    /**
     * 单条重传：读暂存字节 → 上传 → 记录资产并清理；失败累计次数重排
     */
    private void retry(AiTransferTask task, int maxAttempt)
    {
        if (System.currentTimeMillis() > task.getExpireTime() || task.getAttempt() >= maxAttempt)
        {
            log.warn("补偿重传放弃：objectKey={}, attempt={}", task.getObjectKey(), task.getAttempt());
            compensationStore.discard(task);
            return;
        }
        byte[] data = compensationStore.readBytes(task);
        if (data == null)
        {
            log.warn("补偿重传放弃（暂存文件缺失）：objectKey={}", task.getObjectKey());
            compensationStore.discard(task);
            return;
        }
        try
        {
            storageService.store(task.getObjectKey(), () -> IoUtil.toStream(data), data.length, task.getContentType());
            recordAsset(task, data.length);
            compensationStore.markSuccess(task);
            log.info("补偿重传成功：objectKey={}, attempt={}", task.getObjectKey(), task.getAttempt());
        }
        catch (Exception e)
        {
            log.warn("补偿重传失败（第 {} 次）：objectKey={}，原因：{}",
                    task.getAttempt() + 1, task.getObjectKey(), e.getMessage());
            compensationStore.markRetry(task, e.getMessage());
        }
    }

    /**
     * 记录资产，失败只记日志（图已上传，不影响结果）
     */
    private void recordAsset(AiTransferTask task, long size)
    {
        try
        {
            AiImageAsset asset = new AiImageAsset();
            asset.setObjectKey(task.getObjectKey());
            asset.setUrl(storageService.urlOf(task.getObjectKey()));
            asset.setSizeBytes(size);
            aiImageAssetMapper.insertAiImageAsset(asset);
        }
        catch (Exception e)
        {
            log.warn("补偿重传成功但写资产记录失败：objectKey={}", task.getObjectKey(), e);
        }
    }
}
