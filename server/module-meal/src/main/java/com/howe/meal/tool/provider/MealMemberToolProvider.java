package com.howe.meal.tool.provider;

import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.model.ToolPage;
import com.howe.common.tool.model.ToolResult;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import com.howe.meal.service.IMealMemberService;
import com.howe.meal.tool.service.IMealReadService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 家庭成员只读工具。
 *
 * <p>只发布分页成员列表，字段限制为成员 ID、展示名、家庭角色键与状态；
 * 不返回账号名、手机号、邮箱、部门或完整权限。调用者必须具备家庭管理员角色。</p>
 */
public final class MealMemberToolProvider implements BusinessToolProvider {

    /** 能力分组：成员读取。 */
    private static final String GROUP = "meal.member.read";

    /** 列表结果预算。 */
    private static final int LIST_BYTES = 65536;

    /** 输入字段。 */
    private static final Set<String> FIELDS = Set.of("keyword", "page", "pageSize");

    private final IMealMemberService members;

    /**
     * 构造成员工具提供者。
     *
     * @param members 成员服务
     */
    public MealMemberToolProvider(IMealMemberService members) {
        this.members = members;
    }

    @Override
    public String domain() {
        return "meal";
    }

    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(memberList());
    }

    /** 成员列表。 */
    private ToolDefinition memberList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "maxLength", 100));
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10000, "default", 1));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 20));
        return new ToolDefinition("meal_member_list", "1", "查询家庭成员",
                "分页查询当前家庭的成员，只返回成员 ID、展示名、家庭角色键与状态；"
                        + "仅家庭管理员或内置平台管理员可调用。",
                GROUP, new ToolInputSchema("object", properties, List.of(), false, null, null),
                ToolAccess.ofRoles(Set.of("meal_manager", "admin")),
                new ToolDefinition.ToolAnnotations("查询家庭成员", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, FIELDS);
                    ToolArguments.optionalText(arguments, "keyword", 100);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    String keyword = ToolArguments.optionalText(arguments, "keyword", 100);
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    IMealReadService.PageResult<Map<String, Object>> result;
                    try {
                        result = members.listFamilyMembersForTool(keyword, page, pageSize);
                    } catch (ToolException exception) {
                        throw exception;
                    } catch (ServiceException exception) {
                        throw new ToolException("not_found_or_forbidden", "家庭成员不存在或无权访问");
                    } catch (RuntimeException exception) {
                        throw new ToolException("internal_error", "读取家庭成员失败");
                    }
                    ToolPage pageInfo = ToolArguments.pageOf(page, pageSize, result.items().size(), result.total());
                    return ToolResults.success(new ArrayList<>(result.items()), pageInfo, result.hasMore(), List.of(),
                            "共 " + result.total() + " 名家庭成员");
                });
    }
}
