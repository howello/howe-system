package com.howe.ai.task;

import com.howe.ai.persistence.mapper.AiCallLogMapper;
import com.howe.ai.persistence.mapper.AiImageAssetMapper;
import com.howe.common.utils.ConfigUtils;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证保留期和图片资产在日志清理中的独立生命周期。 */
class AiCallLogCleanupTaskTest {
    /** 每批先解除资产关联再删日志，不删除仍被业务引用的图片。 */
    @Test
    void detachesAssetsBeforeRemovingExpiredLogs() {
        AiCallLogMapper logs = mock(AiCallLogMapper.class);
        AiImageAssetMapper assets = mock(AiImageAssetMapper.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        when(logs.selectExpiredIds(any(), eq(500))).thenReturn(List.of(1L, 2L));
        when(logs.deleteByIds(List.of(1L, 2L))).thenReturn(2);
        try (var config = mockStatic(ConfigUtils.class)) {
            config.when(() -> ConfigUtils.getInt(anyString(), anyInt())).thenReturn(30);
            new AiCallLogCleanupTask(logs, assets, transactions).clean();
        }
        var order = inOrder(assets, logs);
        order.verify(logs).selectExpiredIds(any(), eq(500));
        order.verify(assets).detachCallLogs(List.of(1L, 2L));
        order.verify(logs).deleteByIds(List.of(1L, 2L));
        verifyNoMoreInteractions(assets);
    }
}
