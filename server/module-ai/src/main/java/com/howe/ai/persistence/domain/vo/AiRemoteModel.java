package com.howe.ai.persistence.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 厂商侧的模型
 *
 * <p>「从渠道获取模型」的返回项：厂商 models 接口只给模型名，能力类型与是否已配置
 * 都由本模块补上，前端据此渲染可勾选的导入列表。</p>
 *
 * @param modelName           厂商模型名
 * @param added               该模型是否已在本渠道下配置
 * @param suggestedCapability 按模型名推测的能力类型，仅作导入时的默认值
 * @author howe
 */
@Schema(description = "厂商侧的模型")
public record AiRemoteModel(String modelName, boolean added, String suggestedCapability)
{
}
