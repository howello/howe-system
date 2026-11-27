package com.howe.pregnancy.constant;

import java.util.Set;

/**
 * 孕期助手常量
 *
 * <p>目标模型为单家庭、单次孕期，业务数据全站共享。访问资格由现有系统账号状态与
 * 当前有效的孕期角色决定，不再有档案、成员与归档状态。</p>
 *
 * <p>角色键与 {@code sys_role.role_key} 一致；同时拥有多个孕期角色时按能力并集处理。</p>
 *
 * @author howe
 */
public final class PregnancyConstants {

    /** 角色键：孕期管理员 */
    public static final String ROLE_MANAGER = "pregnancy_manager";

    /** 全部孕期角色 */
    public static final Set<String> ALL_ROLES = Set.of(ROLE_MANAGER);

    /** 可写业务记录的角色 */
    public static final Set<String> WRITABLE_ROLES = ALL_ROLES;

    /** 业务时区，首版固定 */
    public static final String TIMEZONE = "Asia/Shanghai";

    /** 预产期当天对应的孕日 */
    public static final int GESTATION_DAYS_AT_DUE = 280;

    /** 37 周对应的孕日 */
    public static final int DAYS_OF_FULL_TERM = 37 * 7;

    /** 每日内容素材覆盖的最小孕日 */
    public static final int GROWTH_DAY_MIN = 1;

    /** 每日内容素材覆盖的最大孕日 */
    public static final int GROWTH_DAY_MAX = 294;

    /** 周概况覆盖的最小孕周 */
    public static final int GROWTH_WEEK_MIN = 0;

    /** 周概况覆盖的最大孕周 */
    public static final int GROWTH_WEEK_MAX = 42;

    /** 每日变化查询单次最大天数 */
    public static final int GROWTH_MAX_SPAN_DAYS = 31;

    /** 月摘要允许的最大自然日跨度 */
    public static final int CALENDAR_MAX_SPAN_DAYS = 62;

    /** 统计默认回看天数 */
    public static final int STATISTICS_DEFAULT_DAYS = 14;

    /** 分页默认与上限 */
    public static final int PAGE_SIZE_DEFAULT = 20;

    /** 分页最大条数 */
    public static final int PAGE_SIZE_MAX = 100;

    /** 产检状态：已计划 */
    public static final String CHECKUP_PLANNED = "PLANNED";

    /** 产检状态：已预约 */
    public static final String CHECKUP_BOOKED = "BOOKED";

    /** 产检状态：已完成 */
    public static final String CHECKUP_COMPLETED = "COMPLETED";

    /** 产检状态：已取消 */
    public static final String CHECKUP_CANCELLED = "CANCELLED";

    /** 产检详情类型：项目 */
    public static final String DETAIL_ITEM = "ITEM";

    /** 产检详情类型：准备事项 */
    public static final String DETAIL_PREPARATION = "PREPARATION";

    /** 文件状态：临时 */
    public static final String FILE_TEMP = "TEMP";

    /** 文件状态：已绑定 */
    public static final String FILE_BOUND = "BOUND";

    /** 文件状态：已解绑待回收 */
    public static final String FILE_ORPHAN = "ORPHAN";

    /** 文件状态：回收中 */
    public static final String FILE_DELETING = "DELETING";

    /** 文件所有者：报告 */
    public static final String FILE_OWNER_REPORT = "REPORT";

    /** 文件所有者：饮食 */
    public static final String FILE_OWNER_DIET = "DIET";

    /** 任务类型：提醒 */
    public static final String TASK_REMINDER = "REMINDER";

    /** 任务类型：待办每日提醒 */
    public static final String TASK_TODO_REMINDER = "TODO_REMINDER";

    /** 任务类型：按孕期提醒规则周期发送 */
    public static final String TASK_RULE_REMINDER = "RULE_REMINDER";

    /** 判断任务是否走企微提醒的发送与恢复流程。 */
    public static boolean isReminderTask(String kind) {
        return TASK_REMINDER.equals(kind) || TASK_TODO_REMINDER.equals(kind) || TASK_RULE_REMINDER.equals(kind);
    }

    /** 任务类型：物品 AI 内容 */
    public static final String TASK_ITEM_CONTENT = "ITEM_CONTENT";

    /** 任务类型：导出 */
    public static final String TASK_EXPORT = "EXPORT";

    /** 任务状态：待执行 */
    public static final String TASK_PENDING = "PENDING";

    /** 任务状态：执行中 */
    public static final String TASK_RUNNING = "RUNNING";

    /** 任务状态：成功 */
    public static final String TASK_SUCCEEDED = "SUCCEEDED";

    /** 任务状态：等待重试 */
    public static final String TASK_RETRY_WAIT = "RETRY_WAIT";

    /** 任务状态：失败 */
    public static final String TASK_FAILED = "FAILED";

    /** 任务状态：已取消 */
    public static final String TASK_CANCELLED = "CANCELLED";

    /** 任务状态：跳过 */
    public static final String TASK_SKIPPED = "SKIPPED";

    /** 任务状态：结果不明 */
    public static final String TASK_UNKNOWN = "UNKNOWN";

    /** 费用来源：产检 */
    public static final String EXPENSE_SOURCE_CHECKUP = "CHECKUP";

    /** 费用来源：报告 */
    public static final String EXPENSE_SOURCE_REPORT = "REPORT";

    /** 费用来源：待产包物品 */
    public static final String EXPENSE_SOURCE_BAG_ITEM = "BAG_ITEM";

    /** 待产包金额槽位 */
    public static final String EXPENSE_SLOT_TOTAL = "TOTAL";

    /** 物品分类：妈妈 */
    public static final String BAG_CATEGORY_MOTHER = "MOTHER";

    /** 物品分类：宝宝 */
    public static final String BAG_CATEGORY_BABY = "BABY";

    /** 默认待产包稳定键 */
    public static final String DEFAULT_BAG_SEED = "default";

    /** 统一统计聚合：原始点 */
    public static final String AGGREGATION_RAW = "RAW";

    /** 统一统计聚合：按日最后一条 */
    public static final String AGGREGATION_DAY_LAST = "DAY_LAST";

    /** 统一统计聚合：按日求和 */
    public static final String AGGREGATION_DAY_SUM = "DAY_SUM";

    /** 日历标记：测量 */
    public static final String MARKER_MEASUREMENT = "MEASUREMENT";

    /** 日历标记：产检 */
    public static final String MARKER_CHECKUP = "CHECKUP";

    /** 日历标记：报告 */
    public static final String MARKER_REPORT = "REPORT";

    /** 日历标记：饮食 */
    public static final String MARKER_DIET = "DIET";

    /** 日历标记：待办 */
    public static final String MARKER_TODO = "TODO";

    /** 错误码：校验失败 */
    public static final String ERROR_VALIDATION = "VALIDATION_ERROR";

    /** 错误码：实际记录不得晚于业务今天 */
    public static final String ERROR_FUTURE_RECORD = "FUTURE_RECORD_NOT_ALLOWED";

    /** 错误码：未认证 */
    public static final String ERROR_UNAUTHENTICATED = "UNAUTHENTICATED";

    /** 错误码：角色不足 */
    public static final String ERROR_FORBIDDEN = "FORBIDDEN";

    /** 错误码：资源不存在或无权访问 */
    public static final String ERROR_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

    /** 错误码：版本冲突 */
    public static final String ERROR_VERSION_CONFLICT = "VERSION_CONFLICT";

    /** 错误码：幂等冲突 */
    public static final String ERROR_IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";

    /** 错误码：非法状态转换 */
    public static final String ERROR_INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";

    /** 错误码：资源被占用 */
    public static final String ERROR_RESOURCE_IN_USE = "RESOURCE_IN_USE";

    /** 错误码：文件过大 */
    public static final String ERROR_FILE_TOO_LARGE = "FILE_TOO_LARGE";

    /** 错误码：不支持的文件类型 */
    public static final String ERROR_UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";

    /** 错误码：导出过期 */
    public static final String ERROR_EXPORT_EXPIRED = "EXPORT_EXPIRED";

    /** 错误码：限流 */
    public static final String ERROR_RATE_LIMITED = "RATE_LIMITED";

    /** 错误码：内部错误 */
    public static final String ERROR_INTERNAL = "INTERNAL_ERROR";

    /** 错误码：通知结果不明 */
    public static final String ERROR_DELIVERY_UNKNOWN = "DELIVERY_UNKNOWN";

    /** 配置键：末次月经日期 */
    public static final String CONFIG_LAST_MENSTRUAL_PERIOD = "pregnancy.lastMenstrualPeriod";

    /**
     * 工具类不允许实例化
     */
    private PregnancyConstants() {
    }
}
