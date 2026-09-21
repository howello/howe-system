package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 图片理解请求
 *
 * <p>与 {@link AiChatRequest} 同构，额外携带图片。图片既可以是公网 URL，
 * 也可以是 {@code data:image/png;base64,....} 形式的内联数据。</p>
 *
 * @author howe
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "图片理解请求")
public class AiVisionRequest extends AiChatRequest
{
    @Schema(description = "图片列表，URL 或 data:image/...;base64, 形式")
    private List<String> images;
}
