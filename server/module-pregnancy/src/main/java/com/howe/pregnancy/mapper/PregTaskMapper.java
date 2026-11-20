package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregTask;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台任务数据层
 *
 * <p>领取、续租与提交都必须带租约条件，避免执行者失去租约后仍回写结果。</p>
 *
 * @author howe
 */
public interface PregTaskMapper {

    /**
     * 查询可立即执行的任务 ID（用于领取）
     *
     * @param now   当前时刻
     * @param limit 数量
     * @return 任务 ID 列表
     */
    List<Long> selectDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 保存阶段结果（保持 RUNNING 与租约不变）
     *
     * @param id         任务 ID
     * @param leaseToken 租约令牌
     * @param stageJson  阶段结果
     * @param now        当前时刻
     * @return 影响行数，0 表示租约失效
     */
    int updateStage(@Param("id") Long id, @Param("leaseToken") String leaseToken,
                    @Param("stageJson") String stageJson, @Param("now") LocalDateTime now);

    /**
     * 为尚未执行的提醒更新计划内容与执行时刻
     *
     * @param id              任务 ID
     * @param businessVersion 业务版本
     * @param payloadJson     受控入参
     * @param nextRunAt       执行时刻
     * @return 影响行数
     */
    int reschedulePending(@Param("id") Long id, @Param("businessVersion") Integer businessVersion,
                          @Param("payloadJson") String payloadJson,
                          @Param("nextRunAt") LocalDateTime nextRunAt);

    /**
     * 提高任务尝试上限（显式重试开启新一轮）
     *
     * @param id          任务 ID
     * @param maxAttempts 新上限
     * @return 影响行数
     */
    int extendAttempts(@Param("id") Long id, @Param("maxAttempts") Integer maxAttempts);

    /**
     * 按租约条件领取任务
     *
     * @param id         任务 ID
     * @param leaseToken 新租约令牌
     * @param leaseUntil 租约到期时刻
     * @param now        当前时刻
     * @return 影响行数，0 表示已被其他执行者领取
     */
    int claim(@Param("id") Long id, @Param("leaseToken") String leaseToken,
              @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now);

    /**
     * 续租
     *
     * @param id         任务 ID
     * @param leaseToken 租约令牌
     * @param leaseUntil 新到期时刻
     * @param now        当前时刻
     * @return 影响行数，0 表示已失去执行权
     */
    int renewLease(@Param("id") Long id, @Param("leaseToken") String leaseToken,
                   @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now);

    /**
     * 按主键查询
     *
     * @param id 任务 ID
     * @return 任务，不存在返回 null
     */
    PregTask selectById(@Param("id") Long id);

    /**
     * 按去重键查询
     *
     * @param dedupKey 去重键
     * @return 任务，不存在返回 null
     */
    PregTask selectByDedupKey(@Param("dedupKey") String dedupKey);

    /**
     * 新增任务
     *
     * @param task 任务
     * @return 影响行数
     */
    int insert(PregTask task);

    /**
     * 提交成功结果（带租约校验）
     *
     * @param id          任务 ID
     * @param leaseToken  租约令牌
     * @param status      目标状态
     * @param stageJson   阶段结果
     * @param resultRef   结果引用
     * @param expiresAt   结果到期时刻
     * @param sentAt      提醒发送时刻
     * @param now         当前时刻
     * @return 影响行数，0 表示租约失效
     */
    int complete(@Param("id") Long id, @Param("leaseToken") String leaseToken,
                 @Param("status") String status, @Param("stageJson") String stageJson,
                 @Param("resultRef") String resultRef, @Param("expiresAt") LocalDateTime expiresAt,
                 @Param("sentAt") LocalDateTime sentAt, @Param("now") LocalDateTime now);

    /**
     * 提交失败并安排重试或终态
     *
     * @param id          任务 ID
     * @param leaseToken  租约令牌
     * @param status      目标状态
     * @param errorCode   错误码
     * @param attempt     本次尝试后的累计次数
     * @param maxAttempts 调整后的上限
     * @param nextRunAt   下次执行时刻
     * @param stageJson   阶段结果
     * @param now         当前时刻
     * @return 影响行数
     */
    int fail(@Param("id") Long id, @Param("leaseToken") String leaseToken,
             @Param("status") String status, @Param("errorCode") String errorCode,
             @Param("attempt") Integer attempt, @Param("maxAttempts") Integer maxAttempts,
             @Param("nextRunAt") LocalDateTime nextRunAt, @Param("stageJson") String stageJson,
             @Param("now") LocalDateTime now);

    /**
     * 直接更新状态（取消、跳过等，不做租约校验）
     *
     * @param id        任务 ID
     * @param status    目标状态
     * @param errorCode 错误码，可为空
     * @param now       当前时刻
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("errorCode") String errorCode, @Param("now") LocalDateTime now);

    /**
     * 按业务资源取消未完成的任务
     *
     * @param kind       任务类型
     * @param businessId 业务 ID
     * @param now        当前时刻
     * @return 影响行数
     */
    int cancelByBusiness(@Param("kind") String kind, @Param("businessId") Long businessId,
                         @Param("now") LocalDateTime now);

    /**
     * 查询租约过期的运行中任务
     *
     * @param now   当前时刻
     * @param limit 数量
     * @return 任务列表
     */
    List<PregTask> selectExpiredRunning(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 恢复过期任务：清除租约并回到可执行状态
     *
     * @param id         任务 ID
     * @param leaseToken 过期前的租约令牌（防止误改已被重新领取的行）
     * @param status     目标状态
     * @param errorCode  错误码
     * @param now        当前时刻
     * @return 影响行数
     */
    int recoverExpired(@Param("id") Long id, @Param("leaseToken") String leaseToken,
                       @Param("status") String status, @Param("errorCode") String errorCode,
                       @Param("now") LocalDateTime now);

    /**
     * 查询申请者的导出任务
     *
     * @param requesterUserId 请求人
     * @return 任务列表
     */
    List<PregTask> selectExportsByRequester(@Param("requesterUserId") Long requesterUserId);

    /**
     * 按业务资源查询最新任务
     *
     * @param kind       任务类型
     * @param businessId 业务 ID
     * @return 任务，不存在返回 null
     */
    PregTask selectLatestByBusiness(@Param("kind") String kind, @Param("businessId") Long businessId);

    /**
     * 查询最近一次提醒任务
     *
     * @return 任务，不存在返回 null
     */
    PregTask selectLatestReminder();

    /**
     * 把到期的导出结果标记为过期（释放结果引用）
     *
     * @param now 当前时刻
     * @return 影响行数
     */
    int expireFinishedExports(@Param("now") LocalDateTime now);
}
