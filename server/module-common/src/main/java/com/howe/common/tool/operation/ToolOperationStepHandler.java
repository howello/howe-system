package com.howe.common.tool.operation;

import java.util.Map;

/**
 * 固定后续步骤的领域处理器 SPI。
 *
 * <p>每个步骤由所属领域模块实现并由平台按代码键登记，不接受数据库或模型输入指定实现类。
 * 实现只依赖本契约与所属领域 Service，不得注入 MCP 平台服务或直接读写账本。</p>
 *
 * <p>实现必须区分「授权失效」与「业务失败」：授权失效应抛受控异常或返回
 * {@link com.howe.common.tool.operation.ToolOperationStepContext.StepOutcome#aborted}，
 * 不能被当作可降级的业务失败继续后续写入。</p>
 */
public interface ToolOperationStepHandler {

    /**
     * 返回步骤代码键，必须与父操作声明的 {@link ToolOperationContinuation#stepKey()} 一致。
     *
     * @return 步骤代码键
     */
    String stepKey();

    /**
     * 返回与步骤代码键配套的处理器版本，用于账本冻结。
     *
     * @return 处理器版本
     */
    String stepVersion();

    /**
     * 执行步骤。
     *
     * @param context 可信上下文与授权守卫
     * @param payload 父操作登记的业务载荷
     * @return 步骤结果
     */
    ToolOperationStepContext.StepOutcome execute(ToolOperationStepContext context, Map<String, Object> payload);
}
