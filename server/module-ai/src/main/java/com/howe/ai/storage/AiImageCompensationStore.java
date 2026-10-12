package com.howe.ai.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.howe.common.config.YmlConfig;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 生成图转存补偿存储
 *
 * <p>转存失败时把「已压缩字节 + 目标对象键」暂存下来：任务详情放 Redis（可扫描、带过期），
 * 字节放本地挂载目录（避免把大对象塞进 Redis，也避免撑大数据库）。定时任务据此重传，
 * 成功或放弃后一并清理。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImageCompensationStore
{
    /** 待重传任务索引：ZSet，member=taskId，score=下次重试时间戳 */
    private static final String PENDING_KEY = "ai:transfer:pending";

    /** 单个任务详情键前缀 */
    private static final String TASK_KEY_PREFIX = "ai:transfer:task:";

    /** 字节暂存子目录 */
    private static final String DIR_NAME = "ai-transfer";

    /** 暂存文件后缀 */
    private static final String FILE_SUFFIX = ".bin";

    private final RedisCache redisCache;

    /**
     * 入队一条待补偿任务
     *
     * @param objectKey     完整对象键
     * @param data          已压缩的图片字节
     * @param contentType   Content-Type
     * @param expireMinutes 任务保留时长（分钟）
     * @param error         本次失败原因
     */
    public void enqueue(String objectKey, byte[] data, String contentType, int expireMinutes, String error)
    {
        String taskId = IdUtil.fastSimpleUUID();
        long now = System.currentTimeMillis();
        AiTransferTask task = new AiTransferTask();
        task.setTaskId(taskId);
        task.setObjectKey(objectKey);
        task.setContentType(contentType);
        task.setFile(taskId + FILE_SUFFIX);
        task.setAttempt(0);
        task.setCreateTime(now);
        task.setExpireTime(now + TimeUnit.MINUTES.toMillis(expireMinutes));
        task.setLastError(error);
        try
        {
            FileUtil.writeBytes(data, fileOf(task).getAbsolutePath());
        }
        catch (Exception e)
        {
            log.error("暂存待补偿图片失败，放弃补偿：objectKey={}", objectKey, e);
            return;
        }
        save(task);
        redisCache.zAdd(PENDING_KEY, taskId, now);
        log.warn("图片转存失败已入补偿队列：objectKey={}, taskId={}, error={}", objectKey, taskId, error);
    }

    /**
     * 取一批到期的补偿任务（已过滤过期与详情键缺失）
     *
     * @param limit 最多取多少条
     * @return 待重传任务
     */
    public List<AiTransferTask> dueTasks(int limit)
    {
        Set<String> ids = redisCache.zRangeByScore(PENDING_KEY, 0, System.currentTimeMillis(), limit);
        List<AiTransferTask> tasks = new ArrayList<>(ids.size());
        for (String id : ids)
        {
            AiTransferTask task = find(id);
            if (task == null)
            {
                // 详情键已过期，顺手清掉索引
                redisCache.zRemove(PENDING_KEY, id);
                continue;
            }
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 按 ID 读取任务详情
     *
     * @param taskId 任务 ID
     * @return 任务；不存在时返回 null
     */
    public AiTransferTask find(String taskId)
    {
        return redisCache.getCacheObject(TASK_KEY_PREFIX + taskId);
    }

    /**
     * 读取暂存字节
     *
     * @param task 任务
     * @return 图片字节；文件缺失时返回 null
     */
    public byte[] readBytes(AiTransferTask task)
    {
        File file = fileOf(task);
        if (!file.isFile())
        {
            return null;
        }
        try
        {
            return FileUtil.readBytes(file);
        }
        catch (Exception e)
        {
            log.warn("读取待补偿图片失败：{}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 重传成功，清理任务
     *
     * @param task 任务
     */
    public void markSuccess(AiTransferTask task)
    {
        discard(task);
    }

    /**
     * 重传失败，累计次数并重排下次重试
     *
     * @param task  任务
     * @param error 失败原因
     */
    public void markRetry(AiTransferTask task, String error)
    {
        task.setAttempt(task.getAttempt() + 1);
        task.setLastError(error);
        save(task);
        // 退避：30s × 次数，封顶 10 分钟
        long delay = Math.min(TimeUnit.MINUTES.toMillis(10), TimeUnit.SECONDS.toMillis(30L * task.getAttempt()));
        redisCache.zAdd(PENDING_KEY, task.getTaskId(), System.currentTimeMillis() + delay);
    }

    /**
     * 放弃并清理任务
     *
     * @param task 任务
     */
    public void discard(AiTransferTask task)
    {
        redisCache.zRemove(PENDING_KEY, task.getTaskId());
        redisCache.deleteObject(TASK_KEY_PREFIX + task.getTaskId());
        try
        {
            FileUtil.del(fileOf(task));
        }
        catch (Exception e)
        {
            log.warn("删除待补偿图片暂存文件失败：{}", task.getFile(), e);
        }
    }

    /**
     * 写入任务详情，TTL 取剩余存活时间
     */
    private void save(AiTransferTask task)
    {
        long remainMs = task.getExpireTime() - System.currentTimeMillis();
        if (remainMs <= 0)
        {
            return;
        }
        redisCache.setCacheObject(TASK_KEY_PREFIX + task.getTaskId(), task,
                (int) TimeUnit.MILLISECONDS.toSeconds(remainMs), TimeUnit.SECONDS);
    }

    /**
     * 暂存文件路径
     */
    private File fileOf(AiTransferTask task)
    {
        return FileUtil.file(cacheDir() + "/" + DIR_NAME + "/" + task.getFile());
    }

    /**
     * 缓存根目录：未配置时依次回退到上传目录、系统临时目录
     */
    private String cacheDir()
    {
        String dir = YmlConfig.getCacheDir();
        if (StringUtils.isEmpty(dir))
        {
            dir = YmlConfig.getProfile();
        }
        if (StringUtils.isEmpty(dir))
        {
            dir = System.getProperty("java.io.tmpdir");
        }
        return dir;
    }
}
