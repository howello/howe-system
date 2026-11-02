package com.howe.meal.service;

import com.howe.common.core.redis.RedisCache;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证任务拒绝、失败结果与跨家庭查询边界。 */
class MealDishAiTaskServiceTest {
    /** 任务状态使用生产缓存相同的序列化配置后仍可恢复为 record。 */
    @Test
    void roundTripsStatusWithRedisJsonFormat() {
        var status = new MealDishAiTaskService.Status("a".repeat(32), 1L, 10L, 2L, "PENDING", "等待", 123L);
        String json = com.alibaba.fastjson2.JSON.toJSONString(status, com.alibaba.fastjson2.JSONWriter.Feature.WriteClassName);
        Object restored = com.alibaba.fastjson2.JSON.parseObject(json, Object.class,
                com.alibaba.fastjson2.JSONReader.autoTypeFilter(com.howe.common.constant.Constants.JSON_WHITELIST_STR));
        assertEquals(status, restored);
    }
    /** 池满时返回提交失败，并留下可识别的失败状态，不能谎报已受理。 */
    @Test
    void rejectsWithoutRunningAndPreservesFailedState() {
        RedisCache redis = mock(RedisCache.class);
        Map<String, MealDishAiTaskService.Status> values = new HashMap<>();
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(redis).setCacheObject(anyString(), any(), anyInt(), any());
        MealDishAiTaskService service = new MealDishAiTaskService(redis, task -> { throw new RejectedExecutionException(); });
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            security.when(SecurityUtils::getDeptId).thenReturn(10L);
            assertThrows(ServiceException.class, () -> service.submit(1L, () -> fail("被拒绝任务不得执行")));
        }
        assertEquals(1, values.size());
        assertEquals("FAILED", values.values().iterator().next().state());
    }

    /** 即使知道任务 ID，也不能读取其他用户或家庭的执行结果。 */
    @Test
    void rejectsForeignTaskQueries() {
        RedisCache redis = mock(RedisCache.class);
        String id = "a".repeat(32);
        when(redis.getCacheObject("meal:ai-task:" + id)).thenReturn(new MealDishAiTaskService.Status(
                id, 3L, 20L, 1L, "SUCCEEDED", "已完成", System.currentTimeMillis()));
        MealDishAiTaskService service = new MealDishAiTaskService(redis, Runnable::run);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            security.when(SecurityUtils::getDeptId).thenReturn(10L);
            assertThrows(ServiceException.class, () -> service.get(id));
        }
    }
}
