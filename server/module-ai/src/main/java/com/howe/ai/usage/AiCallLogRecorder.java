package com.howe.ai.usage;

import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.AiImageAsset;
import com.howe.ai.persistence.mapper.AiCallLogMapper;
import com.howe.ai.persistence.mapper.AiImageAssetMapper;
import com.howe.common.utils.spring.SpringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 调用记录落库
 *
 * <p>写库在线程池里做，业务线程不承担数据库 I/O；写库失败只记日志，绝不影响业务返回。</p>
 *
 * <p>返回的调用记录 ID 通过有界等待取回：单行 insert 通常在一毫秒内完成，
 * 等待上限 {@value #WAIT_MS} 毫秒，超时就返回 null 并把写入留在后台继续，
 * 业务响应不会因为数据库变慢而被拖住。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallLogRecorder
{
    /** 取回调用记录 ID 的等待上限（毫秒） */
    private static final long WAIT_MS = 500L;

    /** 业务线程池的 bean 名，由 module-framework 提供 */
    private static final String EXECUTOR_BEAN = "threadPoolTaskExecutor";

    private final AiCallLogMapper aiCallLogMapper;

    private final AiImageAssetMapper aiImageAssetMapper;

    /** 线程池，延迟解析：模块单独使用时可以退化为同步写入 */
    private volatile Executor executor;

    /**
     * 记录一次调用
     *
     * @param callLog 调用记录
     * @param assets  生成图资产，可为空
     * @return 调用记录 ID；写入超时或失败时返回 null
     */
    public Long record(AiCallLog callLog, List<AiImageAsset> assets)
    {
        Executor target = resolveExecutor();
        if (target == null)
        {
            return write(callLog, assets);
        }
        CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> write(callLog, assets), target);
        try
        {
            return future.get(WAIT_MS, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (Exception e)
        {
            log.warn("等待调用记录落库超时，记录将在后台继续写入：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 真正写库，任何异常都只记日志
     *
     * @param callLog 调用记录
     * @param assets  生成图资产
     * @return 调用记录 ID，失败时返回 null
     */
    private Long write(AiCallLog callLog, List<AiImageAsset> assets)
    {
        try
        {
            aiCallLogMapper.insertAiCallLog(callLog);
            Long callLogId = callLog.getId();
            if (assets != null && !assets.isEmpty() && callLogId != null)
            {
                for (AiImageAsset asset : assets)
                {
                    asset.setCallLogId(callLogId);
                    aiImageAssetMapper.insertAiImageAsset(asset);
                }
            }
            return callLogId;
        }
        catch (Exception e)
        {
            log.error("写入 AI 调用记录失败，不影响业务返回", e);
            return null;
        }
    }

    /**
     * 解析业务线程池
     *
     * @return 线程池；容器未就绪或没有该 bean 时返回 null
     */
    private Executor resolveExecutor()
    {
        Executor current = this.executor;
        if (current != null)
        {
            return current;
        }
        try
        {
            Object bean = SpringUtils.getBean(EXECUTOR_BEAN);
            if (bean instanceof Executor target)
            {
                this.executor = target;
                return target;
            }
        }
        catch (Exception e)
        {
            log.debug("未找到 {} 线程池，调用记录改为同步写入", EXECUTOR_BEAN);
        }
        return null;
    }
}
