package com.howe.ai.task;

import com.howe.ai.persistence.mapper.AiCallLogMapper;
import com.howe.ai.persistence.mapper.AiImageAssetMapper;
import org.springframework.transaction.support.TransactionTemplate;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** 有界清理过期 AI 调用日志；生成图资产及业务引用的图片独立保留。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallLogCleanupTask {
    private final AiCallLogMapper mapper;
    private final AiImageAssetMapper assets;
    private final TransactionTemplate transactions;

    /** 每小时最多清理 2500 条，保留期为零时禁用清理。 */
    @Scheduled(fixedDelay = 3600000, initialDelay = 120000)
    public void clean() {
        int days = ConfigUtils.getInt(ConfigConstants.AI_LOG_RETENTION_DAYS, 30);
        if (days <= 0) return;
        Date before = Date.from(Instant.now().minus(Math.min(days, 36500), ChronoUnit.DAYS));
        long start = System.currentTimeMillis();
        int total = 0;
        try {
            for (int batch = 0; batch < 5; batch++) {
                Integer count = transactions.execute(status -> {
                    var ids = mapper.selectExpiredIds(before, 500);
                    if (ids.isEmpty()) return 0;
                    assets.detachCallLogs(ids);
                    return mapper.deleteByIds(ids);
                });
                int rows = count == null ? 0 : count;
                total += rows;
                if (rows < 500 || System.currentTimeMillis() - start > 5000) break;
            }
            log.info("AI 日志清理完成，删除 {} 条，耗时 {}ms", total, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("AI 日志清理失败，已删除 {} 条，将在下一轮继续", total, e);
        }
    }
}
