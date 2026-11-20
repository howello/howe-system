package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.McpManagementService;

import com.howe.common.exception.ServiceException;
import com.howe.mcp.service.security.McpAuditContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            select c.id,c.client_id,c.client_name,c.registration_source,c.status,c.metadata_valid,
                   c.create_time,c.update_time,c.last_used_at,
                   (select count(*) from mcp_grant g where g.registered_client_id=c.id and g.status='0') grant_count
              from oauth2_registered_client c
            """;
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final JdbcRegisteredClientRepository clients;
    private final McpGrantService grants;

    /** 分页检索客户端；关键字为字面子串，不把百分号当通配符。 */
    @Override
    public Page<ClientView> clients(Query query) {
        Filter filter = filter(query);
        filter.keyword("c.client_name", "c.client_id");
        filter.equal("c.status", status(query.status()));
        return page(CLIENT_SELECT, "oauth2_registered_client c",
                filter, "c.create_time desc,c.id", this::clientRow);
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

    /** 停用会原子撤销现有授权；恢复仅改管理状态，不能恢复旧凭据。 */
    @Override
    public void clientStatus(String id, String value) {
        requireId(id);
        String checked = requiredStatus(value);
        transactions.executeWithoutResult(transaction -> {
            List<String> current = jdbc.query("select status from oauth2_registered_client where id=? for update",
                    (row, index) -> row.getString(1), id);
            if (current.isEmpty()) { throw missing(); }
            if ("1".equals(checked)) { McpClientLifecycle.invalidate(jdbc, id); }
            jdbc.update("update oauth2_registered_client set status=?,update_time=current_timestamp where id=?", checked, id);
        });
    }

    /** 全站授权查询明确要求后台方法权限，服务不从请求推定操作者身份。 */
    @Override
    public Page<GrantView> grants(Query query) {
        Filter filter = filter(query);
        filter.keyword("c.client_name", "c.client_id");
        filter.equal("g.status", status(query.status()));
        filter.equal("g.user_id", positive(query.userId()));
        return page("""
                select g.*,c.client_id,c.client_name,c.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id

                """, "mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id",
                filter, "g.update_time desc,g.grant_id desc", this::grantRow);
    }

    /** 返回最小授权状态，不公开加密载荷、token 或权限快照。 */
    @Override
    public GrantView grant(Long id) {
        if (id == null || id <= 0) { throw missing(); }
        List<GrantView> rows = jdbc.query("""
                select g.*,c.client_id,c.client_name,c.status client_status
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                  where g.grant_id=?
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
        String trimmed = value.trim();
        if (trimmed.length() > max) { throw new ServiceException("查询条件过长"); }
        return trimmed;
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

}
