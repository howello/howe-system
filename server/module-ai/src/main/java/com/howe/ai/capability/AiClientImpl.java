package com.howe.ai.capability;

import com.howe.ai.api.AiClient;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiEmbedRequest;
import com.howe.ai.api.dto.AiEmbedResult;
import com.howe.ai.api.dto.AiImageRequest;
import com.howe.ai.api.dto.AiImageResult;
import com.howe.ai.api.dto.AiTextChunk;
import com.howe.ai.api.dto.AiVisionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 调用门面实现
 *
 * <p>业务模块注入 {@link AiClient} 拿到的就是这个实现，它只做分发，
 * 具体能力由各自的 Capability 承担。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class AiClientImpl implements AiClient
{
    private final ChatCapability chatCapability;

    private final StreamCapability streamCapability;

    private final ImageCapability imageCapability;

    private final EmbeddingCapability embeddingCapability;

    @Override
    public AiChatResult chat(AiChatRequest request)
    {
        return chatCapability.chat(request);
    }

    @Override
    public Flux<AiTextChunk> chatStream(AiChatRequest request)
    {
        return streamCapability.stream(request);
    }

    @Override
    public <T> T chat(AiChatRequest request, Class<T> type)
    {
        return chatCapability.structured(request, type);
    }

    @Override
    public AiImageResult image(AiImageRequest request)
    {
        return imageCapability.image(request);
    }

    @Override
    public AiChatResult vision(AiVisionRequest request)
    {
        return chatCapability.vision(request);
    }

    @Override
    public AiEmbedResult embed(AiEmbedRequest request)
    {
        return embeddingCapability.embed(request);
    }
}
