package com.howe.share.tool.provider;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.model.ToolResult;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import com.howe.share.domain.ShareLink;
import com.howe.share.service.IShareLinkService;
import com.howe.share.tool.dto.ShareReadDtos.LinkView;
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
 * 分享只读工具提供者。
 *
 * <p>沿用现有管理范围：持有 {@code share:link:list} 或 {@code share:link:query} 的账号
 * 可读取对应管理资源，本 change 不新增 owner 隔离。只走管理 Service，
 * 不调用公开的 open/file/download 路径，因此不产生访问计数、阅后即焚或缓存变化。</p>
 *
 * <p>本类只依赖 common 工具契约与本模块 Service，不引用 MCP、SDK 或 OAuth 类型。</p>
 */
public final class ShareReadToolProvider implements BusinessToolProvider {

    /** 能力分组。 */
    private static final String CAPABILITY_GROUP = "share.read";

    /** 结果预算：列表。 */
    private static final int LIST_BYTES = 65536;

    /** 结果预算：详情。 */
    private static final int DETAIL_BYTES = 131072;

    /** 列表输入字段。 */
    private static final Set<String> LIST_FIELDS = Set.of("keyword", "status", "page", "pageSize");

    /** 详情输入字段。 */
    private static final Set<String> GET_FIELDS = Set.of("linkId");

    /** 状态映射：对外枚举 → 库中值。 */
    private static final Map<String, String> STATUS_INPUT = Map.of("enabled", "0", "disabled", "1");

    /** 状态映射：库中值 → 对外枚举。 */
    private static final Map<String, String> STATUS_OUTPUT = Map.of("0", "enabled", "1", "disabled", "2", "burned",
            "3", "deleting");

    /** 时间输出格式。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final IShareLinkService links;

    /**
     * 构造分享只读工具提供者。
     *
     * @param links 分享管理服务
     */
    public ShareReadToolProvider(IShareLinkService links) {
        this.links = Objects.requireNonNull(links);
    }

    /**
     * 返回领域标识。
     *
     * @return 领域标识
     */
    @Override
    public String domain() {
        return "share";
    }

    /**
     * 返回两个分享只读工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(linkList(), linkGet());
    }

    /**
     * 分享列表。
     *
     * @return 工具定义
     */
    private ToolDefinition linkList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "maxLength", 100));
        properties.put("status", Map.of("type", "string", "enum", List.of("enabled", "disabled")));
        paging(properties);
        return new ToolDefinition("share_link_list", "1", "查询分享列表",
                "查询当前账号管理范围内的分享记录；不返回短码、短链、远端地址、存储路径或缓存错误。",
                CAPABILITY_GROUP, schema(properties, List.of()),
                ToolAccess.ofPermissions(Set.of("share:link:list")),
                new ToolDefinition.ToolAnnotations("查询分享列表", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, LIST_FIELDS);
                    ToolArguments.optionalText(arguments, "keyword", 100);
                    status(arguments);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    ShareLink query = new ShareLink();
                    query.setFileName(ToolArguments.optionalText(arguments, "keyword", 100));
                    query.setStatus(status(arguments));

                    PageHelper.startPage(page, pageSize);
                    try {
                        List<ShareLink> rows = read(() -> links.selectShareLinkList(query), "分享列表");
                        PageInfo<ShareLink> info = new PageInfo<>(rows);
                        List<LinkView> data = rows.stream().map(ShareReadToolProvider::view).toList();
                        return ToolResults.page(data,
                                ToolArguments.pageOf(page, pageSize, data.size(), info.getTotal()),
                                info.isHasNextPage(), "共 " + info.getTotal() + " 条分享");
                    } finally {
                        PageHelper.clearPage();
                    }
                });
    }

    /**
     * 分享详情。
     *
     * @return 工具定义
     */
    private ToolDefinition linkGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("linkId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$"));
        return new ToolDefinition("share_link_get", "1", "读取分享详情",
                "读取单条分享的管理字段；不消费访问次数、不触发缓存或公开下载。",
                CAPABILITY_GROUP, schema(properties, List.of("linkId")),
                ToolAccess.ofPermissions(Set.of("share:link:query")),
                new ToolDefinition.ToolAnnotations("读取分享详情", true, false, true, false, true),
                Duration.ofSeconds(10), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, GET_FIELDS);
                    ToolArguments.requiredId(arguments, "linkId");
                },
                (context, arguments) -> {
                    Long linkId = ToolArguments.requiredId(arguments, "linkId");
                    ShareLink link = read(() -> links.selectShareLinkById(linkId), "分享");
                    if (link == null) {
                        throw new ToolException("not_found_or_forbidden", "分享不存在或无权访问");
                    }
                    return ToolResults.detail(view(link), "已返回分享详情");
                });
    }

    /**
     * 构造输入结构。
     *
     * @param properties 属性树
     * @param required 必填字段
     * @return 输入结构
     */
    private static ToolInputSchema schema(Map<String, Object> properties, List<String> required) {
        return new ToolInputSchema("object", properties, required, false, null, null);
    }

    /**
     * 加入分页属性。
     *
     * @param properties 属性树
     */
    private static void paging(Map<String, Object> properties) {
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10000, "default", 1));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 20));
    }

    /**
     * 读取状态入参并转换为库中值。
     *
     * @param arguments 入参
     * @return 库中状态；未提供时为 null
     */
    private static String status(Map<String, Object> arguments) {
        String value = ToolArguments.optionalText(arguments, "status", 16);
        if (value == null) {
            return null;
        }
        String mapped = STATUS_INPUT.get(value);
        if (mapped == null) {
            throw new IllegalArgumentException("参数取值不合法：status");
        }
        return mapped;
    }

    /**
     * 映射分享视图。
     *
     * @param link 分享
     * @return 视图
     */
    private static LinkView view(ShareLink link) {
        String displayName = link.getFileName() == null || link.getFileName().isBlank()
                ? "分享 #" + link.getLinkId() : link.getFileName();
        String status = link.getStatus() == null ? "unknown"
                : STATUS_OUTPUT.getOrDefault(link.getStatus(), "unknown");
        return new LinkView(String.valueOf(link.getLinkId()), displayName, link.getSourceType(), status,
                link.getPasswordSet(), time(link.getExpireAt()), link.getMaxViews(), link.getViewCount(),
                "1".equals(link.getBurnAfterRead()), link.getCacheStatus());
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

    /**
     * 执行只读查询并把业务异常收敛为稳定工具错误。
     *
     * @param action 查询动作
     * @param label 对象名称
     * @param <T> 返回类型
     * @return 查询结果
     */
    private static <T> T read(java.util.function.Supplier<T> action, String label) {
        try {
            return action.get();
        } catch (ToolException exception) {
            throw exception;
        } catch (ServiceException exception) {
            throw new ToolException("not_found_or_forbidden", label + "不存在或无权访问");
        } catch (RuntimeException exception) {
            throw new ToolException("internal_error", "读取" + label + "失败");
        }
    }
}
