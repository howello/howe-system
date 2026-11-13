package com.howe.meal.mapper;

import com.howe.meal.domain.MealOrderCandidate;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 点餐-订单候选厨师 数据层
 *
 * <p>所有状态迁移都用带前置状态条件的更新实现并发裁决：影响 0 行表示竞争失败，
 * 调用方据此返回可读提示，不强行覆盖。候选人是接单资格的唯一依据，
 * 订单状态是胜负的最终裁决点。</p>
 *
 * @author howe
 */
public interface MealOrderCandidateMapper {
    /**
     * 批量插入候选人
     *
     * @param candidates 候选人列表
     * @return 插入行数
     */
    int insertCandidates(@Param("candidates") List<MealOrderCandidate> candidates);

    /**
     * 查询某订单的全部候选人
     *
     * @param orderId 订单ID
     * @return 候选人列表
     */
    List<MealOrderCandidate> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 查询某订单中指定用户的候选人记录
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return 候选人，不存在时返回 {@code null}
     */
    MealOrderCandidate selectByOrderAndUser(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * 接单：把自己的当前轮次待响应候选人置为已接单
     *
     * @param orderId        订单ID
     * @param userId         用户ID
     * @param candidateRound 订单当前候选轮次
     * @param expectedVersion 期望的候选数据版本，为 {@code null} 时不限制
     * @return 影响行数，0 表示已不是当前轮次的待响应候选人
     */
    int acceptCandidate(@Param("orderId") Long orderId, @Param("userId") Long userId,
            @Param("candidateRound") Long candidateRound, @Param("expectedVersion") Long expectedVersion);

    /**
     * 拒绝：把自己的当前轮次待响应候选人置为已拒绝
     *
     * @param orderId        订单ID
     * @param userId         用户ID
     * @param candidateRound 订单当前候选轮次
     * @param expectedVersion 期望的候选数据版本，为 {@code null} 时不限制
     * @return 影响行数，0 表示已不是当前轮次的待响应候选人
     */
    int refuseCandidate(@Param("orderId") Long orderId, @Param("userId") Long userId,
            @Param("candidateRound") Long candidateRound, @Param("expectedVersion") Long expectedVersion);

    /**
     * 统计某订单当前轮次仍待响应的候选人数
     *
     * @param orderId        订单ID
     * @param candidateRound 当前候选轮次
     * @return 待响应人数
     */
    int countPending(@Param("orderId") Long orderId, @Param("candidateRound") Long candidateRound);

    /**
     * 把某订单当前轮次除指定用户外的待响应候选人全部置为已失效
     *
     * @param orderId        订单ID
     * @param acceptedUserId 已接单用户ID
     * @param candidateRound 当前候选轮次
     * @return 影响行数
     */
    int invalidateOthers(@Param("orderId") Long orderId, @Param("acceptedUserId") Long acceptedUserId,
            @Param("candidateRound") Long candidateRound);

    /**
     * 把某订单当前轮次全部待响应候选人置为已失效
     *
     * @param orderId        订单ID
     * @param candidateRound 当前候选轮次
     * @return 影响行数
     */
    int invalidatePending(@Param("orderId") Long orderId, @Param("candidateRound") Long candidateRound);

    /**
     * 把某订单全部候选人置为已失效（不论当前状态），用于订单关闭后的收尾
     *
     * @param orderId 订单ID
     * @return 影响行数
     */
    int invalidateAll(@Param("orderId") Long orderId);

    /**
     * 把某订单指定用户的当前轮次待响应候选人置为已超时未响应
     *
     * @param orderId        订单ID
     * @param userId         用户ID
     * @param candidateRound 当前候选轮次
     * @return 影响行数
     */
    int expireCandidate(@Param("orderId") Long orderId, @Param("userId") Long userId,
            @Param("candidateRound") Long candidateRound);

    /**
     * 指派转公开：把原指派人迁入新轮次，保留其拒绝/超时状态并更新有效期
     *
     * @param orderId    订单ID
     * @param userId     被指派人用户ID
     * @param oldRound   原候选轮次
     * @param newRound   新候选轮次
     * @param expireTime 新截止时间
     * @return 影响行数
     */
    int promoteCandidateRound(@Param("orderId") Long orderId, @Param("userId") Long userId,
            @Param("oldRound") Long oldRound, @Param("newRound") Long newRound,
            @Param("expireTime") Date expireTime);

    /**
     * 筛选出当前用户已不再是待响应候选人的订单ID
     *
     * <p>用于 {@code poll} 阶段过滤陈旧的 {@code NEW_ORDER} 通知：订单已不是待接单，
     * 或自己已不是待响应候选人时，本轮不再展示该条新单提醒。</p>
     *
     * @param userId   当前用户ID
     * @param orderIds 待检查的订单ID集合
     * @return 应当剔除的订单ID
     */
    List<Long> selectNoLongerPendingOrderIds(@Param("userId") Long userId,
            @Param("orderIds") List<Long> orderIds);

    /**
     * 为订单补充候选人：已存在候选人的用户跳过
     *
     * @param candidates 候选人列表
     * @return 实际插入行数
     */
    int insertCandidatesIgnoringExisting(@Param("candidates") List<MealOrderCandidate> candidates);

    /**
     * 指派转公开时查询订单当前的候选人用户ID，避免重复入池
     *
     * @param orderId 订单ID
     * @return 已有候选人用户ID集合
     */
    List<Long> selectCandidateUserIds(@Param("orderId") Long orderId);

    /**
     * 延长某订单指定候选人在当前轮次的响应截止时间（指派转公开后截止时间重置）
     *
     * @param orderId        订单ID
     * @param userId         用户ID
     * @param candidateRound 当前候选轮次
     * @param expireTime     新的截止时间
     * @return 影响行数
     */
    int updateCandidateExpireTime(@Param("orderId") Long orderId, @Param("userId") Long userId,
            @Param("candidateRound") Long candidateRound, @Param("expireTime") Date expireTime);
}
