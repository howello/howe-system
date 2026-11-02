package com.howe.ai.storage;

import com.howe.common.config.YmlConfig;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 补偿入队的磁盘故障回归，不写入用户实际缓存目录。 */
class AiImageCompensationStoreTest {
    @TempDir Path directory;

    /** 使用生产缓存相同的 Fastjson 配置验证 record 可恢复为带类型的对象。 */
    @Test
    void roundTripsStatusWithRedisJsonFormat() {
        var status = new AiImageCompensationStore.TransferStatus("a".repeat(32), 1L, 10L, "READY", "已保存", 123L);
        String json = com.alibaba.fastjson2.JSON.toJSONString(status, com.alibaba.fastjson2.JSONWriter.Feature.WriteClassName);
        Object restored = com.alibaba.fastjson2.JSON.parseObject(json, Object.class,
                com.alibaba.fastjson2.JSONReader.autoTypeFilter(com.howe.common.constant.Constants.JSON_WHITELIST_STR));
        assertEquals(status, restored);
    }

    /** 暂存目录不可写时抛出失败，不创建无字节的 Redis 任务。 */
    @Test
    void rejectsWhenSpoolIsNotADirectory() throws Exception {
        Path file = Files.writeString(directory.resolve("file"), "不是目录");
        RedisCache redis = mock(RedisCache.class);
        try (var config = mockStatic(YmlConfig.class)) {
            config.when(YmlConfig::getCacheDir).thenReturn(file.toString());
            assertThrows(ServiceException.class, () -> new AiImageCompensationStore(redis)
                    .enqueue("ai/test.png", new byte[] { 1 }, "image/png", 120, "test"));
        }
        verifyNoInteractions(redis);
    }

    /** 过期任务返回明确失败，且家庭或用户改变后不能读取旧结果。 */
    @Test
    void expiresResultAndChecksOwnership() {
        RedisCache redis = mock(RedisCache.class);
        String id = "a".repeat(32);
        when(redis.getCacheObject("ai:transfer:status:" + id)).thenReturn(
                new AiImageCompensationStore.TransferStatus(id, 1L, 10L, "PENDING_TRANSFER", "等待", 1));
        var store = new AiImageCompensationStore(redis);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            security.when(SecurityUtils::getDeptId).thenReturn(10L);
            assertEquals("FAILED", store.status(id).state());
            security.when(SecurityUtils::getDeptId).thenReturn(20L);
            assertThrows(ServiceException.class, () -> store.status(id));
        }
    }

    /** 补偿成功清理文件和队列后，仍保留可查询的已保存结果。 */
    @Test
    void retainsSuccessfulResultAfterCleanup() {
        RedisCache redis = mock(RedisCache.class);
        String id = "b".repeat(32);
        var status = new AiImageCompensationStore.TransferStatus(id, 1L, 10L, "PENDING_TRANSFER", "等待", Long.MAX_VALUE / 2);
        when(redis.getCacheObject("ai:transfer:status:" + id)).thenReturn(status);
        AiTransferTask task = new AiTransferTask();
        task.setTaskId(id);
        task.setFile(id + ".bin");
        try (var config = mockStatic(YmlConfig.class)) {
            config.when(YmlConfig::getCacheDir).thenReturn(directory.toString());
            new AiImageCompensationStore(redis).markSuccess(task);
        }
        verify(redis).setCacheObject(eq("ai:transfer:status:" + id), argThat(value ->
                value instanceof AiImageCompensationStore.TransferStatus result && "READY".equals(result.state())),
                anyInt(), eq(java.util.concurrent.TimeUnit.SECONDS));
        verify(redis).deleteObject("ai:transfer:task:" + id);
        verify(redis, never()).deleteObject("ai:transfer:status:" + id);
    }
}
