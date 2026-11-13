package com.howe.common.tool.operation;

import java.util.Map;

/**
 * 父操作成功后需要继续执行的固定后续步骤声明。
 *
 * <p>领域 Handler 只声明步骤代码键与业务载荷，不接触账本、租约或授权。
 * 平台在父操作成功事务内登记步骤；步骤是否执行、何时执行、授权是否仍有效，
 * 全部由平台裁决。</p>
 *
 * @param stepKey 步骤代码键，必须与已注册的 {@link ToolOperationStepHandler} 一致
 * @param payload 仅本步骤需要的业务参数，必须只含 JSON 基础类型
 */
public record ToolOperationContinuation(String stepKey, Map<String, Object> payload) {

    /** 防御性复制载荷并校验必要字段。 */
    public ToolOperationContinuation {
        if (stepKey == null || stepKey.isBlank()) {
            throw new IllegalArgumentException("后续步骤缺少代码键");
        }
        payload = ToolOperationValues.copyMap(payload);
    }
}
