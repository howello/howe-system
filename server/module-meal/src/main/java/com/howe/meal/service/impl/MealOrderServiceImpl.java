package com.howe.meal.service.impl;

import com.howe.common.annotation.DataScope;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.dto.MealOrderSubmitBody;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.mapper.MealReviewMapper;
import com.howe.meal.service.IMealOrderService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
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

    private static final long MILLIS_MINUTE = 60 * 1000L;

    private final MealOrderMapper mealOrderMapper;
    private final MealDishMapper mealDishMapper;
    private final MealReviewMapper mealReviewMapper;

    @Override
    @DataScope(deptAlias = "d")
    public List<MealOrder> selectMealOrderList(MealOrder mealOrder) {
        return mealOrderMapper.selectMealOrderList(mealOrder);
    }

    @Override
    @DataScope(deptAlias = "d")
    public List<MealOrder> selectKitchenOrderList(MealOrder mealOrder) {
        List<MealOrder> orders = mealOrderMapper.selectMealOrderList(mealOrder);
        fillItems(orders);
        return orders;
    }

    @Override
    public List<MealOrder> selectMyOrderList(MealOrder mealOrder) {
        mealOrder.setUserId(SecurityUtils.getUserId());
        List<MealOrder> orders = mealOrderMapper.selectMyOrderList(mealOrder);
        // 列表卡片要展示菜品缩略图与「N 道菜」，明细必须一起带上
        fillItems(orders);
        fillReviewed(orders);
        return orders;
    }

    /**
     * 「按 id 取详情」不受 {@code @DataScope} 保护，这里显式校验家庭归属，
     * 否则换个 orderId 就能读到别人家的订单。
     */
    @Override
    public MealOrder selectMealOrderDetail(Long orderId) {
        MealOrder order = requireOrder(orderId);
        if (!SecurityUtils.isAdmin()) {
            MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        }
        order.setItems(mealOrderMapper.selectItemsByOrderId(orderId));
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

        MealOrder order = new MealOrder();
        order.setOrderNo(generateOrderNo());
        order.setDeptId(deptId);
        order.setUserId(userId);
        order.setUserName(currentNickName());
        order.setStatus(MealOrder.STATUS_WAITING);
        order.setTotalCount(totalCount);
        order.setOrderRemark(body.getOrderRemark());
        order.setCreateBy(SecurityUtils.getUsername());
        mealOrderMapper.insertMealOrder(order);

        items.forEach(item -> item.setOrderId(order.getOrderId()));
        mealOrderMapper.insertOrderItems(items);
        return order.getOrderId();
    }

    @Override
    public int cancelOrder(Long orderId) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameUser(order.getUserId(), "订单");
        if (!MealOrder.STATUS_WAITING.equals(order.getStatus())) {
            throw new ServiceException("订单已被接单，不能取消");
        }
        return changeStatus(orderId, MealOrder.STATUS_CANCELED, MealOrder.STATUS_WAITING, null, null);
    }

    @Override
    public int acceptOrder(Long orderId) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        if (!MealOrder.STATUS_WAITING.equals(order.getStatus())) {
            throw new ServiceException("该订单不是待接单状态");
        }
        return changeStatus(orderId, MealOrder.STATUS_COOKING, MealOrder.STATUS_WAITING, currentNickName(), null);
    }

    @Override
    public int finishOrder(Long orderId) {
        MealOrder order = requireOrder(orderId);
        MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
        if (!MealOrder.STATUS_COOKING.equals(order.getStatus())) {
            throw new ServiceException("该订单不是制作中状态");
        }
        return changeStatus(orderId, MealOrder.STATUS_FINISHED, MealOrder.STATUS_COOKING, null, currentNickName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealOrderByIds(Long[] orderIds) {
        if (orderIds == null || orderIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long orderId : orderIds) {
            MealOrder order = requireOrder(orderId);
            if (!SecurityUtils.isAdmin()) {
                MealScopeGuard.assertSameDept(order.getDeptId(), "订单");
            }
        }
        return mealOrderMapper.deleteMealOrderByIds(orderIds);
    }

    /**
     * 状态流转：where 里带上期望的当前状态，两个厨师同时点「接单」时只有一个能成功
     */
    private int changeStatus(Long orderId, String target, String expected, String acceptBy, String finishBy) {
        MealOrder update = new MealOrder();
        update.setOrderId(orderId);
        update.setStatus(target);
        update.setExpectedStatus(expected);
        update.setAcceptBy(acceptBy);
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

    private MealOrder requireOrder(Long orderId) {
        MealOrder order = mealOrderMapper.selectMealOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        return order;
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

    /**
     * 订单号：M + yyyyMMdd + 3 位当日流水。并发下极小概率撞号，由 order_no 唯一约束兜底。
     */
    private String generateOrderNo() {
        String prefix = ORDER_NO_PREFIX + DateUtils.dateTimeNow("yyyyMMdd");
        String max = mealOrderMapper.selectMaxOrderNo(prefix);
        int seq = 1;
        if (StringUtils.isNotEmpty(max) && max.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 历史数据格式异常时退回 1，唯一约束会挡住真正的重复
            }
        }
        return prefix + String.format("%03d", seq);
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
