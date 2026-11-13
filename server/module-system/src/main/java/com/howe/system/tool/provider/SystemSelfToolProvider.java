package com.howe.system.tool.provider;

import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import com.howe.common.utils.SecurityUtils;
import com.howe.system.service.ISysDeptService;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 当前账号最小信息工具。
 *
 * <p>只读取已重新加载的当前 {@link LoginUser}，不接受任何身份参数，
 * 不返回用户名、联系方式、权限清单、token 或后台路由。
 * 本类只依赖 common 工具契约与本模块 Service，不引用 MCP、SDK 或 OAuth 类型。</p>
 */
public final class SystemSelfToolProvider implements BusinessToolProvider {

    /** 工具名。 */
    private static final String TOOL_NAME = "system_self_get";

    /** 能力分组，由平台映射到 OAuth scope。 */
    private static final String CAPABILITY_GROUP = "system.self.read";

    /** 家庭部门类型。 */
    private static final String FAMILY_DEPT_TYPE = "F";

    private final ISysDeptService depts;

    /**
     * 构造本人信息工具提供者。
     *
     * @param depts 部门服务，用于判断是否属于有效家庭
     */
    public SystemSelfToolProvider(ISysDeptService depts) {
        this.depts = Objects.requireNonNull(depts);
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
     * 返回本人信息工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(self());
    }

    /**
     * 构造无参数本人信息工具。
     *
     * @return 工具定义
     */
    private ToolDefinition self() {
        return new ToolDefinition(TOOL_NAME, "1", "读取本人信息",
                "返回当前 MCP 授权账号的最小身份信息，不访问其他用户，不接受身份参数。",
                CAPABILITY_GROUP, ToolInputSchema.emptyObject(), ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("读取本人信息", true, false, true, false, true),
                Duration.ofSeconds(5), 8192,
                ToolArguments::requireEmpty,
                (context, arguments) -> {
                    LoginUser loginUser;
                    try {
                        loginUser = SecurityUtils.getLoginUser();
                    } catch (RuntimeException exception) {
                        throw new ToolException("tool_unavailable", "当前账号不可用");
                    }
                    if (loginUser == null || loginUser.getUser() == null) {
                        throw new ToolException("tool_unavailable", "当前账号不可用");
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("userId", String.valueOf(loginUser.getUserId()));
                    data.put("displayName", loginUser.getUser().getNickName());
                    data.put("roles", roleKeys(loginUser));
                    data.put("hasFamily", hasFamily(loginUser));
                    return ToolResults.detail(data, "已返回当前账号信息");
                });
    }

    /**
     * 提取去重排序后的角色键。
     *
     * @param loginUser 当前账号
     * @return 角色键数组
     */
    private static List<String> roleKeys(LoginUser loginUser) {
        if (loginUser.getUser().getRoles() == null) {
            return List.of();
        }
        return loginUser.getUser().getRoles().stream()
                .filter(Objects::nonNull)
                .map(SysRole::getRoleKey)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 判断当前账号是否属于有效家庭部门。
     *
     * <p>不能简单用 deptId 非空判断：系统部门同样有 deptId。这里按部门类型
     * {@code F}、未删除、启用三个条件判断；查询失败时按 false 处理，不把普通部门标成家庭。</p>
     *
     * @param loginUser 当前账号
     * @return 属于有效家庭时返回 true
     */
    private boolean hasFamily(LoginUser loginUser) {
        Long deptId = loginUser.getDeptId();
        if (deptId == null || deptId <= 0) {
            return false;
        }
        try {
            var dept = depts.selectDeptById(deptId);
            return dept != null && FAMILY_DEPT_TYPE.equals(dept.getDeptType())
                    && "0".equals(dept.getDelFlag()) && "0".equals(dept.getStatus());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
