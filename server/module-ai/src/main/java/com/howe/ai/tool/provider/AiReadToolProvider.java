package com.howe.ai.tool.provider;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.ai.capability.AiCapability;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import com.howe.ai.service.AiStatsPage;
import com.howe.ai.service.IAiCallLogService;
import com.howe.ai.service.IAiModelService;
import com.howe.ai.tool.dto.AiReadDtos.ModelView;
import com.howe.ai.tool.dto.AiReadDtos.UsageRow;
import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.model.ToolResult;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 只读工具提供者。
 *
 * <p>发布模型目录与用量统计两个工具。模型目录只暴露展示所需的四个字段，
 * 不含渠道、端点、密钥与附加参数；用量统计在数据库聚合主查询上分页，
 * 展示名只补齐当前页，费用不推断、固定为 null。</p>
 *
 * <p>本类只依赖 common 工具契约与本模块 Service，不引用 MCP、SDK 或 OAuth 类型。</p>
 */
public final class AiReadToolProvider implements BusinessToolProvider {

    /** 能力分组：模型目录。 */
    private static final String CATALOG_GROUP = "ai.catalog.read";

    /** 能力分组：用量日志。 */
    private static final String LOGS_GROUP = "ai.logs.read";

    /** 结果预算。 */
    private static final int RESULT_BYTES = 65536;

    /** 统计区间上限（天）。 */
    private static final int MAX_RANGE_DAYS = 31;

    /** 模型列表输入字段。 */
    private static final Set<String> MODEL_LIST_FIELDS = Set.of("capability", "status", "page", "pageSize");

    /** 统计输入字段。 */
    private static final Set<String> USAGE_FIELDS = Set.of("groupBy", "from", "to", "scene", "page", "pageSize");

    /** 时间/日期解析格式。 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final IAiModelService models;

    private final IAiCallLogService callLogs;

    /**
     * 构造 AI 只读工具提供者。
     *
     * @param models 模型服务
     * @param callLogs 调用记录服务
     */
    public AiReadToolProvider(IAiModelService models, IAiCallLogService callLogs) {
        this.models = Objects.requireNonNull(models);
        this.callLogs = Objects.requireNonNull(callLogs);
    }

    /**
     * 返回领域标识。
     *
     * @return 领域标识
     */
    @Override
    public String domain() {
        return "ai";
    }

    /**
     * 返回两个 AI 只读工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(modelList(), usageStats());
    }

    /**
     * 模型目录列表。
     *
     * @return 工具定义
     */
    private ToolDefinition modelList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("capability", Map.of("type", "string", "enum",
                Arrays.stream(AiCapability.values()).map(Enum::name).toList()));
        properties.put("status", Map.of("type", "string", "enum", List.of("enabled", "disabled")));
        paging(properties);
        return new ToolDefinition("ai_model_list", "1", "查询 AI 模型目录",
                "查询已配置模型的展示名、能力与启停状态；不返回渠道、端点、密钥或扩展参数。",
                CATALOG_GROUP, schema(properties, List.of()),
                ToolAccess.ofPermissions(Set.of("ai:model:list")),
                new ToolDefinition.ToolAnnotations("查询 AI 模型目录", true, false, true, false, true),
                Duration.ofSeconds(10), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, MODEL_LIST_FIELDS);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    capability(arguments);
                    modelStatus(arguments);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    AiModel query = new AiModel();
                    query.setCapability(capability(arguments));
                    query.setEnabled(modelStatus(arguments));
                    PageHelper.startPage(page, pageSize);
                    try {
                        List<AiModel> rows = read(() -> models.selectAiModelList(query), "AI 模型目录");
                        PageInfo<AiModel> info = new PageInfo<>(rows);
                        List<ModelView> data = rows.stream()
                                .map(row -> new ModelView(String.valueOf(row.getId()), row.getDisplayName(),
                                        row.getCapability(), Integer.valueOf(1).equals(row.getEnabled())
                                                ? "enabled" : "disabled"))
                                .toList();
                        return ToolResults.page(data,
                                ToolArguments.pageOf(page, pageSize, data.size(), info.getTotal()),
                                info.isHasNextPage(), "共 " + info.getTotal() + " 个模型");
                    } finally {
                        PageHelper.clearPage();
                    }
                });
    }

    /**
     * 用量统计。
     *
     * @return 工具定义
     */
    private ToolDefinition usageStats() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("groupBy", Map.of("type", "string", "enum", List.of("day", "scene", "model")));
        properties.put("from", Map.of("type", "string", "description", "起始日期，ISO-8601 本地日期，含"));
        properties.put("to", Map.of("type", "string", "description", "结束日期，ISO-8601 本地日期，不含"));
        properties.put("scene", Map.of("type", "string", "maxLength", 100));
        paging(properties);
        return new ToolDefinition("ai_usage_stats_get", "1", "查询 AI 用量统计",
                "按天、场景或模型聚合调用用量；区间左闭右开且最长 31 天，费用未接入厂商账单时返回 null。",
                LOGS_GROUP, schema(properties, List.of("groupBy", "from", "to")),
                ToolAccess.ofPermissions(Set.of("ai:calllog:stats")),
                new ToolDefinition.ToolAnnotations("查询 AI 用量统计", true, false, true, false, true),
                Duration.ofSeconds(10), RESULT_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, USAGE_FIELDS);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    String groupBy = ToolArguments.optionalText(arguments, "groupBy", 16);
                    if (groupBy == null || !List.of("day", "scene", "model").contains(groupBy)) {
                        throw new IllegalArgumentException("参数取值不合法：groupBy");
                    }
                    ToolArguments.optionalText(arguments, "scene", 100);
                    range(arguments);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    String groupBy = ToolArguments.optionalText(arguments, "groupBy", 16);
                    String scene = ToolArguments.optionalText(arguments, "scene", 100);
                    LocalDateTime[] range = range(arguments);
                    AiStatsPage result = read(() -> callLogs.selectStatsPage(groupBy,
                            java.util.Date.from(range[0].atZone(ZoneId.systemDefault()).toInstant()),
                            java.util.Date.from(range[1].atZone(ZoneId.systemDefault()).toInstant()), scene, page,
                            pageSize), "AI 用量统计");
                    List<UsageRow> data = result.items().stream()
                            .map(row -> new UsageRow(row.getGroupKey(), row.getGroupLabel(), row.getTotalCount(),
                                    row.getSuccessCount(), row.getFailedCount(), row.getInputTokens(),
                                    row.getOutputTokens(), row.getImageCount(), row.getAvgElapsedMs(), null))
                            .toList();
                    ToolResult response = ToolResults.success(data,
                            ToolArguments.pageOf(page, pageSize, data.size(), result.total()), result.hasMore(),
                            List.of(), "共 " + result.total() + " 个统计分组");
                    return response;
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
     * 读取并校验能力类型。
     *
     * @param arguments 入参
     * @return 能力枚举名；未提供时为 null
     */
    private static String capability(Map<String, Object> arguments) {
        String value = ToolArguments.optionalText(arguments, "capability", 32);
        if (value == null) {
            return null;
        }
        for (AiCapability capability : AiCapability.values()) {
            if (capability.name().equals(value)) {
                return value;
            }
        }
        throw new IllegalArgumentException("参数取值不合法：capability");
    }

    /**
     * 读取并转换启停状态。
     *
     * @param arguments 入参
     * @return 库中值 {@code 1}/{@code 0}；未提供时为 null
     */
    private static Integer modelStatus(Map<String, Object> arguments) {
        String value = ToolArguments.optionalText(arguments, "status", 16);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "enabled" -> 1;
            case "disabled" -> 0;
            default -> throw new IllegalArgumentException("参数取值不合法：status");
        };
    }

    /**
     * 解析并校验统计区间。
     *
     * <p>{@code from} 含、{@code to} 不含；跨度必须大于 0 且不超过 31 天。</p>
     *
     * @param arguments 入参
     * @return 长度 2 的数组：起始与结束时间
     */
    private static LocalDateTime[] range(Map<String, Object> arguments) {
        String from = ToolArguments.optionalText(arguments, "from", 32);
        String to = ToolArguments.optionalText(arguments, "to", 32);
        if (from == null || to == null) {
            throw new IllegalArgumentException("from 与 to 必须同时提供");
        }
        LocalDate startDate = parseDate(from);
        LocalDate endDate = parseDate(to);
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("from 必须早于 to");
        }
        if (startDate.plusDays(MAX_RANGE_DAYS).isBefore(endDate)) {
            throw new IllegalArgumentException("统计区间不能超过 31 天");
        }
        return new LocalDateTime[] { startDate.atStartOfDay(), endDate.atStartOfDay() };
    }

    /**
     * 解析本地日期。
     *
     * @param value 日期文本
     * @return 日期
     */
    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日期格式必须为 ISO-8601 本地日期");
        }
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
