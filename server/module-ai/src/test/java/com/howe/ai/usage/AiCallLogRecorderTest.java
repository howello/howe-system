package com.howe.ai.usage;

import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.mapper.AiCallLogMapper;
import com.howe.ai.persistence.mapper.AiImageAssetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 日志异步池拒绝任务时，不能影响业务结果或回到请求线程写库。 */
class AiCallLogRecorderTest {
    /** 饱和时丢弃辅助日志并返回空，不抛异常也不执行数据库写入。 */
    @Test
    void doesNotWriteOnCallerWhenExecutorRejects() {
        AiCallLogMapper mapper = mock(AiCallLogMapper.class);
        AiCallLogRecorder recorder = new AiCallLogRecorder(mapper, mock(AiImageAssetMapper.class));
        ReflectionTestUtils.setField(recorder, "executor", (Executor) command -> { throw new RejectedExecutionException(); });
        assertNull(recorder.record(new AiCallLog(), List.of()));
        verifyNoInteractions(mapper);
    }
}
