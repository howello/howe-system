package com.howe.ai.capability;

import cn.hutool.core.util.IdUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiEmbedRequest;
import com.howe.ai.api.dto.AiEmbedResult;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.route.AiRoutePlan;
import com.howe.ai.route.AiRouteResolver;
import com.howe.ai.usage.AiRequestDigest;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量能力
 *
 * <p>只负责算向量，存与查由业务方负责。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingCapability
{
    private final AiRouteResolver aiRouteResolver;

    private final AiConfigCache aiConfigCache;

    private final AiExecutionTemplate aiExecutionTemplate;

    /**
     * 文本向量化
     *
     * @param request 向量请求
     * @return 向量结果
     */
    public AiEmbedResult embed(AiEmbedRequest request)
    {
        if (request.getTexts() == null || request.getTexts().isEmpty())
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "向量化文本列表不能为空");
        }
        AiRoutePlan plan = aiRouteResolver.resolve(request.getScene(), AiCapability.EMBEDDING,
                request.getChannel(), request.getModel());
        AiCallContext context = new AiCallContext(IdUtil.fastSimpleUUID(), request.getScene(),
                AiCapability.EMBEDDING,
                AiRequestDigest.ofTexts(request.getScene(), AiCapability.EMBEDDING.name(), request.getTexts()),
                currentOperator());

        AiInvocation<List<float[]>> invocation = aiExecutionTemplate.execute(plan, context, target ->
        {
            EmbeddingModel embeddingModel = aiConfigCache.embeddingModel(target.channel(), target.model());
            EmbeddingOptions options = aiConfigCache.embeddingOptions(target.channel(), target.model(),
                    request.getDimensions());
            EmbeddingResponse response = options == null
                    ? embeddingModel.embedForResponse(request.getTexts())
                    : embeddingModel.call(new EmbeddingRequest(request.getTexts(), options));
            List<float[]> vectors = new ArrayList<>();
            response.getResults().forEach(result -> vectors.add(result.getOutput()));
            return vectors;
        }, (callLog, vectors) -> List.of());

        return AiEmbedResult.builder()
                .embeddings(invocation.value())
                .provider(invocation.target().channel().getProviderCode())
                .model(invocation.target().model().getModelName())
                .elapsedMs(invocation.elapsedMs())
                .callLogId(invocation.callLogId())
                .build();
    }

    /**
     * 取当前操作人，未登录时返回 system
     *
     * @return 操作人
     */
    private String currentOperator()
    {
        try
        {
            String username = SecurityUtils.getUsername();
            return StringUtils.isEmpty(username) ? "system" : username;
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
