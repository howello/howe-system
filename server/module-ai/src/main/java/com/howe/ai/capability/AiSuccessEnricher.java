package com.howe.ai.capability;

import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.AiImageAsset;

import java.util.List;

/**
 * 成功调用后的记录补充
 *
 * <p>各能力要落库的字段不同（对话有 token 用量、文生图有图片资产），
 * 由能力层在这里补齐，执行模板只负责通用字段。</p>
 *
 * @param <T> 调用结果类型
 * @author howe
 */
@FunctionalInterface
public interface AiSuccessEnricher<T>
{
    /**
     * 补充调用记录，并返回需要一并落库的图片资产
     *
     * @param callLog 待落库的调用记录
     * @param value   调用结果
     * @return 图片资产，没有时返回空列表
     */
    List<AiImageAsset> enrich(AiCallLog callLog, T value);
}
