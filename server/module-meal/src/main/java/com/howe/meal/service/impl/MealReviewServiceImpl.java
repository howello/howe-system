package com.howe.meal.service.impl;

import com.howe.common.annotation.DataScope;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealReview;
import com.howe.meal.mapper.MealOrderMapper;
import com.howe.meal.mapper.MealReviewMapper;
import com.howe.meal.service.IMealReviewService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 点餐-评价 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealReviewServiceImpl implements IMealReviewService {

    private final MealReviewMapper mealReviewMapper;
    private final MealOrderMapper mealOrderMapper;

    @Override
    @DataScope(deptAlias = "d")
    public List<MealReview> selectMealReviewList(MealReview mealReview) {
        return mealReviewMapper.selectMealReviewList(mealReview);
    }

    @Override
    public List<MealReview> selectMyReviewList(MealReview mealReview) {
        mealReview.setUserId(SecurityUtils.getUserId());
        return mealReviewMapper.selectMyReviewList(mealReview);
    }

    /**
     * 一单一评：必须是自己的订单、已完成、且还没评价过。
     * 唯一约束 {@code uk_meal_review_order} 是最后一道防线，这里先给出可读的提示。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitReview(MealReview mealReview) {
        MealOrder order = mealOrderMapper.selectMealOrderById(mealReview.getOrderId());
        if (order == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        MealScopeGuard.assertSameUser(order.getUserId(), "订单");
        if (!MealOrder.STATUS_FINISHED.equals(order.getStatus())) {
            throw new ServiceException("订单完成后才能评价");
        }
        if (mealReviewMapper.selectMealReviewByOrderId(mealReview.getOrderId()) != null) {
            throw new ServiceException("该订单已经评价过了");
        }

        mealReview.setReviewId(null);
        mealReview.setDeptId(order.getDeptId());
        mealReview.setUserId(order.getUserId());
        mealReview.setUserName(currentNickName());
        if (StringUtils.isEmpty(mealReview.getAnonymous())) {
            mealReview.setAnonymous("0");
        }
        mealReview.setCreateBy(SecurityUtils.getUsername());
        return mealReviewMapper.insertMealReview(mealReview);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealReviewByIds(Long[] reviewIds) {
        if (reviewIds == null || reviewIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long reviewId : reviewIds) {
            MealReview exist = mealReviewMapper.selectMealReviewById(reviewId);
            if (exist == null) {
                throw new ServiceException("评价不存在或已删除");
            }
            if (!SecurityUtils.isAdmin()) {
                MealScopeGuard.assertSameDept(exist.getDeptId(), "评价");
            }
        }
        return mealReviewMapper.deleteMealReviewByIds(reviewIds);
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
