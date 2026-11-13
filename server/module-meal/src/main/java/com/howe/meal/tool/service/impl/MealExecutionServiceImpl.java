package com.howe.meal.tool.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderCandidate;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.MealReview;
import com.howe.meal.domain.vo.MealKitchenOrderSummary;
import com.howe.meal.mapper.MealOrderCandidateMapper;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.mapper.MealProposalMapper;
import com.howe.meal.service.IMealOrderService;
import com.howe.meal.service.IMealProposalService;
import com.howe.meal.service.IMealReviewService;
import com.howe.meal.tool.service.IMealExecutionService;
import com.howe.meal.tool.service.IMealReadService;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 点餐写工具与厨房读取的领域执行实现。
 *
 * <p>所有状态迁移复用原 Service 的守卫与事务，不另起一套订单状态机。
 * 本类只做工具命令到领域对象的映射、版本核对与安全结果投影。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealExecutionServiceImpl implements IMealExecutionService {

    /** 厨房列表每单明细上限。 */
    private static final int KITCHEN_ITEMS_PER_ORDER = 10;

    /** 等待状态常量。 */
    private static final String WAITING = "waiting";

    /** 制作中状态常量。 */
    private static final String COOKING = "cooking";

    private final IMealOrderService orders;

    private final IMealReviewService reviews;

    private final IMealProposalService proposals;

    private final MealOrderMapper orderMapper;

    private final com.howe.meal.mapper.MealDishMapper dishMapper;

    private final MealOrderCandidateMapper candidateMapper;

    private final MealProposalMapper proposalMapper;

    private final com.howe.meal.mapper.MealReviewMapper reviewMapper;

    /** 按实际下单资格生成固定预览；确认时再调用一次即可检测目标变化。 */
    @Override
    public OrderPreview previewOrder(OrderSubmitCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new ServiceException("请至少点一道菜");
        }
        Long deptId = SecurityUtils.getDeptId();
        List<PreviewLine> items = new ArrayList<>(command.items().size());
        for (OrderLine line : command.items()) {
            MealDish dish = dishMapper.selectMealDishById(line.dishId());
            if (dish == null) {
                throw new ServiceException("菜品不存在或已删除");
            }
            boolean isPublic = Long.valueOf(0).equals(dish.getDeptId());
            if (!isPublic && !java.util.Objects.equals(dish.getDeptId(), deptId)) {
                throw new ServiceException("存在不属于本家庭的菜品");
            }
            if (!"0".equals(dish.getStatus())) {
                throw new ServiceException("菜品已下架");
            }
            items.add(new PreviewLine(dish.getDishId(), dish.getName(), dish.getCover(), line.quantity(),
                    line.remark()));
        }
        String assigneeName = null;
        if (command.assignUserId() != null) {
            var assignee = orderMapper.selectChefUserInfoByDeptId(deptId, command.assignUserId());
            if (assignee == null) {
                throw new ServiceException("被指派的厨师不在本家庭或已停用");
            }
            assigneeName = assignee.nickName();
        }
        return new OrderPreview(List.copyOf(items), command.orderRemark(), command.assignUserId(), assigneeName);
    }

    /** 厨房列表：只接受 waiting/cooking，保留原有候选与接单人可见性。 */
    @Override
    public IMealReadService.PageResult<KitchenOrderView> listKitchenOrders(String status, int page, int pageSize) {
        if (!WAITING.equals(status) && !COOKING.equals(status)) {
            throw new ServiceException("状态只能是 waiting 或 cooking");
        }
        MealOrder query = new MealOrder();
        query.setStatus(MealOrder.STATUS_WAITING.equals(toCode(status)) ? MealOrder.STATUS_WAITING
                : MealOrder.STATUS_COOKING);
        // 原方法会给查询对象补充当前用户过滤与 DataScope 片段，这里直接复用；
        // 工具路径使用有界分页主查询，不加载无界订单明细。
        PageHelper.startPage(page, pageSize);
        List<MealOrder> rows;
        try {
            rows = orders.selectKitchenOrderPageForTool(query);
            PageInfo<MealOrder> info = new PageInfo<>(rows);
            List<MealOrder> pageRows = new ArrayList<>(rows);
            Map<Long, CandidateView> candidateViews = candidateViews(pageRows);
            List<KitchenOrderView> views = new ArrayList<>(pageRows.size());
            for (MealOrder order : pageRows) {
                CandidateView candidate = candidateViews.get(order.getOrderId());
                views.add(new KitchenOrderView(String.valueOf(order.getOrderId()), order.getOrderNo(), order.getStatus(),
                        order.getDataVersion(), order.getCandidateRound(), order.getAssignMode(), order.getUserName(),
                        format(order.getDeadlineTime()), candidate == null ? null : candidate.status(),
                        candidate == null ? null : candidate.version(), kitchenItems(order), order.getTotalCount() == null
                        ? 0 : order.getTotalCount()));
            }
            return new IMealReadService.PageResult<>(views, info.getTotal(), info.isHasNextPage());
        } finally {
            PageHelper.clearPage();
        }
    }

    /** 厨房角标复用原统计的可见性谓词。 */
    @Override
    public MealKitchenOrderSummary kitchenSummary() {
        return orders.countKitchenOrderSummary(new MealOrder());
    }

    /** 下单：校验正数份数与菜品归属后复用原下单事务。 */
    @Override
    public OrderResult submitOrder(OrderSubmitCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new ServiceException("请至少点一道菜");
        }
        com.howe.meal.domain.dto.MealOrderSubmitBody body = new com.howe.meal.domain.dto.MealOrderSubmitBody();
        List<com.howe.meal.domain.dto.MealOrderSubmitBody.Item> items = new ArrayList<>();
        for (OrderLine line : command.items()) {
            com.howe.meal.domain.dto.MealOrderSubmitBody.Item item =
                    new com.howe.meal.domain.dto.MealOrderSubmitBody.Item();
            item.setDishId(line.dishId());
            item.setCount(line.quantity());
            item.setRemark(line.remark());
            items.add(item);
        }
        body.setItems(items);
        body.setOrderRemark(command.orderRemark());
        body.setAssignUserId(command.assignUserId());
        Long orderId = orders.submitOrder(body);
        MealOrder created = orderMapper.selectMealOrderById(orderId);
        return result(created, orderId);
    }

    /** 取消：把预览版本传入领域 CAS，而不是只做一次预检。 */
    @Override
    public OrderResult cancelOrder(Long orderId, long expectedVersion) {
        MealOrder order = requireOrder(orderId);
        requireVersion(order.getDataVersion(), expectedVersion, "订单");
        orders.cancelOrder(orderId, expectedVersion);
        return result(requireOrder(orderId), orderId);
    }

    /** 接单：订单版本、候选版本与轮次一并传入领域 CAS。 */
    @Override
    public OrderResult acceptOrder(Long orderId, long expectedVersion, long candidateVersion, long candidateRound) {
        requireCandidate(orderId, expectedVersion, candidateVersion, candidateRound);
        orders.acceptOrder(orderId, expectedVersion, candidateVersion, candidateRound);
        return result(requireOrder(orderId), orderId);
    }

    /** 拒单：订单版本、候选版本与轮次一并传入领域 CAS。 */
    @Override
    public OrderResult refuseOrder(Long orderId, long expectedVersion, long candidateVersion, long candidateRound) {
        requireCandidate(orderId, expectedVersion, candidateVersion, candidateRound);
        orders.refuseOrder(orderId, expectedVersion, candidateVersion, candidateRound);
        return result(requireOrder(orderId), orderId);
    }

    /** 完成：把预览版本传入领域 CAS。 */
    @Override
    public OrderResult finishOrder(Long orderId, long expectedVersion) {
        MealOrder order = requireOrder(orderId);
        requireVersion(order.getDataVersion(), expectedVersion, "订单");
        orders.finishOrder(orderId, expectedVersion);
        return result(requireOrder(orderId), orderId);
    }

    /** 订单确认快照。 */
    @Override
    public OrderSnapshot orderSnapshot(Long orderId) {
        MealOrder order = requireOrder(orderId);
        return new OrderSnapshot(String.valueOf(orderId), order.getStatus(), order.getDataVersion(),
                order.getCandidateRound());
    }

    /** 候选确认快照。 */
    @Override
    public CandidateSnapshot candidateSnapshot(Long orderId) {
        requireOrder(orderId);
        MealOrderCandidate candidate = candidateMapper.selectByOrderAndUser(orderId, SecurityUtils.getUserId());
        if (candidate == null) {
            throw new ServiceException("不是该订单的候选人");
        }
        return new CandidateSnapshot(String.valueOf(orderId), candidate.getStatus(), candidate.getDataVersion(),
                candidate.getCandidateRound());
    }

    /** 评价：复用原一单一评事务与通知。 */
    @Override
    public ReviewResult submitReview(Long orderId, int score, String content, boolean anonymous) {
        MealReview review = new MealReview();
        review.setOrderId(orderId);
        review.setScore(score);
        review.setContent(content);
        review.setAnonymous(anonymous ? "1" : "0");
        reviews.submitReview(review);
        // 原 Service 不回传主键，这里按订单唯一键回读刚写入的评价。
        MealReview created = findReview(orderId);
        return new ReviewResult(created == null ? null : String.valueOf(created.getReviewId()),
                String.valueOf(orderId));
    }

    /** 提案提交：复用原创建校验，返回后续自动审核载荷。 */
    @Override
    public ProposalSubmitResult submitProposal(ProposalSubmitCommand command) {
        MealProposal proposal = new MealProposal();
        proposal.setName(command.name());
        proposal.setDescription(command.description());
        proposal.setReason(command.reason());
        proposal.setTips(command.notes());
        proposal.setImage(command.image());
        if (command.categoryId() != null) {
            proposal.setCategoryIds(List.of(command.categoryId()));
        }
        proposals.submitProposalForTool(proposal);
        MealProposal created = proposalMapper.selectMealProposalById(proposal.getProposalId());
        return new ProposalSubmitResult(String.valueOf(proposal.getProposalId()),
                created == null ? 0L : created.getDataVersion(), created == null ? "0" : created.getStatus());
    }

    /** 人工审核：复用原审核事务（含改判与菜品联动）。 */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ProposalAuditResult auditProposal(Long proposalId, long expectedVersion, String expectedStatus,
            String decision, String reason) {
        MealProposal before = proposalMapper.selectProposalForUpdate(proposalId);
        if (before == null) {
            throw new ServiceException("提案不存在或无权访问");
        }
        com.howe.meal.util.MealScopeGuard.assertSameDept(before.getDeptId(), "提案");
        requireVersion(before.getDataVersion(), expectedVersion, "提案");
        if (expectedStatus != null && !expectedStatus.equals(proposalStatusName(before.getStatus()))) {
            throw new ServiceException("提案状态已变化，请重新预览");
        }
        com.howe.meal.domain.dto.MealProposalAuditBody body =
                new com.howe.meal.domain.dto.MealProposalAuditBody();
        body.setProposalId(proposalId);
        body.setStatus("approved".equals(decision) ? MealProposal.STATUS_APPROVED : MealProposal.STATUS_REJECTED);
        body.setAuditRemark(reason);
        proposals.auditProposal(body);
        MealProposal after = requireProposal(proposalId);
        String effect = dishEffect(before, after);
        return new ProposalAuditResult(String.valueOf(proposalId), after.getStatus(), after.getDataVersion(),
                after.getDishId() == null ? null : String.valueOf(after.getDishId()), effect);
    }

    /** 数据库存储状态映射为工具契约状态。 */
    private static String proposalStatusName(String status) {
        return "1".equals(status) ? "approved" : "2".equals(status) ? "rejected" : "pending";
    }

    /** 提案确认快照。 */
    @Override
    public ProposalSnapshot proposalSnapshot(Long proposalId) {
        MealProposal proposal = requireProposal(proposalId);
        return new ProposalSnapshot(String.valueOf(proposalId), proposal.getStatus(), proposal.getDataVersion(),
                proposal.getDishId() == null ? null : String.valueOf(proposal.getDishId()));
    }

    /** 校验订单存在且本人可见。 */
    private MealOrder requireOrder(Long orderId) {
        MealOrder order = orderMapper.selectMealOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或无权访问");
        }
        com.howe.meal.util.MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        return order;
    }

    /** 校验提案存在且本人家庭可见。 */
    private MealProposal requireProposal(Long proposalId) {
        MealProposal proposal = proposalMapper.selectMealProposalById(proposalId);
        if (proposal == null) {
            throw new ServiceException("提案不存在或无权访问");
        }
        com.howe.meal.util.MealScopeGuard.assertSameDept(proposal.getDeptId(), "提案");
        return proposal;
    }

    /** 版本相等校验。 */
    private static void requireVersion(Long actual, long expected, String label) {
        if (actual == null || actual != expected) {
            throw new ServiceException(label + "内容已变化，请重新预览");
        }
    }

    /** 订单与候选版本/轮次一起核对。 */
    private void requireCandidate(Long orderId, long expectedVersion, long candidateVersion, long candidateRound) {
        MealOrder order = requireOrder(orderId);
        requireVersion(order.getDataVersion(), expectedVersion, "订单");
        long round = order.getCandidateRound() == null ? 0L : order.getCandidateRound();
        if (round != candidateRound) {
            throw new ServiceException("订单候选轮次已变化，请重新预览");
        }
        MealOrderCandidate candidate = candidateMapper.selectByOrderAndUser(orderId, SecurityUtils.getUserId());
        if (candidate == null) {
            throw new ServiceException("不是该订单的候选人");
        }
        requireVersion(candidate.getDataVersion(), candidateVersion, "候选人");
        long candidateRoundValue = candidate.getCandidateRound() == null ? 0L : candidate.getCandidateRound();
        if (candidateRoundValue != candidateRound) {
            throw new ServiceException("候选人轮次已变化，请重新预览");
        }
    }

    /** 按订单读取唯一评价。 */
    private MealReview findReview(Long orderId) {
        return reviewMapper.selectMealReviewByOrderId(orderId);
    }

    /** 菜品影响判定。 */
    private static String dishEffect(MealProposal before, MealProposal after) {
        boolean wasApproved = MealProposal.STATUS_APPROVED.equals(before.getStatus());
        boolean nowApproved = MealProposal.STATUS_APPROVED.equals(after.getStatus());
        if (before.getDishId() == null && after.getDishId() != null) {
            return "created";
        }
        if (!wasApproved && nowApproved) {
            return "listed";
        }
        if (wasApproved && !nowApproved && after.getDishId() != null) {
            return "unlisted";
        }
        return "unchanged";
    }

    /** 订单安全结果。 */
    private static OrderResult result(MealOrder order, Long fallbackId) {
        if (order == null) {
            return new OrderResult(String.valueOf(fallbackId), null, null, null, null, null);
        }
        return new OrderResult(String.valueOf(order.getOrderId()), order.getOrderNo(), order.getStatus(),
                order.getDataVersion(), order.getCloseReason(), order.getAcceptUserId());
    }

    /** 本人候选视图。 */
    private Map<Long, CandidateView> candidateViews(List<MealOrder> orders) {
        Map<Long, CandidateView> views = new LinkedHashMap<>();
        for (MealOrder order : orders) {
            MealOrderCandidate candidate = candidateMapper.selectByOrderAndUser(order.getOrderId(),
                    SecurityUtils.getUserId());
            if (candidate != null) {
                views.put(order.getOrderId(), new CandidateView(candidate.getStatus(), candidate.getDataVersion()));
            }
        }
        return views;
    }

    /** 有界明细摘要。 */
    private List<ItemView> kitchenItems(MealOrder order) {
        List<MealOrderItem> items = orders.selectOrderItemSummaries(order.getOrderId(), KITCHEN_ITEMS_PER_ORDER);
        List<ItemView> views = new ArrayList<>(items.size());
        for (MealOrderItem item : items) {
            views.add(new ItemView(item.getDishName(), item.getCount()));
        }
        return views;
    }

    /** 状态串到状态码。 */
    private static String toCode(String status) {
        return WAITING.equals(status) ? MealOrder.STATUS_WAITING : MealOrder.STATUS_COOKING;
    }

    /** 时间格式，空值返回 null。 */
    private static String format(Date value) {
        return value == null ? null : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    /** 本人候选视图。 */
    private record CandidateView(String status, Long version) {
    }
}
