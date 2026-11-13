package com.howe.meal.tool.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.common.exception.ServiceException;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.MealReview;
import com.howe.meal.domain.vo.MealOrderAssignee;
import com.howe.meal.service.IMealCategoryService;
import com.howe.meal.service.IMealDishService;
import com.howe.meal.service.IMealOrderService;
import com.howe.meal.service.IMealProposalService;
import com.howe.meal.service.IMealReviewService;
import com.howe.meal.tool.service.IMealReadService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 点餐只读工具的业务读取服务实现。
 *
 * <p>所有分页都在本层对主查询调用 {@code PageHelper.startPage}，并在 {@code finally} 清理；
 * 家庭预检发生在分页之前，保证不会出现"先起分页再查权限表"导致的分页串线程。</p>
 *
 * <p>本实现只读取既有 Service，不复制其归属与状态守卫逻辑。</p>
 */
@Service
@RequiredArgsConstructor
public class MealReadServiceImpl implements IMealReadService {

    private final IMealCategoryService categories;

    private final IMealDishService dishes;

    private final IMealOrderService orders;

    private final IMealProposalService proposals;

    private final IMealReviewService reviews;

    private final com.howe.system.mapper.SysDeptMapper sysDeptMapper;

    /**
     * 分页查询分类。
     *
     * <p>普通账号必须先确认有效家庭：否则 {@code SecurityUtils.getDeptId()} 为 null，
     * Mapper 中的 {@code dept_id} 条件会被整段跳过，等同于返回全站分类。</p>
     */
    @Override
    public PageResult<MealCategory> listCategories(int page, int pageSize, String status) {
        ensureFamilyContext();
        MealCategory query = new MealCategory();
        query.setStatus(status);
        return page(page, pageSize, () -> categories.selectMealCategoryList(query));
    }

    /**
     * 按主键读取分类，沿用原 Service 的公共/本家庭守卫。
     */
    @Override
    public MealCategory getCategory(Long categoryId) {
        return requireRead(() -> categories.selectMealCategoryById(categoryId), "分类");
    }

    /**
     * 分页查询菜品。
     *
     * <p>与分类同理：普通账号必须有有效家庭，避免 null deptId 被解释为全站可见。</p>
     */
    @Override
    public PageResult<MealDish> listDishes(int page, int pageSize, String name, Long categoryId, String status) {
        ensureFamilyContext();
        MealDish query = new MealDish();
        query.setName(name);
        query.setCategoryId(categoryId);
        query.setStatus(status);
        return page(page, pageSize, () -> dishes.selectMealDishList(query));
    }

    /**
     * 按主键读取菜品，沿用原 Service 的公共/本家庭守卫。
     */
    @Override
    public MealDish getDish(Long dishId) {
        return requireRead(() -> dishes.selectMealDishById(dishId), "菜品");
    }

    /**
     * 读取招牌菜，沿用原有 90 天窗口与 3 条上限。
     *
     * <p>原 Service 在非平台账号且无家庭时会把 {@code deptId} 传成 null，
     * 招牌 SQL 的两个 {@code deptId} 条件都会被跳过，因此这里同样先做有效家庭预检。</p>
     */
    @Override
    public List<MealDish> listSignatureDishes() {
        ensureFamilyContext();
        return read(() -> dishes.selectSignatureDishes(), "招牌菜");
    }

    /**
     * 分页查询本人订单。
     *
     * <p>日期区间通过查询对象的 {@code params} 传递：模型无法直接提交该 Map，
     * 值已在 Provider 侧解析并校验。使用 {@link java.util.Date} 而非
     * {@code LocalDateTime}，因为 MyBatis 对 {@code params} Map 中的
     * {@code LocalDateTime} 缺少可靠 TypeHandler，会抛
     * {@code UnknownTypeHandler}；{@code Date} 有原生处理器。</p>
     */
    @Override
    public PageResult<MealOrder> listMyOrders(int page, int pageSize, String status,
            java.util.Date createdFrom, java.util.Date createdTo) {
        MealOrder query = new MealOrder();
        query.setStatus(status);
        if (createdFrom != null) {
            query.getParams().put("createdFrom", createdFrom);
        }
        if (createdTo != null) {
            query.getParams().put("createdTo", createdTo);
        }
        // 分页只作用于订单主查询；工具 Service 清理分页上下文后仅补评价标记和时长，
        // 有界订单明细摘要由下方 Provider 通过按每单 limit 的批量 SQL 查询。
        PageHelper.startPage(page, pageSize);
        try {
            List<MealOrder> rows = read(() -> orders.selectMyOrderPageForTool(query), "订单列表");
            PageInfo<MealOrder> info = new PageInfo<>(rows);
            return new PageResult<>(rows, info.getTotal(), info.isHasNextPage());
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 读取订单主记录详情，沿用家庭归属守卫但不读取明细。
     *
     * <p>详情工具稍后会通过 SQL 限制查询有界明细摘要，不能先调用旧详情方法无界读取全部明细。</p>
     */
    @Override
    public MealOrder getOrder(Long orderId) {
        return requireRead(() -> orders.selectMealOrderForTool(orderId), "订单");
    }

    /**
     * 分页查询本人提案。
     */
    @Override
    public PageResult<MealProposal> listMyProposals(int page, int pageSize, String status) {
        MealProposal query = new MealProposal();
        query.setStatus(status);
        return page(page, pageSize, () -> proposals.selectMyProposalList(query));
    }

    /**
     * 读取提案详情，沿用家庭归属守卫。
     */
    @Override
    public MealProposal getProposal(Long proposalId) {
        return requireRead(() -> proposals.selectMealProposalDetail(proposalId), "提案");
    }

    /**
     * 分页查询本人评价。
     *
     * <p>分页只包评价主查询：Service 在主查询后立即清 PageHelper，再补菜品快照，
     * 避免补数据的查询被 PageHelper 当作分页目标。</p>
     */
    @Override
    public PageResult<MealReview> listMyReviews(int page, int pageSize) {
        MealReview query = new MealReview();
        PageHelper.startPage(page, pageSize);
        try {
            List<MealReview> rows = read(() -> reviews.selectMyReviewPageForTool(query), "评价列表");
            PageInfo<MealReview> info = new PageInfo<>(rows);
            return new PageResult<>(rows, info.getTotal(), info.isHasNextPage());
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 分页查询本家庭可指派厨师。
     */
    @Override
    public PageResult<MealOrderAssignee> listAssignees(int page, int pageSize) {
        return page(page, pageSize, () -> orders.selectAssigneesForTool(page, pageSize), false);
    }

    /**
     * 读取订单明细摘要。
     */
    @Override
    public List<MealOrderItem> listOrderItemSummaries(Long orderId, int limit) {
        return orders.selectOrderItemSummaries(orderId, limit);
    }

    /**
     * 批量读取工具专用的订单明细摘要。
     *
     * @param orderIds 订单ID数组
     * @param limitPerOrder 每单最多返回条数
     * @return 每单有界明细摘要
     */
    @Override
    public List<MealOrderItem> listOrderItemSummaries(Long[] orderIds, int limitPerOrder) {
        return orders.selectOrderItemSummaries(orderIds, limitPerOrder);
    }

    /**
     * 统计订单明细总数。
     */
    @Override
    public int countOrderItems(Long orderId) {
        return orders.countOrderItems(orderId);
    }

    /**
     * 批量统计订单明细总数。
     */
    @Override
    public List<Map<String, Object>> countOrderItems(Long[] orderIds) {
        return orders.countOrderItems(orderIds);
    }

    /**
     * 在自身分页内执行查询并组装分页结果。
     *
     * <p>{@code paged=false} 用于已在被调方完成分页的场景（如厨师列表），
     * 此时本层不再重复分页，只负责计算 {@code hasMore} 与总数。</p>
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param query 主查询
     * @return 分页结果
     */
    private static <T> PageResult<T> page(int page, int pageSize, Supplier<List<T>> query) {
        return page(page, pageSize, query, true);
    }

    /**
     * 在指定分页策略内执行查询并组装分页结果。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param query 主查询
     * @param startHere 是否由本层启动分页
     * @return 分页结果
     */
    private static <T> PageResult<T> page(int page, int pageSize, Supplier<List<T>> query, boolean startHere) {
        if (startHere) {
            PageHelper.startPage(page, pageSize);
        }
        try {
            List<T> rows = query.get();
            PageInfo<T> info = new PageInfo<>(rows);
            return new PageResult<>(rows, info.getTotal(), info.isHasNextPage());
        } finally {
            if (startHere) {
                PageHelper.clearPage();
            }
        }
    }

    /**
     * 校验普通账号必须属于有效家庭，否则立即失败关闭。
     *
     * <p>菜品与分类的可见范围是「公共 + 本家庭」。若当前账号无有效家庭，
     * {@code SecurityUtils.getDeptId()} 为 null，Mapper 中的 {@code dept_id} 条件会被整段跳过，
     * 等同于把全站私有数据当公共数据返回。平台内置账号沿用既有全量范围，不经过本校验。</p>
     */
    private void ensureFamilyContext() {
        if (com.howe.meal.util.MealFamilyGuard.isPlatformAccount()) {
            return;
        }
        com.howe.meal.util.MealFamilyGuard.requireFamilyDeptId(sysDeptMapper);
    }

    /**
     * 执行只读查询，把空结果转为统一的不可见错误。
     *
     * @param action 查询动作
     * @param label 对象名称
     * @param <T> 返回类型
     * @return 查询结果
     */
    private static <T> T requireRead(Supplier<T> action, String label) {
        T value = read(action, label);
        if (value == null) {
            throw new ServiceException(label + "不存在或无权访问");
        }
        return value;
    }

    /**
     * 执行只读查询并统一异常类型。
     *
     * @param action 查询动作
     * @param label 对象名称
     * @param <T> 返回类型
     * @return 查询结果
     */
    private static <T> T read(Supplier<T> action, String label) {
        try {
            return Objects.requireNonNull(action).get();
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            ServiceException wrapped = new ServiceException("读取" + label + "失败");
            wrapped.setDetailMessage(exception.getClass().getSimpleName());
            throw wrapped;
        }
    }
}
