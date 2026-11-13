package com.howe.meal.service.impl;

import com.howe.common.annotation.DataScope;
import cn.hutool.core.util.IdUtil;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderCandidate;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.dto.MealOrderSubmitBody;
import com.howe.meal.domain.vo.MealKitchenOrderSummary;
import com.howe.meal.domain.vo.MealOrderAssignee;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.mapper.MealOrderCandidateMapper;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.mapper.MealReviewMapper;
import com.howe.meal.service.IMealOrderCandidateService;
import com.howe.meal.service.IMealOrderService;
import com.howe.meal.service.IMealNotifyService;
import com.howe.meal.service.IMealWeComNotifyService;
import com.howe.meal.service.ITransactionCommitExecutor;
import com.howe.meal.util.MealFamilyGuard;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 点餐-订单 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealOrderServiceImpl implements IMealOrderService {

    /** 订单号前缀：M + yyyyMMdd */
    private static final String ORDER_NO_PREFIX = "M";

    /** 「我的订单」视角：厨师，返回自己接单制作过的订单 */
    private static final String SCOPE_CHEF = "chef";

    private static final long MILLIS_MINUTE = 60 * 1000L;

    /** 默认接单超时分钟数：3 小时 */
    private static final int DEFAULT_TIMEOUT_MINUTES = 180;

    private final MealOrderMapper mealOrderMapper;
    private final MealDishMapper mealDishMapper;
    private final MealReviewMapper mealReviewMapper;
    private final IMealNotifyService mealNotifyService;
    private final IMealWeComNotifyService mealWeComNotifyService;
    private final ITransactionCommitExecutor transactionCommitExecutor;
    private final IMealOrderCandidateService mealOrderCandidateService;
    private final MealOrderCandidateMapper mealOrderCandidateMapper;
    private final com.howe.system.mapper.SysDeptMapper sysDeptMapper;
    @DataScope(deptAlias = "d")
    public List<MealOrder> selectMealOrderList(MealOrder mealOrder) {
        return mealOrderMapper.selectMealOrderList(mealOrder);
    }

    /**
     * 厨师工作台列表：待接单按候选人过滤，制作中只属于接单厨师本人。
     *
     * <p>待接单只返回「当前用户仍是待响应候选人」的订单：拒绝过的、指派给别人的都不会出现。
     * 制作中必须按 {@code acceptUserId} 过滤，不能把两者混在一起。</p>
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<MealOrder> selectKitchenOrderList(MealOrder mealOrder) {
        applyKitchenVisibility(mealOrder);
        List<MealOrder> orders = mealOrderMapper.selectMealOrderList(mealOrder);
        fillItems(orders);
        return orders;
    }

    /**
     * 工具专用的厨师工作台分页主查询。
     *
     * <p>分页由调用方启动；本方法在主查询后立即清理 PageHelper，只补时长，
     * 不调用无界的 {@link #fillItems(List)}，明细由调用方按每单固定上限读取。</p>
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<MealOrder> selectKitchenOrderPageForTool(MealOrder mealOrder) {
        applyKitchenVisibility(mealOrder);
        List<MealOrder> orders;
        try {
            orders = mealOrderMapper.selectMealOrderList(mealOrder);
        } finally {
            com.github.pagehelper.PageHelper.clearPage();
        }
        if (orders != null) {
            orders.forEach(this::fillDuration);
        }
        return orders;
    }

    /**
     * 厨师工作台角标：待接单统计家庭全部，制作中只统计本人接单。
     */    @Override
    @DataScope(deptAlias = "d")
    public MealKitchenOrderSummary countKitchenOrderSummary(MealOrder mealOrder) {
        long pendingCount = countKitchenStatus(MealOrder.STATUS_WAITING, false, mealOrder);
        long cookingCount = countKitchenStatus(MealOrder.STATUS_COOKING, true, mealOrder);
        return new MealKitchenOrderSummary(pendingCount, cookingCount);
    }

    /**
     * 按状态统计厨师工作台数量；制作中订单只统计当前登录账号接的单。
     *
     * <p>统计使用独立查询对象，必须显式继承入参上的 {@code params.dataScope}，
     * 否则 {@code @DataScope} 写入的权限片段会丢失，导致跨家庭统计。</p>
     *
     * @param status          订单状态
     * @param onlyCurrentChef 是否只统计当前接单人
     * @param template        控制器入参，用于继承数据权限片段
     * @return 数量
     */
    private long countKitchenStatus(String status, boolean onlyCurrentChef, MealOrder template) {
        MealOrder query = new MealOrder();
        query.setStatus(status);
        if (template != null) {
            query.setParams(new HashMap<>(template.getParams()));
        }
        if (onlyCurrentChef) {
            query.setAcceptUserId(SecurityUtils.getUserId());
        } else {
            // 待接单角标只统计「我是待响应候选人」的订单
            query.setCandidateUserId(SecurityUtils.getUserId());
        }
        List<Map<String, Object>> rows = mealOrderMapper.selectKitchenOrderStatusCounts(query);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object count = rows.get(0).get("cnt");
        return count instanceof Number value ? value.longValue() : 0L;
    }

    /**
     * 给厨师工作台查询设置可见范围
     *
     * <p>待接单按候选人过滤：只有「我是待响应候选人」的订单可见，指派给别人的单不会出现；
     * 制作中只限接单人本人。</p>
     *
     * @param mealOrder 查询条件
     */
    private void applyKitchenVisibility(MealOrder mealOrder) {
        if (mealOrder == null) {
            return;
        }
        String status = mealOrder.getStatus();
        if (MealOrder.STATUS_COOKING.equals(status)) {
            mealOrder.setAcceptUserId(SecurityUtils.getUserId());
        } else if (MealOrder.STATUS_WAITING.equals(status)) {
            mealOrder.setCandidateUserId(SecurityUtils.getUserId());
        }
    }

    @Override
    public List<MealOrder> selectMyOrderList(MealOrder mealOrder, String scope) {
        List<MealOrder> orders;
        if (SCOPE_CHEF.equals(scope)) {
            // 厨师视角：自己接过单、制作过的订单，而不是自己点的
            mealOrder.setAcceptUserId(SecurityUtils.getUserId());
            orders = mealOrderMapper.selectCookedOrderList(mealOrder);
        } else {
            mealOrder.setUserId(SecurityUtils.getUserId());
            orders = mealOrderMapper.selectMyOrderList(mealOrder);
            // 列表卡片要展示菜品缩略图与「N 道菜」，明细必须一起带上
            fillReviewed(orders);
        }
        fillItems(orders);
        return orders;
    }

    /**
     * 工具专用的本人订单分页主查询。
     *
     * <p>分页由调用方启动；本方法在主查询后立即清理 PageHelper，随后只补已评价标记与时长，
     * 不调用 {@link #fillItems(List)}。工具列表明细由 Provider 经 Mapper 窗口查询按每单限量读取，
     * 避免先执行无界 {@code selectItemsByOrderIds} 再在内存截断。旧 Web 列表继续使用
     * {@link #selectMyOrderList(MealOrder, String)}，其完整明细契约不变。</p>
     */
    @Override
    public List<MealOrder> selectMyOrderPageForTool(MealOrder mealOrder) {
        mealOrder.setUserId(SecurityUtils.getUserId());
        List<MealOrder> orders;
        try {
            orders = mealOrderMapper.selectMyOrderList(mealOrder);
        } finally {
            com.github.pagehelper.PageHelper.clearPage();
        }
        fillReviewed(orders);
        if (orders != null) {
            orders.forEach(this::fillDuration);
        }
        return orders;
    }

    /**
     * 「按 id 取详情」不受 {@code @DataScope} 保护，这里显式校验家庭归属，
     * 否则换个 orderId 就能读到别人家的订单。
     */
    @Override
    public MealOrder selectMealOrderDetail(Long orderId) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        order.setItems(mealOrderMapper.selectItemsByOrderId(orderId));
        order.setReviewed(!mealReviewMapper.selectReviewedOrderIds(new Long[] { orderId }).isEmpty());
        fillDuration(order);
        return order;
    }

    /**
     * 工具专用的订单主记录详情读取。
     *
     * <p>沿用原 Service 的订单存在性与家庭归属守卫，但不加载任何明细；
     * 工具随后执行有界摘要查询，避免无界读取整张订单明细集合。</p>
     */
    @Override
    public MealOrder selectMealOrderForTool(Long orderId) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        order.setReviewed(!mealReviewMapper.selectReviewedOrderIds(new Long[] { orderId }).isEmpty());
        fillDuration(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitOrder(MealOrderSubmitBody body) {
        Long userId = SecurityUtils.getUserId();
        Long deptId = SecurityUtils.getDeptId();

        List<MealOrderItem> items = new ArrayList<>();
        int totalCount = 0;
        for (MealOrderSubmitBody.Item line : body.getItems()) {
            if (line.getDishId() == null) {
                throw new ServiceException("菜品ID不能为空");
            }
            int count = line.getCount() == null ? 1 : line.getCount();
            if (count <= 0) {
                throw new ServiceException("份数必须大于 0");
            }
            MealDish dish = mealDishMapper.selectMealDishById(line.getDishId());
            if (dish == null) {
                throw new ServiceException("菜品不存在或已删除");
            }
            // 只能点公共菜谱或本家庭的私有菜
            boolean isPublic = dish.getDeptId() != null && dish.getDeptId().longValue() == 0L;
            if (!isPublic && !dish.getDeptId().equals(deptId)) {
                throw new ServiceException("存在不属于本家庭的菜品");
            }
            if (!"0".equals(dish.getStatus())) {
                throw new ServiceException("菜品「" + dish.getName() + "」已下架");
            }
            // 菜名与封面在此刻落成快照，之后菜品怎么改都不影响这张订单
            MealOrderItem item = new MealOrderItem();
            item.setDishId(dish.getDishId());
            item.setDishName(dish.getName());
            item.setDishCover(dish.getCover());
            item.setCount(count);
            item.setRemark(line.getRemark());
            items.add(item);
            totalCount += count;
        }

        Long assignUserId = body.getAssignUserId();
        MealOrderAssignee assignee = null;
        if (assignUserId != null) {
            // 指派资格在下单时一次性校验：同家庭、启用、拥有 meal_chef
            assignee = mealOrderMapper.selectChefUserInfoByDeptId(deptId, assignUserId);
            if (assignee == null) {
                throw new ServiceException("被指派的厨师不在本家庭或已停用");
            }
        }

        Date deadline = new Date(System.currentTimeMillis() + acceptTimeoutMinutes() * 60_000L);
        MealOrder order = new MealOrder();
        // 事务内临时唯一号，拿到数据库主键后替换为稳定业务号。
        order.setOrderNo(IdUtil.fastSimpleUUID());
        order.setDeptId(deptId);
        order.setUserId(userId);
        order.setUserName(currentNickName());
        order.setStatus(MealOrder.STATUS_WAITING);
        order.setTotalCount(totalCount);
        order.setOrderRemark(body.getOrderRemark());
        order.setAssignMode(assignee != null ? MealOrder.ASSIGN_MODE_ASSIGNED : MealOrder.ASSIGN_MODE_PUBLIC);
        order.setAssignUserId(assignee != null ? assignee.userId() : null);
        order.setAssignUserName(assignee != null ? assignee.nickName() : "");
        order.setDeadlineTime(deadline);
        order.setCreateBy(SecurityUtils.getUsername());
        mealOrderMapper.insertMealOrder(order);
        String orderNo = ORDER_NO_PREFIX + DateUtils.dateTimeNow("yyyyMMdd") + "-" + order.getOrderId();
        if (mealOrderMapper.updateOrderNo(order.getOrderId(), orderNo) != 1) {
            throw new ServiceException("生成订单号失败，请重试");
        }
        order.setOrderNo(orderNo);

        items.forEach(item -> item.setOrderId(order.getOrderId()));
        mealOrderMapper.insertOrderItems(items);
        // 候选人生成放在同一事务里：没有候选人时整个下单失败，不留下无人可见的订单
        List<MealOrderCandidate> candidates = mealOrderCandidateService.createCandidates(order, assignUserId);

        transactionCommitExecutor.afterCommit(() -> pushNewOrderToCandidates(order, candidates));
        transactionCommitExecutor.afterCommit(() -> mealWeComNotifyService.notifyNewOrder(order.getOrderId()));
        return order.getOrderId();
    }

    /**
     * 按候选人生成新订单通知：指派单只推被指派人，公开单推全部待响应候选人
     *
     * @param order      订单
     * @param candidates 本轮生成的候选人
     */
    private void pushNewOrderToCandidates(MealOrder order, List<MealOrderCandidate> candidates) {
        for (MealOrderCandidate candidate : candidates) {
            if (!MealOrderCandidate.STATUS_PENDING.equals(candidate.getStatus())) {
                continue;
            }
            mealNotifyService.push("notify:chef:" + candidate.getUserId(), "NEW_ORDER", order.getOrderId(),
                    "新订单", order.getUserName() + " 下单 " + order.getTotalCount() + " 份，等待接单",
                    notificationExtra(order.getOrderNo()));
        }
    }

    /**
     * 读取接单超时分钟数，非法或缺失时回退默认值
     *
     * @return 超时分钟数
     */
    private int acceptTimeoutMinutes() {
        int value = ConfigUtils.getInt(ConfigConstants.MEAL_ORDER_ACCEPT_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES);
        return value > 0 ? value : DEFAULT_TIMEOUT_MINUTES;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelOrder(Long orderId) {
        return cancelOrder(orderId, null);
    }

    /**
     * 取消订单：工具路径额外要求订单版本等于预览快照。
     *
     * <p>Web 路径传 {@code null}，保持原有「读到最新状态后条件更新」的语义；
     * 工具路径传入预览版本，避免预览之后被并发修改仍继续执行。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelOrder(Long orderId, Long expectedVersion) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameUser(order.getUserId(), "订单");
        if (!MealOrder.STATUS_WAITING.equals(order.getStatus())) {
            throw new ServiceException("订单已被接单，不能取消");
        }
        requirePreviewVersion(order.getDataVersion(), expectedVersion);
        MealOrder update = new MealOrder();
        update.setOrderId(orderId);
        update.setStatus(MealOrder.STATUS_CANCELED);
        update.setExpectedStatus(MealOrder.STATUS_WAITING);
        update.setExpectedVersion(expectedVersion == null ? order.getDataVersion() : expectedVersion);
        update.setCloseReason(MealOrder.CLOSE_REASON_CANCELED);
        update.setCancelTime(new Date());
        update.setUpdateBy(SecurityUtils.getUsername());
        int rows = mealOrderMapper.updateMealOrderStatus(update);
        if (rows == 0) {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        // 取消后其余候选人一并失效，避免残留待响应记录
        mealOrderCandidateService.invalidateAll(orderId);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int acceptOrder(Long orderId) {
        return acceptOrder(orderId, null, null, null);
    }

    /**
     * 接单：工具路径要求订单版本、本人候选版本与轮次都匹配预览快照。
     *
     * <p>Web 路径三个期望值均为 {@code null}，仍按「锁订单后读取最新候选」执行；
     * 工具路径把预览时的快照一路传入候选 CAS 与订单 CAS，避免预览后状态变化仍命中。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int acceptOrder(Long orderId, Long expectedVersion, Long candidateVersion, Long candidateRound) {
        Long userId = SecurityUtils.getUserId();
        // 统一锁顺序：先锁订单行，再裁决候选。反过来会在并发接单时形成相反的锁序。
        MealOrder order = mealOrderMapper.selectOrderForUpdate(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        if (!MealOrder.STATUS_WAITING.equals(order.getStatus())) {
            throw new ServiceException("该订单不是待接单状态");
        }
        requirePreviewVersion(order.getDataVersion(), expectedVersion);
        Long round = candidateRound == null
                ? (order.getCandidateRound() == null ? 0L : order.getCandidateRound()) : candidateRound;
        if (order.getCandidateRound() != null && order.getCandidateRound() != round) {
            throw new ServiceException("订单候选轮次已变化，请刷新后重试");
        }
        // 候选人层裁决：我还挂在当前轮次吗
        if (mealOrderCandidateMapper.acceptCandidate(orderId, userId, round, candidateVersion) == 0) {
            throw new ServiceException("该订单已不可接单");
        }
        // 订单层裁决：还在等人接吗 —— 并发下只有一人能拿到 1 行
        MealOrder update = new MealOrder();
        update.setOrderId(orderId);
        update.setStatus(MealOrder.STATUS_COOKING);
        update.setExpectedStatus(MealOrder.STATUS_WAITING);
        update.setExpectedVersion(expectedVersion == null ? order.getDataVersion() : expectedVersion);
        update.setAcceptBy(currentNickName());
        update.setAcceptUserId(userId);
        update.setUpdateBy(SecurityUtils.getUsername());
        if (mealOrderMapper.updateMealOrderStatus(update) == 0) {
            throw new ServiceException("订单已被他人接走");
        }
        // 其余候选人失效，订单真正归属当前厨师；只影响当前轮次
        mealOrderCandidateMapper.invalidateOthers(orderId, userId, round);

        transactionCommitExecutor.afterCommit(() -> mealNotifyService.push("notify:user:" + order.getUserId(),
                "ORDER_ACCEPTED", order.getOrderId(), "厨师已接单", "厨师已接单，正在准备您的餐点",
                notificationExtra(order.getOrderNo())));
        transactionCommitExecutor.afterCommit(() -> mealWeComNotifyService.notifyOrderAccepted(order.getOrderId()));
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refuseOrder(Long orderId) {
        refuseOrder(orderId, null, null, null);
    }

    /**
     * 拒单：工具路径要求订单版本、本人候选版本与轮次都匹配预览快照。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refuseOrder(Long orderId, Long expectedVersion, Long candidateVersion, Long candidateRound) {
        mealOrderCandidateService.refuseOrder(orderId, expectedVersion, candidateVersion, candidateRound);
    }

    @Override
    public List<MealOrderAssignee> selectAssignees() {
        Long deptId = SecurityUtils.getDeptId();
        if (deptId == null) {
            return new ArrayList<>();
        }
        return mealOrderMapper.selectChefAssigneesByDeptId(deptId);
    }

    /**
     * 工具专用的可指派厨师分页查询。
     *
     * <p>先确认有效家庭，再对厨师单表做数据库分页；平台内置账号沿用既有全量范围。</p>
     */
    @Override
    public List<MealOrderAssignee> selectAssigneesForTool(int page, int pageSize) {
        Long deptId;
        if (MealFamilyGuard.isPlatformAccount()) {
            deptId = SecurityUtils.getDeptId();
        } else {
            deptId = MealFamilyGuard.requireFamilyDeptId(sysDeptMapper);
        }
        if (deptId == null) {
            throw new ServiceException("当前账号没有可用的家庭归属");
        }
        com.github.pagehelper.PageHelper.startPage(page, pageSize);
        try {
            return mealOrderMapper.selectChefAssigneesByDeptId(deptId);
        } finally {
            com.github.pagehelper.PageHelper.clearPage();
        }
    }

    /**
     * 工具专用的订单明细摘要，按 item_id 稳定截断。
     */
    @Override
    public List<MealOrderItem> selectOrderItemSummaries(Long orderId, int limit) {
        return mealOrderMapper.selectItemSummariesByOrderId(orderId, limit);
    }

    /**
     * 批量查询工具专用的订单明细摘要。
     */
    @Override
    public List<MealOrderItem> selectOrderItemSummaries(Long[] orderIds, int limitPerOrder) {
        if (orderIds == null || orderIds.length == 0) {
            return List.of();
        }
        if (limitPerOrder < 1) {
            throw new IllegalArgumentException("每单明细上限必须为正数");
        }
        return mealOrderMapper.selectItemSummariesByOrderIds(orderIds, limitPerOrder);
    }

    /**
     * 统计订单明细总数。
     */
    @Override
    public int countOrderItems(Long orderId) {
        return mealOrderMapper.countItemsByOrderId(orderId);
    }

    /**
     * 批量统计订单明细总数。
     */
    @Override
    public List<Map<String, Object>> countOrderItems(Long[] orderIds) {
        return mealOrderMapper.countItemsByOrderIds(orderIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finishOrder(Long orderId) {
        return finishOrder(orderId, null);
    }

    /**
     * 完成：工具路径要求订单版本等于预览快照。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finishOrder(Long orderId, Long expectedVersion) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        if (!MealOrder.STATUS_COOKING.equals(order.getStatus())) {
            throw new ServiceException("该订单不是制作中状态");
        }
        requirePreviewVersion(order.getDataVersion(), expectedVersion);
        // 只有实际接单人能标记完成，避免同家庭其他人误操作别人的单
        Long acceptUserId = order.getAcceptUserId();
        if (acceptUserId != null && !acceptUserId.equals(SecurityUtils.getUserId())
                && !SecurityUtils.isAdmin()) {
            throw new ServiceException("只有接单的厨师可以标记完成");
        }
        int rows = changeStatus(orderId, MealOrder.STATUS_FINISHED, MealOrder.STATUS_COOKING,
                null, currentNickName(), null,
                expectedVersion == null ? order.getDataVersion() : expectedVersion);
        transactionCommitExecutor.afterCommit(() -> mealNotifyService.push("notify:user:" + order.getUserId(),
                "ORDER_COMPLETED", order.getOrderId(), "订单已完成", "您的订单已完成",
                notificationExtra(order.getOrderNo())));
        transactionCommitExecutor.afterCommit(() -> mealWeComNotifyService.notifyOrderCompleted(order.getOrderId()));
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealOrderByIds(Long[] orderIds) {
        if (orderIds == null || orderIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long orderId : orderIds) {
            MealOrder order = requireOrder(orderId);
            MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        }
        return mealOrderMapper.deleteMealOrderByIds(orderIds);
    }

    /**
     * 状态流转：where 里带上期望的当前状态与数据版本，
     * 两个厨师同时点「接单」或与超时任务并发时只有一个能成功。
     *
     * @param orderId         订单ID
     * @param target          目标状态
     * @param expected        期望的当前状态
     * @param acceptBy        接单人（可空）
     * @param finishBy        完成人（可空）
     * @param acceptUserId    接单人用户ID（可空）
     * @param expectedVersion 期望的数据版本
     * @return 影响行数
     */
    private int changeStatus(Long orderId, String target, String expected, String acceptBy, String finishBy,
            Long acceptUserId, Long expectedVersion) {
        MealOrder update = new MealOrder();
        update.setOrderId(orderId);
        update.setStatus(target);
        update.setExpectedStatus(expected);
        update.setExpectedVersion(expectedVersion);
        update.setAcceptBy(acceptBy);
        update.setAcceptUserId(acceptUserId);
        update.setFinishBy(finishBy);
        if (MealOrder.STATUS_CANCELED.equals(target)) {
            update.setCancelTime(new Date());
        }
        update.setUpdateBy(SecurityUtils.getUsername());
        int rows = mealOrderMapper.updateMealOrderStatus(update);
        if (rows == 0) {
            throw new ServiceException("订单状态已变化，请刷新后重试");
        }
        return rows;
    }

    private Map<String, Object> notificationExtra(String orderNo) {
        Map<String, Object> extra = new java.util.HashMap<>();
        extra.put("orderNo", orderNo);
        return extra;
    }

    private MealOrder requireOrder(Long orderId) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        return order;
    }

    /**
     * 工具预览版本与实际版本核对；{@code null} 表示 Web 路径不启用预览版本约束。
     *
     * @param actual 当前版本
     * @param expected 预览版本
     */
    private static void requirePreviewVersion(Long actual, Long expected) {
        if (expected != null && (actual == null || !actual.equals(expected))) {
            throw new ServiceException("订单已变化，请重新预览");
        }
    }

    /**
     * 批量填充点餐明细，并计算等待/制作时长
     *
     * <p>时长统一由后端算好返回，前端不自己计时，避免各端时钟不一致。</p>
     */
    private void fillItems(List<MealOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Long[] orderIds = orders.stream().map(MealOrder::getOrderId).toArray(Long[]::new);
        List<MealOrderItem> items = mealOrderMapper.selectItemsByOrderIds(orderIds);
        Map<Long, List<MealOrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(MealOrderItem::getOrderId));
        for (MealOrder order : orders) {
            order.setItems(grouped.getOrDefault(order.getOrderId(), new ArrayList<>()));
            fillDuration(order);
        }
    }

    /**
     * 标记哪些订单已经评价过，前端据此把「去评价」换成「已评价」
     */
    private void fillReviewed(List<MealOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Long[] orderIds = orders.stream().map(MealOrder::getOrderId).toArray(Long[]::new);
        Set<Long> reviewed = new HashSet<>(mealReviewMapper.selectReviewedOrderIds(orderIds));
        for (MealOrder order : orders) {
            order.setReviewed(reviewed.contains(order.getOrderId()));
        }
    }

    private void fillDuration(MealOrder order) {
        long now = System.currentTimeMillis();
        if (MealOrder.STATUS_WAITING.equals(order.getStatus()) && order.getCreateTime() != null) {
            order.setWaitMinutes(Math.max(0, (now - order.getCreateTime().getTime()) / MILLIS_MINUTE));
        }
        if (MealOrder.STATUS_COOKING.equals(order.getStatus()) && order.getAcceptTime() != null) {
            order.setCookMinutes(Math.max(0, (now - order.getAcceptTime().getTime()) / MILLIS_MINUTE));
        }
    }

    private String currentNickName() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            return SecurityUtils.getUsername();
        }
        String nickName = loginUser.getUser() != null ? loginUser.getUser().getNickName() : null;
        return StringUtils.isNotEmpty(nickName) ? nickName : loginUser.getUsername();
    }
}
