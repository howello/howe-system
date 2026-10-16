package com.howe.meal.service;

import com.howe.meal.domain.vo.MealAppVersionOverview;
import com.howe.meal.domain.vo.MealDishOverview;
import com.howe.meal.domain.vo.MealHomeStats;
import com.howe.meal.domain.vo.MealHotDishes;

/**
 * 点餐首页统计服务
 *
 * <p>口径与数据范围（本部门隔离）封装在本服务内，表结构知识不外泄到 admin 层。
 * 家庭管理员与厨师只看到本家庭数据，平台管理员看全量。</p>
 *
 * @author howe
 */
public interface IMealStatsService {

    /**
     * 首页点餐统计（今日订单、状态分布、近 7 天趋势、评价均分）
     *
     * @return 点餐统计
     */
    MealHomeStats getHomeStats();

    /**
     * 菜品概况（上架/下架、分类数、公共与家庭菜谱数）
     *
     * @return 菜品概况
     */
    MealDishOverview getDishOverview();

    /**
     * App 版本概况（最新已发布版本、发布时间、是否强制更新、记录总数）
     *
     * @return 版本概况
     */
    MealAppVersionOverview getAppVersionOverview();

    /**
     * 热门菜品（近 30 天已完成订单中份数 Top 5）
     *
     * @return 热门菜品
     */
    MealHotDishes getHotDishes();

    /**
     * 待办：待审新菜提案数（无权限时由调用方决定是否展示）
     *
     * @return 待审提案数
     */
    long countPendingProposals();

    /**
     * 待办：待接订单数
     *
     * @return 待接订单数
     */
    long countPendingOrders();

    /**
     * 个人足迹：当前用户下过的订单数
     *
     * @return 订单数
     */
    long countMyOrders();

    /**
     * 个人足迹：当前用户提交的提案数
     *
     * @return 提案数
     */
    long countMyProposals();
}
