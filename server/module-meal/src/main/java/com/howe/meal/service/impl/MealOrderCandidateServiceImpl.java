package com.howe.meal.service.impl;

import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderCandidate;
import com.howe.meal.domain.vo.MealOrderAssignee;
import com.howe.meal.mapper.MealOrderCandidateMapper;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.service.IMealNotifyService;
import com.howe.meal.service.IMealOrderCandidateService;
import com.howe.meal.service.IMealWeComNotifyService;
import com.howe.meal.service.ITransactionCommitExecutor;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 点餐-订单候选厨师 服务层实现
 *
 * <p>候选人只包含本家庭拥有 {@code meal_chef} 角色的启用成员，家庭管理员不进入候选池。
 * 接单资格以候选人状态为准，订单是否还能被接由订单状态的条件更新裁决。</p>
 *
 * <p>拒绝流程必须先把订单行锁住再判断「是否已无待响应候选人」：不锁行时，
 * 两名候选人并发拒绝会各自读到对方仍待响应的快照，结果谁都不关单，订单一直挂到超时。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealOrderCandidateServiceImpl implements IMealOrderCandidateService {

    /** 指派被拒 / 超时后转公开池 */
    private static final String STRATEGY_REOPEN = "1";

    /** 默认超时分钟数：3 小时 */
    private static final int DEFAULT_TIMEOUT_MINUTES = 180;

    private final MealOrderMapper mealOrderMapper;
    private final MealOrderCandidateMapper candidateMapper;
    private final IMealNotifyService mealNotifyService;
    private final IMealWeComNotifyService mealWeComNotifyService;
    private final ITransactionCommitExecutor transactionCommitExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MealOrderCandidate> createCandidates(MealOrder order, Long assignUserId) {
        Long deptId = order.getDeptId();
        Date expireTime = order.getDeadlineTime();
        List<MealOrderCandidate> candidates = new ArrayList<>();
        if (assignUserId != null) {
            // 指派：下单前已校验资格，这里再取一次展示名做快照
            MealOrderAssignee assignee = mealOrderMapper.selectChefUserInfoByDeptId(deptId, assignUserId);
            if (assignee == null) {
                throw new ServiceException("被指派的厨师不在本家庭或已停用");
            }
            candidates.add(buildCandidate(order.getOrderId(), deptId, assignee, MealOrderCandidate.SOURCE_ASSIGNED,
                    expireTime));
        } else {
            List<MealOrderAssignee> chefs = mealOrderMapper.selectChefAssigneesByDeptId(deptId);
            for (MealOrderAssignee chef : chefs) {
                candidates.add(buildCandidate(order.getOrderId(), deptId, chef, MealOrderCandidate.SOURCE_PUBLIC,
                        expireTime));
            }
        }
        if (candidates.isEmpty()) {
            throw new ServiceException("家里还没有厨师，暂时无法下单");
        }
        candidateMapper.insertCandidates(candidates);
        return candidates;
    }

    /**
     * 组装一条候选人记录
     *
     * @param orderId    订单ID
     * @param deptId     家庭ID
     * @param chef       厨师信息
     * @param source     来源
     * @param expireTime 响应截止时间
     * @return 候选人
     */
    private MealOrderCandidate buildCandidate(Long orderId, Long deptId, MealOrderAssignee chef, String source,
            Date expireTime) {
        MealOrderCandidate candidate = new MealOrderCandidate();
        candidate.setOrderId(orderId);
        candidate.setDeptId(deptId);
        candidate.setUserId(chef.userId());
        candidate.setUserName(chef.nickName());
        candidate.setSource(source);
        candidate.setStatus(MealOrderCandidate.STATUS_PENDING);
        candidate.setExpireTime(expireTime);
        return candidate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void refuseOrder(Long orderId) {
        Long userId = SecurityUtils.getUserId();
        // 先锁订单行：同一订单的并发拒绝在这里排队，避免双方都读到对方仍待响应
        MealOrder order = mealOrderMapper.selectOrderForUpdate(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        if (!MealOrder.STATUS_WAITING.equals(order.getStatus())) {
            throw new ServiceException("该订单已不是待接单状态");
        }
        if (candidateMapper.refuseCandidate(orderId, userId) == 0) {
            throw new ServiceException("该订单已不可拒绝");
        }

        if (candidateMapper.countPending(orderId) > 0) {
            // 仍有其他人待响应，订单维持待接单
            return;
        }

        if (isAssigned(order) && STRATEGY_REOPEN.equals(assignRefuseStrategy())) {
            reopenToPublic(order, MealOrderCandidate.STATUS_REFUSED, MealOrder.CLOSE_REASON_ALL_REFUSED);
            return;
        }
        closeAsUnaccepted(order, MealOrder.CLOSE_REASON_ALL_REFUSED);
    }

    /**
     * 判断订单当前是否处于指派中
     *
     * @param order 订单
     * @return 指派中返回 true
     */
    private boolean isAssigned(MealOrder order) {
        return MealOrder.ASSIGN_MODE_ASSIGNED.equals(order.getAssignMode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleTimeout(MealOrder order) {
        if (isAssigned(order) && STRATEGY_REOPEN.equals(assignRefuseStrategy())) {
            // 指派超时转公开：若补入后仍无人可接，原因仍按超时记录，不写成「全员拒绝」
            return reopenToPublic(order, MealOrderCandidate.STATUS_EXPIRED, MealOrder.CLOSE_REASON_TIMEOUT);
        }
        return closeAsUnaccepted(order, MealOrder.CLOSE_REASON_TIMEOUT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeAllRefused(MealOrder order) {
        return closeAsUnaccepted(order, MealOrder.CLOSE_REASON_ALL_REFUSED);
    }

    @Override
    public void invalidateAll(Long orderId) {
        candidateMapper.invalidateAll(orderId);
    }

    /**
     * 指派转公开：标记原指派人的结果，补入本家庭其他厨师并重置截止时间
     *
     * <p>原被指派人的处理按触发原因区分：主动拒绝时置为已拒绝（不再参与本轮抢单）；
     * 指派超时未响应时置为已超时未响应，但仍可接单——他醒来后按公开候选人身份继续抢。</p>
     *
     * @param order           订单
     * @param assigneeStatus  原指派人的候选人状态（已拒绝或已超时未响应）
     * @param fallbackReason  补入后仍无人可接时的关闭原因
     * @return 是否实际推进了订单（转公开或关闭成功）
     */
    private boolean reopenToPublic(MealOrder order, String assigneeStatus, String fallbackReason) {
        Long orderId = order.getOrderId();
        Date newDeadline = new Date(System.currentTimeMillis() + timeoutMinutes() * 60_000L);
        if (mealOrderMapper.reopenToPublic(orderId, newDeadline) == 0) {
            // 订单已被接单或已关闭，静默退出
            return false;
        }

        Long assignUserId = order.getAssignUserId();
        if (assignUserId != null) {
            if (MealOrderCandidate.STATUS_EXPIRED.equals(assigneeStatus)) {
                candidateMapper.expireCandidate(orderId, assignUserId);
            }
            // 截止时间随新一轮延长；状态保持拒绝或超时未响应，不再回到待响应
            candidateMapper.updateCandidateExpireTime(orderId, assignUserId, newDeadline);
        }

        List<MealOrderCandidate> reopened = buildReopenedCandidates(order, newDeadline);
        if (!reopened.isEmpty()) {
            candidateMapper.insertCandidatesIgnoringExisting(reopened);
        }

        if (candidateMapper.countPending(orderId) == 0) {
            // 补入后仍无人可接：按触发原因关闭，不一律记成「全员拒绝」
            return closeAsUnaccepted(order, fallbackReason);
        }

        // 只通知本轮新入池、且确实待响应的厨师
        Set<Long> notified = new HashSet<>();
        for (MealOrderCandidate candidate : reopened) {
            if (!notified.add(candidate.getUserId())) {
                continue;
            }
            Long receiverId = candidate.getUserId();
            transactionCommitExecutor.afterCommit(() -> mealNotifyService.push(
                    "notify:chef:" + receiverId, "NEW_ORDER", orderId, "新订单",
                    order.getUserName() + " 点的单已转为公开，可以接单了",
                    notificationExtra(order.getOrderNo())));
        }
        return true;
    }

    /**
     * 组装指派转公开时需要补入的候选人（本家庭其他符合资格的厨师）
     *
     * @param order       订单
     * @param newDeadline 新的截止时间
     * @return 待插入的候选人
     */
    private List<MealOrderCandidate> buildReopenedCandidates(MealOrder order, Date newDeadline) {
        Set<Long> existing = new HashSet<>(candidateMapper.selectCandidateUserIds(order.getOrderId()));
        List<MealOrderCandidate> candidates = new ArrayList<>();
        for (MealOrderAssignee chef : mealOrderMapper.selectChefAssigneesByDeptId(order.getDeptId())) {
            if (existing.contains(chef.userId())) {
                continue;
            }
            candidates.add(buildCandidate(order.getOrderId(), order.getDeptId(), chef,
                    MealOrderCandidate.SOURCE_REOPENED, newDeadline));
        }
        return candidates;
    }

    /**
     * 关闭订单为无人接单
     *
     * <p>使用带前置状态的条件更新；影响 0 行表示已被接单、已取消或已由其它实例关闭，
     * 此时静默返回，不重复发送通知。</p>
     *
     * @param order       订单
     * @param closeReason 关闭原因
     * @return 是否实际关闭成功
     */
    private boolean closeAsUnaccepted(MealOrder order, String closeReason) {
        MealOrder update = new MealOrder();
        update.setOrderId(order.getOrderId());
        update.setStatus(MealOrder.STATUS_UNACCEPTED);
        update.setExpectedStatus(MealOrder.STATUS_WAITING);
        update.setCloseReason(closeReason);
        update.setUpdateBy(currentOperator());
        // 超时关闭额外要求确实已过截止时间；拒绝关闭不要求
        update.setRequireExpired(MealOrder.CLOSE_REASON_TIMEOUT.equals(closeReason));
        if (mealOrderMapper.updateMealOrderStatus(update) == 0) {
            return false;
        }
        candidateMapper.invalidatePending(order.getOrderId());
        Long userId = order.getUserId();
        Long orderId = order.getOrderId();
        String orderNo = order.getOrderNo();
        transactionCommitExecutor.afterCommit(() -> mealNotifyService.push("notify:user:" + userId,
                "ORDER_UNACCEPTED", orderId, "无人接单",
                order.getUserName() + "，这单暂时没人接，可以重新下单",
                notificationExtra(orderNo)));
        transactionCommitExecutor.afterCommit(() -> mealWeComNotifyService.notifyOrderUnaccepted(orderId));
        return true;
    }

    /**
     * 取当前操作人账号，没有登录态时记为系统任务
     *
     * <p>超时关闭由定时任务触发，此时没有 {@code SecurityContext}；
     * {@link SecurityUtils#getUsername()} 在无登录态时会抛 401 业务异常，
     * 直接调用会让整条超时关闭链路失效，因此这里必须先判断再取值。</p>
     *
     * @return 操作人账号
     */
    private String currentOperator() {
        try {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            return loginUser != null && loginUser.getUsername() != null ? loginUser.getUsername() : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    /**
     * 读取接单超时分钟数，非法或缺失时回退默认值
     *
     * @return 超时分钟数
     */
    private int timeoutMinutes() {
        int value = ConfigUtils.getInt(ConfigConstants.MEAL_ORDER_ACCEPT_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES);
        return value > 0 ? value : DEFAULT_TIMEOUT_MINUTES;
    }

    /**
     * 读取指派被拒 / 超时后的处理策略，非法或缺失时按转公开处理
     *
     * @return 策略值
     */
    private String assignRefuseStrategy() {
        String value = ConfigUtils.getString(ConfigConstants.MEAL_ORDER_ASSIGN_REFUSE_STRATEGY,
                STRATEGY_REOPEN);
        return STRATEGY_REOPEN.equals(value) || "2".equals(value) ? value : STRATEGY_REOPEN;
    }

    /**
     * 组装通知附加数据
     *
     * @param orderNo 订单号
     * @return 附加数据
     */
    private java.util.Map<String, Object> notificationExtra(String orderNo) {
        java.util.Map<String, Object> extra = new java.util.HashMap<>();
        extra.put("orderNo", orderNo);
        return extra;
    }
}
