package com.howe.meal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 点餐首页统计 数据层
 *
 * <p>只做基础聚合取数；补 0、排序、Top N 截断等口径在 Service 层处理。
 * 所有查询都带显式的家庭范围参数（由 Service 依据 {@code @DataScope} 的同一套
 * 规则给出），避免绕过既有数据权限。</p>
 *
 * @author howe
 */
@Mapper
public interface MealStatsMapper {

    /**
     * 订单状态计数（本家庭范围，已排除逻辑删除）
     *
     * @param deptId   家庭ID；null 表示不限（平台管理员）
     * @param startDay 起始日期 yyyy-MM-dd（含），用于限定今日窗口
     * @param endDay   结束日期 yyyy-MM-dd（含）
     * @return 每行含 status 与 cnt
     */
    List<Map<String, Object>> selectOrderCountByStatus(@Param("deptId") Long deptId,
                                                       @Param("startDay") String startDay,
                                                       @Param("endDay") String endDay);

    /**
     * 订单按日计数（本家庭范围）
     *
     * @param deptId   家庭ID；null 表示不限
     * @param startDay 起始日期 yyyy-MM-dd（含）
     * @param endDay   结束日期 yyyy-MM-dd（含）
     * @return 每行含 d_date 与 cnt
     */
    List<Map<String, Object>> selectOrderCountByDay(@Param("deptId") Long deptId,
                                                    @Param("startDay") String startDay,
                                                    @Param("endDay") String endDay);

    /**
     * 评价平均分（本家庭范围，已排除逻辑删除）
     *
     * @param deptId 家庭ID；null 表示不限
     * @return 平均分；无评价时为 null
     */
    Double selectAvgReviewScore(@Param("deptId") Long deptId);

    /**
     * 菜品状态计数（公共菜谱 + 本家庭私有菜）
     *
     * @param deptId 家庭ID；null 表示不限（平台管理员看全量）
     * @return 每行含 status 与 cnt
     */
    List<Map<String, Object>> selectDishCountByStatus(@Param("deptId") Long deptId);

    /**
     * 菜品按归属计数（公共菜谱与家庭私有菜各多少）
     *
     * @param deptId 家庭ID；null 表示不限
     * @return 单行含 public_cnt 与 family_cnt
     */
    Map<String, Object> selectDishCountByOwnership(@Param("deptId") Long deptId);

    /**
     * 分类计数（公共分类 + 本家庭私有分类）
     *
     * @param deptId 家庭ID；null 表示不限
     * @return 分类数
     */
    long selectCategoryCount(@Param("deptId") Long deptId);

    /**
     * 版本记录总数（未删除）
     *
     * @return 总数
     */
    long selectAppVersionCount();

    /**
     * 近 N 天被点份数 Top 菜品（本家庭范围，不限订单状态）
     *
     * @param deptId   家庭ID；null 表示不限
     * @param startDay 起始日期 yyyy-MM-dd（含）
     * @param endDay   截止日期 yyyy-MM-dd（含）
     * @param limit    取前 N 条
     * @return 每行含 dish_name 与 cnt，按 cnt 倒序
     */
    List<Map<String, Object>> selectHotDishes(@Param("deptId") Long deptId,
                                              @Param("startDay") String startDay,
                                              @Param("endDay") String endDay,
                                              @Param("limit") int limit);

    /**
     * 待办：待审提案数
     *
     * @param deptId 家庭ID；null 表示不限
     * @return 待审提案数（status='0'）
     */
    long selectPendingProposalCount(@Param("deptId") Long deptId);

    /**
     * 待办：待接订单数
     *
     * @param deptId 家庭ID；null 表示不限
     * @return 待接订单数（status='0'）
     */
    long selectPendingOrderCount(@Param("deptId") Long deptId);

    /**
     * 个人足迹：我下过的订单数（含各状态，排除逻辑删除）
     *
     * @param userId 用户ID
     * @return 订单数
     */
    long selectMyOrderCount(@Param("userId") Long userId);

    /**
     * 个人足迹：我提交的提案数（排除逻辑删除）
     *
     * @param userId 用户ID
     * @return 提案数
     */
    long selectMyProposalCount(@Param("userId") Long userId);
}
