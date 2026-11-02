package com.howe.ai.capability;

import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.usage.AiUsageGuard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 统一门面的额度拒绝必须发生在付费模型调用之前。 */
class AiClientGuardTest {
    /** 超额用户不能进入能力层，避免只在外围按钮或控制器限制。 */
    @Test
    void rejectsQuotaBeforeProviderCall() {
        AiUsageGuard guard = mock(AiUsageGuard.class);
        ChatCapability chat = mock(ChatCapability.class);
        when(guard.acquire(eq("chat"), any())).thenThrow(new AiException(AiErrorCode.QUOTA_EXCEEDED, "额度已用尽"));
        AiClientImpl client = new AiClientImpl(guard, chat, mock(StreamCapability.class),
                mock(ImageCapability.class), mock(EmbeddingCapability.class));
        assertThrows(AiException.class, () -> client.chat(AiChatRequest.builder().prompt("测试").build()));
        verifyNoInteractions(chat);
    }

    /** 能力调用失败时仍释放并发租约，不能长期占住其他用户的名额。 */
    @Test
    void releasesLeaseOnFailure() {
        AiUsageGuard guard = mock(AiUsageGuard.class);
        AiUsageGuard.Lease lease = mock(AiUsageGuard.Lease.class);
        ChatCapability chat = mock(ChatCapability.class);
        when(guard.acquire(eq("chat"), any())).thenReturn(lease);
        when(chat.chat(any())).thenThrow(new AiException(AiErrorCode.PROVIDER_ERROR, "厂商失败"));
        AiClientImpl client = new AiClientImpl(guard, chat, mock(StreamCapability.class),
                mock(ImageCapability.class), mock(EmbeddingCapability.class));
        assertThrows(AiException.class, () -> client.chat(AiChatRequest.builder().prompt("测试").build()));
        verify(lease).close();
    }
}
