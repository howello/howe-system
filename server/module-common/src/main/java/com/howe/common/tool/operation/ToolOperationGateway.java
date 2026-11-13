package com.howe.common.tool.operation;

import com.howe.common.tool.model.ToolExecutionContext;

/**
 * 领域申请和查询操作的唯一平台接口，不暴露授权或存储服务。
 */
public interface ToolOperationGateway {

    /**
     * 校验当前可信调用，再预览或幂等建立待确认操作；本方法不执行业务写入。
     *
     * @param context 可信业务上下文
     * @param request 操作申请
     * @return 通用回执
     */
    ToolOperationReceipt request(ToolExecutionContext context, ToolOperationRequest request);

    /**
     * 按当前主体、原工具权限和对象可见性读取安全回执。
     *
     * @param context 可信业务上下文
     * @param operationId 操作标识
     * @return 通用回执
     */
    ToolOperationReceipt get(ToolExecutionContext context, String operationId);
}
