package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregExpense;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统一费用数据层
 *
 * @author howe
 */
public interface PregExpenseMapper {

    /**
     * 按条件查询费用
     *
     * @param from       起始支出日，可为空
     * @param to         结束支出日，可为空
     * @param category   分类，可为空
     * @param sourceType 来源类型，可为空
     * @return 费用列表
     */
    List<PregExpense> selectList(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                 @Param("category") String category,
                                 @Param("sourceType") String sourceType);

    /**
     * 按来源查询费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @return 费用列表
     */
    List<PregExpense> selectBySource(@Param("sourceType") String sourceType,
                                     @Param("sourceId") Long sourceId);

    /**
     * 按来源与槽位查询单条费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param sourceSlot 槽位
     * @return 费用，不存在返回 null
     */
    PregExpense selectBySourceSlot(@Param("sourceType") String sourceType,
                                   @Param("sourceId") Long sourceId,
                                   @Param("sourceSlot") String sourceSlot);

    /**
     * 按主键查询
     *
     * @param id 费用 ID
     * @return 费用，不存在返回 null
     */
    PregExpense selectById(@Param("id") Long id);

    /**
     * 按请求 ID 查询（含软删）
     *
     * @param actorUserId 操作人
     * @param requestId   请求 ID
     * @return 费用，不存在返回 null
     */
    PregExpense selectByRequest(@Param("actorUserId") Long actorUserId,
                                @Param("requestId") String requestId);

    /**
     * 新增费用
     *
     * @param expense 费用
     * @return 影响行数
     */
    int insert(PregExpense expense);

    /**
     * 按版本更新
     *
     * @param expense 费用
     * @return 影响行数
     */
    int update(PregExpense expense);

    /**
     * 逻辑删除单条费用
     *
     * @param id      费用 ID
     * @param version 版本
     * @return 影响行数
     */
    int logicalDelete(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 逻辑删除某来源的全部费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @return 影响行数
     */
    int logicalDeleteBySource(@Param("sourceType") String sourceType,
                              @Param("sourceId") Long sourceId);

    /**
     * 按日统计费用（仅未删除且有金额的行）
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 每行含 spentDate 与 total
     */
    List<Map<String, Object>> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 查询某天产生的费用
     *
     * @param date 日期
     * @return 费用列表
     */
    List<PregExpense> selectByDay(@Param("date") LocalDate date);

    /**
     * 按分类与来源类型汇总
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 每行含 categoryCode、sourceType、total
     */
    List<Map<String, Object>> summarize(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 统计费用总额
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 总额，无数据返回 null
     */
    java.math.BigDecimal sumAmount(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
