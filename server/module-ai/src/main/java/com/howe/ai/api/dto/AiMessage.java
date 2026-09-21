package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息
 *
 * <p>只表达「角色 + 内容」，不绑定任何厂商的消息模型。</p>
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对话消息")
public class AiMessage
{
    /** 角色：system */
    public static final String ROLE_SYSTEM = "system";

    /** 角色：user */
    public static final String ROLE_USER = "user";

    /** 角色：assistant */
    public static final String ROLE_ASSISTANT = "assistant";

    @Schema(description = "角色（system / user / assistant）", example = "user")
    private String role;

    @Schema(description = "消息内容", example = "帮我写一段菜谱简介")
    private String content;

    /**
     * 构造一条 system 消息
     *
     * @param content 消息内容
     * @return 消息对象
     */
    public static AiMessage system(String content)
    {
        return AiMessage.builder().role(ROLE_SYSTEM).content(content).build();
    }

    /**
     * 构造一条 user 消息
     *
     * @param content 消息内容
     * @return 消息对象
     */
    public static AiMessage user(String content)
    {
        return AiMessage.builder().role(ROLE_USER).content(content).build();
    }

    /**
     * 构造一条 assistant 消息
     *
     * @param content 消息内容
     * @return 消息对象
     */
    public static AiMessage assistant(String content)
    {
        return AiMessage.builder().role(ROLE_ASSISTANT).content(content).build();
    }
}
