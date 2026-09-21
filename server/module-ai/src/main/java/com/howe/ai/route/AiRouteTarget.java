package com.howe.ai.route;

import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;

/**
 * 一次调用的候选目标
 *
 * @param channel 渠道
 * @param model   模型
 * @author howe
 */
public record AiRouteTarget(AiChannel channel, AiModel model)
{
}
