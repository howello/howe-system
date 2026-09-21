package com.howe.ai.capability;

/**
 * AI 能力类型
 *
 * <p>取值与 {@code ai_model.capability}、{@code ai_scene_route.capability} 的库中值一致。</p>
 *
 * @author howe
 */
public enum AiCapability
{
    /** 文本对话 */
    CHAT,

    /** 图片理解 */
    VISION,

    /** 文生图 */
    IMAGE,

    /** 文本向量 */
    EMBEDDING
}
