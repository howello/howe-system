package com.howe.system.tool.operation;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.system.service.ISysNoticeReadService;
import com.howe.system.service.SysNoticeReadBatchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 公告批量已读处理器。
 *
 * <p>只调用受控批量入口：先锁定并校验整批公告正常可读，再写入已读；
 * 不直接使用没有校验的 {@code markReadBatch}。结果不返回公告 HTML。</p>
 */
public final class SystemNoticeWriteHandler implements ToolOperationHandler {

    private final ISysNoticeReadService notices;

    /**
     * 构造公告已读处理器。
     *
     * @param notices 公告已读服务
     */
    public SystemNoticeWriteHandler(ISysNoticeReadService notices) {
        this.notices = Objects.requireNonNull(notices);
    }

    @Override
    public Key key() {
        return new Key("system_notice_mark_read", "1");
    }

    /** 校验 1—20 个不重复公告 ID。 */
    @Override
    public Plan plan(ToolOperationContext context, Map<String, Object> arguments) {
        Object value = arguments.get("noticeIds");
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 20) {
            throw new IllegalArgumentException("noticeIds 数量必须在 1 到 20 之间");
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text) || !text.matches("^[1-9][0-9]{0,18}$")) {
                throw new IllegalArgumentException("noticeIds 元素格式不正确");
            }
            ids.add(Long.parseLong(text));
        }
        if (ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("noticeIds 不能重复");
        }
        ids = ids.stream().sorted().toList();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("noticeIds", ids.stream().map(String::valueOf).toList());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("noticeIds", plan.get("noticeIds"));
        summary.put("count", ids.size());
        summary.put("effect", "把这些公告标记为当前账号已读");
        return new Plan(plan, summary, "sys_notice_read", null, null, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.LOCAL_WRITE);
    }

    /** 执行前不额外读取，受控入口在事务内复核。 */
    @Override
    public void revalidate(ToolOperationContext context, Plan plan) {
        // 公告可读性必须在同一写入事务内锁定校验，见 execute。
    }

    /** 无额外占用。 */
    @Override
    public void reserve(ToolOperationContext context, Plan plan) {
        // 无领域占用。
    }

    /** 写入已读。 */
    @Override
    public Outcome execute(ToolOperationContext context, Plan plan) {
        List<Long> ids = ((List<?>) plan.arguments().get("noticeIds")).stream()
                .map(value -> Long.parseLong(String.valueOf(value))).toList();
        try {
            SysNoticeReadBatchResult result = notices.markReadableBatchForTool(Long.parseLong(context.identity().userId()),
                    ids);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("acceptedIds", result.acceptedIds().stream().map(String::valueOf).toList());
            evidence.put("alreadyReadIds", result.alreadyReadIds().stream().map(String::valueOf).toList());
            return new Outcome(OutcomeKind.APPLIED, "LOCAL", "marked", evidence);
        } catch (ServiceException exception) {
            throw translate(exception);
        }
    }

    /**
     * 把领域异常收敛为稳定工具错误码。
     *
     * <p>公告不存在或已停用属于对象不可用，不是状态竞争；统一映射为
     * {@code not_found_or_forbidden}，提示用户重新查询可读公告后再准备。
     * 未识别的领域异常保留 {@code state_conflict} 兜底，不回显异常原文。</p>
     *
     * @param exception 领域服务异常
     * @return 可安全展示的工具异常
     */
    private static ToolException translate(ServiceException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("不存在") || message.contains("已停用")) {
            return new ToolException("not_found_or_forbidden", "公告不存在或已停用，请重新查询可读公告后再准备操作");
        }
        return new ToolException("state_conflict", "公告已读未完成，请重新查询公告状态");
    }

    /** 本地写无远端对账。 */
    @Override
    public Outcome reconcile(ToolOperationContext context, Plan plan) {
        return new Outcome(OutcomeKind.UNKNOWN, "RECONCILE", "reconcile_not_supported", Map.of());
    }

    /** 安全结果。 */
    @Override
    public Map<String, Object> complete(ToolOperationContext context, Plan plan, Outcome outcome) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acceptedIds", outcome.evidence().get("acceptedIds"));
        result.put("alreadyReadIds", outcome.evidence().get("alreadyReadIds"));
        return result;
    }

    /** 无占用。 */
    @Override
    public void releaseUnapplied(ToolOperationContext context, Plan plan, String reasonCode) {
        // 无占用。
    }
}
