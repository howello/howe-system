package com.howe.blog.tool.operation;

import com.howe.blog.domain.BlogTalk;
import com.howe.blog.service.IBlogTalkService;
import com.howe.blog.tool.dto.BlogWriteFields;
import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 博客说说写操作处理器。
 *
 * <p>创建时 status 必须由工具显式给出，不使用 Service 默认公开值；
 * 更新使用数据版本 CAS，公开状态变化由确认摘要展示。</p>
 */
public final class BlogTalkWriteHandler implements ToolOperationHandler {

    /** 创建工具名。 */
    private static final String TOOL_CREATE = "blog_talk_create";

    /** 更新工具名。 */
    private static final String TOOL_UPDATE = "blog_talk_update";

    private final String toolName;

    private final IBlogTalkService talks;

    /**
     * 构造说说写处理器。
     *
     * @param toolName 工具名
     * @param talks 说说服务
     */
    public BlogTalkWriteHandler(String toolName, IBlogTalkService talks) {
        this.toolName = toolName;
        this.talks = Objects.requireNonNull(talks);
    }

    @Override
    public Key key() {
        return new Key(toolName, "1");
    }

    /** 规范化并校验。 */
    @Override
    public Plan plan(ToolOperationContext context, Map<String, Object> arguments) {
        return TOOL_CREATE.equals(toolName) ? createPlan(arguments) : updatePlan(arguments);
    }

    /** 执行前重新确认对象仍可访问。 */
    @Override
    public void revalidate(ToolOperationContext context, Plan plan) {
        if (!TOOL_UPDATE.equals(toolName)) {
            return;
        }
        BlogTalk current = talks.selectBlogTalkById(Long.parseLong(plan.targetId()));
        if (current == null) {
            throw new ToolException("not_found_or_forbidden", "说说不存在或无权访问");
        }
    }

    /** 无额外领域占用。 */
    @Override
    public void reserve(ToolOperationContext context, Plan plan) {
        revalidate(context, plan);
    }

    /** 执行写入。 */
    @Override
    public Outcome execute(ToolOperationContext context, Plan plan) {
        try {
            if (TOOL_CREATE.equals(toolName)) {
                BlogTalk talk = new BlogTalk();
                talk.setContent(String.valueOf(plan.arguments().get("content")));
                talk.setStatus("published".equals(plan.arguments().get("status")) ? "0" : "1");
                talk.setTags(join(plan.arguments().get("tags")));
                talk.setIsTop(Boolean.TRUE.equals(plan.arguments().get("isTop")) ? "1" : "0");
                talks.insertBlogTalk(talk);
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("talkId", String.valueOf(talk.getTalkId()));
                evidence.put("version", "0");
                evidence.put("status", plan.arguments().get("status"));
                return new Outcome(OutcomeKind.APPLIED, "LOCAL", "created", evidence);
            }
            Long talkId = Long.parseLong(plan.targetId());
            BlogTalk patch = new BlogTalk();
            patch.setTalkId(talkId);
            patch.setContent(stringOrNull(plan.arguments().get("content")));
            patch.setTags(joinIfPresent(plan.arguments().get("tags")));
            Object status = plan.arguments().get("status");
            if (status != null) {
                patch.setStatus("published".equals(status) ? "0" : "1");
            }
            Object top = plan.arguments().get("isTop");
            if (top instanceof Boolean flag) {
                patch.setIsTop(flag ? "1" : "0");
            }
            long expected = Long.parseLong(plan.expectedVersion());
            long updated = talks.updateBlogTalkCas(patch, expected);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("talkId", String.valueOf(talkId));
            evidence.put("version", String.valueOf(updated));
            evidence.put("status", status == null ? null : status);
            return new Outcome(OutcomeKind.APPLIED, "LOCAL", "updated", evidence);
        } catch (ServiceException exception) {
            throw translate(exception);
        }
    }

    /** 本地写不涉及远端对账。 */
    @Override
    public Outcome reconcile(ToolOperationContext context, Plan plan) {
        return new Outcome(OutcomeKind.UNKNOWN, "RECONCILE", "reconcile_not_supported", Map.of());
    }

    /** 安全结果。 */
    @Override
    public Map<String, Object> complete(ToolOperationContext context, Plan plan, Outcome outcome) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("talkId", outcome.evidence().get("talkId"));
        result.put("version", outcome.evidence().get("version"));
        result.put("status", outcome.evidence().get("status"));
        return result;
    }

    /** 本地事务回滚即未生效。 */
    @Override
    public void releaseUnapplied(ToolOperationContext context, Plan plan, String reasonCode) {
        // 无额外占用。
    }

    /** 创建计划。 */
    private Plan createPlan(Map<String, Object> arguments) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("content", requiredText(arguments, "content", BlogWriteFields.TALK_CONTENT_MAX));
        plan.put("status", requiredStatus(arguments));
        plan.put("tags", textList(arguments, "tags"));
        plan.put("isTop", optionalBoolean(arguments, "isTop"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", plan.get("status"));
        summary.put("isTop", plan.get("isTop"));
        summary.put("contentChars", ((String) plan.get("content")).length());
        summary.put("effect", "published".equals(plan.get("status")) ? "立即公开这条说说" : "保存为隐藏，不公开");
        return new Plan(plan, summary, "blog_talk", null, null, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.LOCAL_WRITE);
    }

    /** 更新计划。 */
    private Plan updatePlan(Map<String, Object> arguments) {
        Object id = arguments.get("talkId");
        if (!(id instanceof String text) || !text.matches("^[1-9][0-9]{0,18}$")) {
            throw new IllegalArgumentException("参数格式不正确：talkId");
        }
        Object patchValue = arguments.get("patch");
        if (!(patchValue instanceof Map<?, ?> rawPatch) || rawPatch.isEmpty()) {
            throw new IllegalArgumentException("patch 不能为空");
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        rawPatch.forEach((key, value) -> patch.put(String.valueOf(key), value));
        for (String key : patch.keySet()) {
            if (!java.util.Set.of("content", "tags", "status", "isTop").contains(key)) {
                throw new IllegalArgumentException("不支持的 patch 字段：" + key);
            }
        }
        Object versionValue = arguments.get("expectedVersion");
        if (!(versionValue instanceof String version) || !version.matches("(0|[1-9][0-9]{0,18})")) {
            throw new IllegalArgumentException("参数格式不正确：expectedVersion");
        }
        if (patch.get("status") != null) {
            Object status = patch.get("status");
            if (!"published".equals(status) && !"unpublished".equals(status)) {
                throw new IllegalArgumentException("参数取值不合法：status");
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("talkId", text);
        summary.put("expectedVersion", version);
        summary.put("fields", new ArrayList<>(patch.keySet()));
        // 公开状态变化必须在确认页明示后果；未改 status 时明确说明保持当前可见性。
        Object statusValue = patch.get("status");
        if (statusValue != null) {
            summary.put("status", statusValue);
            summary.put("effect", "published".equals(statusValue)
                    ? "这条说说将对所有访客公开可见" : "这条说说将隐藏，不再对外展示");
        } else {
            summary.put("effect", "保持当前公开状态不变");
        }
        return new Plan(patch, summary, "blog_talk", text, version, null, ExecutionKind.LOCAL_TRANSACTION,
                QueueGroup.LOCAL_WRITE);
    }

    /** 必填正文。 */
    private static String requiredText(Map<String, Object> arguments, String name, int max) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()
                || text.codePointCount(0, text.length()) > max) {
            throw new IllegalArgumentException("参数不合法：" + name);
        }
        return text;
    }

    /** 必填公开状态。 */
    private static String requiredStatus(Map<String, Object> arguments) {
        Object value = arguments.get("status");
        if (!(value instanceof String text) || (!"published".equals(text) && !"unpublished".equals(text))) {
            throw new IllegalArgumentException("参数取值不合法：status");
        }
        return text;
    }

    /** 字符串数组。 */
    private static List<String> textList(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) {
            return List.of();
        }
        return castList(value);
    }

    /** 可空字符串数组，用于更新 patch。 */
    private static String joinIfPresent(Object value) {
        if (value == null) {
            return null;
        }
        return String.join(",", castList(value));
    }

    /** 拼接逗号分隔。 */
    private static String join(Object value) {
        return value == null ? "" : String.join(",", castList(value));
    }

    /** 字符串数组校验。 */
    private static List<String> castList(Object value) {
        if (!(value instanceof List<?> list) || list.size() > BlogWriteFields.TAG_MAX_ITEMS) {
            throw new IllegalArgumentException("参数类型不正确");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()
                    || text.length() > BlogWriteFields.TAG_MAX_LENGTH) {
                throw new IllegalArgumentException("参数元素不合法");
            }
            result.add(text.trim());
        }
        return result;
    }

    /** 可选布尔。 */
    private static Boolean optionalBoolean(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) {
            return Boolean.FALSE;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("参数类型不正确：" + name);
        }
        return flag;
    }

    /** 可空字符串。 */
    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 业务异常收敛。 */
    private static ToolException translate(ServiceException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("已被其它操作修改")) {
            return new ToolException("version_conflict", "内容已发生变化，请重新预览");
        }
        if (message.contains("不存在")) {
            return new ToolException("not_found_or_forbidden", "说说不存在或无权访问");
        }
        return new ToolException("state_conflict", "说说更新未完成");
    }
}
