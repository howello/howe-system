package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.McpManagementService;

import com.howe.common.exception.ServiceException;
import com.howe.mcp.service.security.McpAuditContext;
import com.howe.mcp.tool.McpToolDefinition;
import com.howe.mcp.tool.McpToolRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.transaction.support.TransactionTemplate;

/** 后台管理的白名单查询和状态操作；不返回客户端密钥、授权载荷或令牌索引。 */
@Slf4j
@RequiredArgsConstructor
public final class McpManagementServiceImpl implements McpManagementService {
    private static final String CLIENT_SELECT = """
            select c.id,c.client_id,c.client_name,e.registration_source,e.status,e.metadata_valid,
                   e.create_time,e.update_time,e.last_used_at,
                   (select count(*) from mcp_grant g where g.registered_client_id=c.id and g.status='0') grant_count
              from oauth2_registered_client c join mcp_client_ext e on e.registered_client_id=c.id
            """;
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final JdbcRegisteredClientRepository clients;
    private final McpGrantService grants;
    private final McpToolRegistry tools;

    /** 分页检索客户端；关键字为字面子串，不把百分号当通配符。 */
    @Override
    public Page<ClientView> clients(Query query) {
        Filter filter = filter(query);
        filter.keyword("c.client_name", "c.client_id");
        filter.equal("e.status", status(query.status()));
        return page(CLIENT_SELECT, "oauth2_registered_client c join mcp_client_ext e on e.registered_client_id=c.id",
                filter, "e.create_time desc,c.id", this::clientRow);
    }

    /** 详情明确选择可公开的客户端元数据，不序列化框架 RegisteredClient。 */
    @Override
    public ClientDetail client(String id) {
        requireId(id);
        List<ClientView> rows = jdbc.query(CLIENT_SELECT + " where c.id=?", this::clientRow, id);
        if (rows.isEmpty()) { throw missing(); }
        var registered = clients.findById(id);
        if (registered == null) { throw missing(); }
        return new ClientDetail(rows.get(0), registered.getRedirectUris(),
                registered.getClientAuthenticationMethods().stream().map(value -> value.getValue()).sorted().toList(),
                registered.getAuthorizationGrantTypes().stream().map(value -> value.getValue()).sorted().toList(),
                registered.getScopes().stream().sorted().toList());
    }

    /** 停用会原子撤销现有授权及待确认 state；恢复仅改管理状态，不能恢复旧凭据。 */
    @Override
    public void clientStatus(String id, String value) {
        requireId(id);
        String checked = requiredStatus(value);
        transactions.executeWithoutResult(transaction -> {
            List<String> current = jdbc.query("select status from mcp_client_ext where registered_client_id=? for update",
                    (row, index) -> row.getString(1), id);
            if (current.isEmpty()) { throw missing(); }
            if ("1".equals(checked)) { McpClientLifecycle.invalidate(jdbc, id); }
            jdbc.update("update mcp_client_ext set status=?,update_time=current_timestamp where registered_client_id=?", checked, id);
        });
    }

    /** 目录仅来自实际注册代码；数据库遗留或任意新增名称不会成为可调用工具。 */
    @Override
    public List<ToolView> tools() {
        return tools.catalog().stream().map(definition -> {
            List<ToolState> states = jdbc.query("select enabled,update_by,update_time,remark from mcp_tool_config where tool_name=?",
                    (row, index) -> new ToolState(row.getString(1), row.getString(2), time(row.getTimestamp(3)), row.getString(4)),
                    definition.schema().name());
            ToolState state = states.isEmpty() ? new ToolState("N", "", null, "") : states.get(0);
            return new ToolView(definition.schema(), definition.version(), definition.resolvedScope(), definition.permissions(),
                    definition.timeout().toMillis(), definition.maxResultBytes(), state.enabled(), state.updateBy(), state.updatedAt(), state.remark());
        }).toList();
    }

    /** 单个工具详情采用代码目录，未知名称直接拒绝。 */
    @Override
    public ToolView tool(String name) {
        return tools().stream().filter(value -> value.schema().name().equals(name)).findFirst().orElseThrow(McpManagementServiceImpl::missing);
    }

    /** 只允许切换代码中已有工具，不接受 schema、权限、实现或回调的编辑。 */
    @Override
    public void toolStatus(String name, String enabled, String operator) {
        if (!"Y".equals(enabled) && !"N".equals(enabled)) { throw new ServiceException("工具状态无效"); }
        if (operator == null || operator.isBlank() || operator.length() > 64) { throw new ServiceException("操作账号无效"); }
        if (tools.catalog().stream().map(McpToolDefinition::schema).noneMatch(value -> value.name().equals(name))) { throw missing(); }
        int changed = jdbc.update("update mcp_tool_config set enabled=?,update_by=?,update_time=current_timestamp where tool_name=?", enabled, operator, name);
        if (changed != 1) { throw new ServiceException("工具配置缺失，请完成安装脚本后重试"); }
    }

    /** 全站授权查询明确要求后台方法权限，服务不从请求推定操作者身份。 */
    @Override
    public Page<GrantView> grants(Query query) {
        Filter filter = filter(query);
        filter.keyword("c.client_name", "c.client_id");
        filter.equal("g.status", status(query.status()));
        filter.equal("g.user_id", positive(query.userId()));
        return page("""
                select g.*,c.client_id,c.client_name,e.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                  join mcp_client_ext e on e.registered_client_id=c.id
                """, "mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id join mcp_client_ext e on e.registered_client_id=c.id",
                filter, "g.update_time desc,g.grant_id desc", this::grantRow);
    }

    /** 返回最小授权状态，不公开加密载荷、token 或权限快照。 */
    @Override
    public GrantView grant(Long id) {
        if (id == null || id <= 0) { throw missing(); }
        List<GrantView> rows = jdbc.query("""
                select g.*,c.client_id,c.client_name,e.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                  join mcp_client_ext e on e.registered_client_id=c.id where g.grant_id=?
                """, this::grantRow, id);
        if (rows.isEmpty()) { throw missing(); }
        return rows.get(0);
    }

    /** 管理员撤销仍复用相同事务服务；清除专属审计上下文，避免进入旧 JWT 线程后残留。 */
    @Override
    public void revoke(Long id) {
        Long userId = grant(id).userId();
        try { grants.revokeOwned(userId, id); }
        finally { McpAuditContext.clear(); }
    }

    /** 调用查询只选取允许公开的元数据，绝不读取 summary 或原始协议。 */
    @Override
    public Page<CallView> calls(Query query) {
        Filter filter = filter(query);
        filter.keyword("c.client_name", "c.client_id");
        filter.equal("l.user_id", positive(query.userId()));
        filter.equal("l.tool_name", text(query.toolName(), 64));
        filter.equal("l.result_code", text(query.resultCode(), 64));
        return page("""
                select l.log_id,l.request_id,l.event_type,l.user_id,l.grant_id,l.tool_name,l.result_code,l.elapsed_ms,l.create_time,c.client_id,c.client_name
                  from mcp_call_log l left join oauth2_registered_client c on c.id=l.registered_client_id
                """, "mcp_call_log l left join oauth2_registered_client c on c.id=l.registered_client_id", filter,
                "l.create_time desc,l.log_id desc", this::callRow);
    }

    /** 单条审计详情与列表采用相同字段白名单。 */
    @Override
    public CallView call(Long id) {
        if (id == null || id <= 0) { throw missing(); }
        List<CallView> rows = jdbc.query("""
                select l.log_id,l.request_id,l.event_type,l.user_id,l.grant_id,l.tool_name,l.result_code,l.elapsed_ms,l.create_time,c.client_id,c.client_name
                  from mcp_call_log l left join oauth2_registered_client c on c.id=l.registered_client_id where l.log_id=?
                """, this::callRow, id);
        if (rows.isEmpty()) { throw missing(); }
        return rows.get(0);
    }

    /** 手工 JDBC 分页不依赖 PageHelper 拦截器，保持后台统一 rows/total 响应。 */
    private <T> Page<T> page(String select, String from, Filter filter, String order, org.springframework.jdbc.core.RowMapper<T> mapper) {
        Long count = jdbc.queryForObject("select count(*) from " + from + filter.where, Long.class, filter.parameters.toArray());
        List<Object> values = new ArrayList<>(filter.parameters);
        values.add(filter.size); values.add((long) (filter.page - 1) * filter.size);
        return new Page<>(jdbc.query(select + filter.where + " order by " + order + " limit ? offset ?", mapper, values.toArray()), count == null ? 0 : count);
    }

    /** 约束单页、偏移和所有查询文本，不允许客户端提供排序 SQL。 */
    private static Filter filter(Query query) {
        int page = query.pageNum() == null ? 1 : query.pageNum();
        int size = query.pageSize() == null ? 20 : query.pageSize();
        if (page < 1 || page > 10000 || size < 1 || size > 100) { throw new ServiceException("分页参数无效"); }
        return new Filter(page, size, text(query.keyword(), 128));
    }

    /** 可选状态只接受已定义枚举。 */
    private static String status(String value) { return value == null || value.isBlank() ? null : requiredStatus(value); }
    /** 状态更新不能省略状态或传入其它字段值。 */
    private static String requiredStatus(String value) {
        if (!"0".equals(value) && !"1".equals(value)) { throw new ServiceException("客户端或授权状态无效"); }
        return value;
    }
    /** 查询文本保留字面语义并限制长度。 */
    private static String text(String value, int max) {
        if (value == null || value.isBlank()) { return null; }
        if (value.length() > max) { throw new ServiceException("查询条件过长"); }
        return value;
    }
    /** 可选账号主键不能是非正数。 */
    private static Long positive(Long value) {
        if (value != null && value <= 0) { throw new ServiceException("账号主键无效"); }
        return value;
    }
    /** 框架登记主键不是外部 URL，不允许无限长路径输入。 */
    private static void requireId(String value) {
        if (value == null || value.isBlank() || value.length() > 100) { throw missing(); }
    }
    /** 不区分不同内部表缺失原因。 */
    private static ServiceException missing() { return new ServiceException("记录不存在或配置不可用"); }
    /** 明确返回 ISO 时间，避免后台全局日期格式改变 Instant 序列化。 */
    private static String time(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    /** 客户端查询只映射公开字段。 */
    private ClientView clientRow(ResultSet row, int index) throws SQLException {
        return new ClientView(row.getString("id"), row.getString("client_id"), row.getString("client_name"), row.getString("registration_source"),
                row.getString("status"), row.getString("metadata_valid"), row.getLong("grant_count"), time(row.getTimestamp("create_time")),
                time(row.getTimestamp("update_time")), time(row.getTimestamp("last_used_at")));
    }
    /** 授权查询不从客户端传入用户信息。 */
    private GrantView grantRow(ResultSet row, int index) throws SQLException {
        return new GrantView(row.getLong("grant_id"), row.getLong("user_id"), row.getString("client_id"), row.getString("client_name"),
                row.getString("resource_uri"), row.getString("status"), row.getString("client_status"), row.getLong("authorization_version"),
                time(row.getTimestamp("create_time")), time(row.getTimestamp("update_time")), time(row.getTimestamp("revoked_at")));
    }
    /** 日志详情不映射任何正文。 */
    private CallView callRow(ResultSet row, int index) throws SQLException {
        return new CallView(row.getLong("log_id"), row.getString("request_id"), row.getString("event_type"), row.getObject("user_id", Long.class),
                row.getObject("grant_id", Long.class), row.getString("client_id"), row.getString("client_name"), row.getString("tool_name"),
                row.getString("result_code"), row.getLong("elapsed_ms"), time(row.getTimestamp("create_time")));
    }

    /** 绑定参数式筛选器，SQL 列名只能由本类常量提供。 */
    private static final class Filter {
        private final int page; private final int size; private final String keyword;
        private final StringBuilder where = new StringBuilder(" where 1=1");
        private final List<Object> parameters = new ArrayList<>();
        /** 保存已校验分页与字面关键字。 */
        private Filter(int page, int size, String keyword) { this.page = page; this.size = size; this.keyword = keyword; }
        /** 仅绑定值，不拼接用户文本。 */
        private void equal(String column, Object value) {
            if (value != null) { where.append(" and ").append(column).append("=?"); parameters.add(value); }
        }
        /** 标识列先转为连接字符集，避免中文关键字被 ASCII 标识列强制转换；仍采用绑定的字面子串。 */
        private void keyword(String first, String second) {
            if (keyword != null) {
                where.append(" and (locate(?,").append(first).append(")>0 or locate(?,cast(").append(second)
                        .append(" as char(2048)))>0)");
                parameters.add(keyword); parameters.add(keyword);
            }
        }
    }

    /** 工具数据库状态不包含实现或动态脚本。 */
    private record ToolState(String enabled, String updateBy, String updatedAt, String remark) { }
}
