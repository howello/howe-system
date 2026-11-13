package com.howe.system.tool.provider;

import com.github.pagehelper.PageInfo;
import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.html.NoticeHtmlSanitizer;
import com.howe.system.domain.SysNotice;
import com.howe.system.service.ISysNoticeReadService;
import com.howe.system.tool.dto.SystemReadDtos.NoticeItem;
import com.howe.system.tool.dto.SystemReadDtos.NoticePage;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 系统公告只读工具提供者。
 *
 * <p>只发布本人公告工具；C1 的 {@code system_self_get} 仍由
 * {@link SystemSelfToolProvider} 注册，本类不重复注册。公告正文经 HTML 净化后
 * 再转纯文本摘要，不把原始富文本透给客户端。</p>
 *
 * <p>本类只依赖 common 工具契约与本模块 Service，不引用 framework、MCP、SDK 或 OAuth 类型。</p>
 */
public final class SystemReadToolProvider implements BusinessToolProvider {

    /** 能力分组。 */
    private static final String CAPABILITY_GROUP = "system.notice.read";

    /** 结果预算。 */
    private static final int RESULT_BYTES = 65536;

    /** 正文摘要上限（码点）。 */
    private static final int SUMMARY_CHARS = 500;

    /** 输入字段。 */
    private static final Set<String> FIELDS = Set.of("readState", "page", "pageSize");

    /** 时间输出格式。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ISysNoticeReadService notices;

    /**
     * 构造系统只读工具提供者。
     *
     * @param notices 公告已读服务
     */
    public SystemReadToolProvider(ISysNoticeReadService notices) {
        this.notices = Objects.requireNonNull(notices);
    }

    /**
     * 返回领域标识。
     *
     * @return 领域标识
     */
    @Override
    public String domain() {
        return "system";
    }

    /**
     * 返回本人公告工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(noticeMyList());
    }

    /**
     * 本人公告分页列表。
     *
     * @return 工具定义
     */
    private ToolDefinition noticeMyList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("readState", Map.of("type", "string", "enum", List.of("all", "read", "unread"), "default",
                "all"));
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10000, "default", 1));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 20));
        return new ToolDefinition("system_notice_my_list", "1", "查询我的公告",
                "分页查询正常状态公告与当前账号已读状态，返回未读总数与正文纯文本摘要。",
                CAPABILITY_GROUP, new ToolInputSchema("object", properties, List.of(), false, null, null),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询我的公告", true, false, true, false, true),
                Duration.ofSeconds(10), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, FIELDS);
                    String readState = ToolArguments.optionalText(arguments, "readState", 16);
                    if (readState != null && !List.of("all", "read", "unread").contains(readState)) {
                        throw new IllegalArgumentException("参数取值不合法：readState");
                    }
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    String readState = ToolArguments.optionalText(arguments, "readState", 16);
                    String state = readState == null ? "all" : readState;
                    Long userId = currentUserId();
                    List<SysNotice> rows;
                    PageInfo<SysNotice> info;
                    try {
                        // 分页在 Service 的主查询处启动并清理，Provider 不再重复设置分页参数。
                        rows = notices.selectNoticePageWithReadStatus(userId, state, page, pageSize);
                        info = new PageInfo<>(rows);
                    } catch (ToolException exception) {
                        throw exception;
                    } catch (ServiceException exception) {
                        throw new ToolException("internal_error", "读取公告失败");
                    } catch (RuntimeException exception) {
                        throw new ToolException("internal_error", "读取公告失败");
                    }
                    Integer unreadCount = readUnreadCount(userId);
                    List<NoticeItem> items = rows.stream().map(SystemReadToolProvider::item).toList();
                    NoticePage data = new NoticePage(items, unreadCount);
                    return ToolResults.success(data, ToolArguments.pageOf(page, pageSize, items.size(), info.getTotal()),
                            info.isHasNextPage(), List.of(), "共 " + info.getTotal() + " 条公告");
                });
    }

    /**
     * 读取当前账号 ID。
     *
     * @return 当前账号 ID
     */
    private static Long currentUserId() {
        try {
            Long userId = SecurityUtils.getUserId();
            if (userId == null || userId <= 0) {
                throw new ToolException("tool_unavailable", "当前账号不可用");
            }
            return userId;
        } catch (ToolException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ToolException("tool_unavailable", "当前账号不可用");
        }
    }

    /**
     * 读取当前账号未读总数；失败时返回 null 而不是伪造 0。
     *
     * @param userId 当前账号
     * @return 未读总数或 null
     */
    private Integer readUnreadCount(Long userId) {
        try {
            return notices.selectUnreadCount(userId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 映射公告列表项，正文只保留纯文本摘要。
     *
     * @param notice 公告
     * @return 列表项
     */
    private static NoticeItem item(SysNotice notice) {
        return new NoticeItem(String.valueOf(notice.getNoticeId()), notice.getNoticeTitle(), notice.getNoticeType(),
                time(notice.getCreateTime()), notice.getIsRead(), summary(notice.getNoticeContent()));
    }

    /**
     * 把富文本正文净化为纯文本摘要。
     *
     * <p>先按既有策略净化，再交给 Jsoup 取文本；只读路径不修改原公告内容。</p>
     *
     * @param html 原始正文
     * @return 纯文本摘要
     */
    private static String summary(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = NoticeHtmlSanitizer.clean(html);
        String text = org.jsoup.Jsoup.parse(clean == null ? "" : clean).text().trim();
        if (text.isEmpty()) {
            return null;
        }
        int count = text.codePointCount(0, text.length());
        if (count <= SUMMARY_CHARS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, SUMMARY_CHARS));
    }

    /**
     * 把时间格式化为带偏移量的 ISO-8601。
     *
     * @param value 时间
     * @return 格式化文本；为空时返回 null
     */
    private static String time(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return TIME_FORMAT.format(value.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }
}
