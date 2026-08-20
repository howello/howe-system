package com.howe.automation.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.howe.common.core.redis.RedisCache;

@ExtendWith(MockitoExtension.class)
class BrowserSessionStoreTest
{
    @Mock
    private RedisCache redisCache;

    @InjectMocks
    private BrowserSessionStore sessionStore;

    @Test
    void shouldSaveSessionWithThirtyDayTtl()
    {
        sessionStore.save("com.howe.automation.task.MockTask", "site-a", "{\"cookies\":[]}");

        verify(redisCache).setCacheObject(
                "automation:session:com.howe.automation.task.MockTask:site-a",
                "{\"cookies\":[]}",
                30,
                TimeUnit.DAYS);
    }

    @Test
    void shouldNormalizeSessionKeyAndReadState()
    {
        when(redisCache.getCacheObject("automation:session:task_key:site-a")).thenReturn("state");

        String result = sessionStore.get("task/key", "site-a");

        assertEquals("state", result);
    }

    @Test
    void shouldDeleteAndRefreshSession()
    {
        sessionStore.delete("task", "site-a");
        sessionStore.refresh("task", "site-a");

        verify(redisCache).deleteObject("automation:session:task:site-a");
        verify(redisCache).expire("automation:session:task:site-a", 30, TimeUnit.DAYS);
    }
}
