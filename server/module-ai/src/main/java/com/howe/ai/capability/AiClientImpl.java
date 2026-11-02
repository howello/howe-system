package com.howe.ai.capability;

import com.howe.ai.api.AiClient;
import com.howe.ai.api.AiException;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.usage.AiUsageGuard;
import java.util.function.Supplier;
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
public class AiClientImpl implements AiClient {
    private final AiUsageGuard usageGuard;

    private final ChatCapability chatCapability;

    private final StreamCapability streamCapability;

    private final ImageCapability imageCapability;

    private final EmbeddingCapability embeddingCapability;

    @Override
    public AiChatResult chat(AiChatRequest request) {
        validateChat(request);
        return guarded("chat", () -> chatCapability.chat(request));
    }

    @Override
    public Flux<AiTextChunk> chatStream(AiChatRequest request) {
        validateChat(request);
        String[] identity = usageGuard.identity();
        return Flux.using(() -> usageGuard.acquire("chat", identity),
                lease -> streamCapability.stream(request), AiUsageGuard.Lease::close);
    }

    @Override
    public <T> T chat(AiChatRequest request, Class<T> type) {
        validateChat(request);
        return guarded("chat", () -> chatCapability.structured(request, type));
    }

    @Override
    public AiImageResult image(AiImageRequest request) {
        if (request == null) throw bad("图片请求不能为空");
        checkText(request.getPrompt(), request.getNegativePrompt());
        checkTimeout(request.getTimeoutMs());
        if (request.getCount() != null && (request.getCount() < 1 || request.getCount() > 4)) {
            throw bad("每次最多生成4张图片");
        }
        return guarded("image", () -> imageCapability.image(request));
    }

    @Override
    public AiChatResult vision(AiVisionRequest request) {
        validateChat(request);
        if (request.getImages() != null && (request.getImages().size() > 4
                || request.getImages().stream().anyMatch(image -> image == null || image.length() > 4_194_304))) {
            throw bad("图片理解最多4张，每张内联内容最多4MB");
        }
        return guarded("chat", () -> chatCapability.vision(request));
    }

    @Override
    public AiEmbedResult embed(AiEmbedRequest request) {
        if (request == null || request.getTexts() == null || request.getTexts().size() > 100) {
            throw bad("向量请求每次最多100段文本");
        }
        checkText(request.getTexts().toArray(String[]::new));
        checkTimeout(request.getTimeoutMs());
        return guarded("chat", () -> embeddingCapability.embed(request));
    }
    /** 在统一门面预占额度并始终释放并发租约，能力内部的重试共用一次额度。 */
    private <T> T guarded(String capability, Supplier<T> action) {
        try (AiUsageGuard.Lease lease = usageGuard.acquire(capability, usageGuard.identity())) {
            return action.get();
        }
    }

    /** 校验对话输入总量，不允许通过消息数组绕过单条提示词限制。 */
    private void validateChat(AiChatRequest request) {
        if (request == null) throw bad("对话请求不能为空");
        java.util.List<String> texts = new java.util.ArrayList<>();
        texts.add(request.getPrompt());
        texts.add(request.getSystem());
        texts.add(request.getOutputSchema());
        if (request.getMessages() != null) {
            if (request.getMessages().size() > 100) throw bad("对话最多100条消息");
            request.getMessages().forEach(message -> {
                if (message == null) throw bad("对话消息不能为空");
                texts.add(message.getContent());
            });
        }
        checkText(texts.toArray(String[]::new));
        checkTimeout(request.getTimeoutMs());
        if (request.getMaxTokens() != null && (request.getMaxTokens() < 1 || request.getMaxTokens() > 32768)) {
            throw bad("最大输出 token 必须介于1至32768");
        }
    }

    /** 限制所有文本字段的累计字符数，避免超大提示词占用资源。 */
    private void checkText(String... texts) {
        long length = 0;
        for (String text : texts) length += text == null ? 0 : text.length();
        if (length > 40000) throw bad("单次 AI 输入不能超过40000个字符");
    }

    /** 限制请求显式超时，不能由调用方无限延长资源占用。 */
    private void checkTimeout(Long timeout) {
        if (timeout != null && (timeout <= 0 || timeout > 300000)) throw bad("AI 超时必须介于1至300000毫秒");
    }

    /** 返回不应重试或降级的入参错误。 */
    private AiException bad(String message) {
        return new AiException(AiErrorCode.BAD_REQUEST, message);
    }
}
