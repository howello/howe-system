package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.vo.ExpenseVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统一费用服务
 *
 * <p>所有金额只保存在费用明细中；产检、报告与待产包物品通过来源关系读写同一数据，
 * 花费页只做汇总，不另建记账入口。</p>
 *
 * @author howe
 */
public interface IPregExpenseService {

    /**
     * 查询费用列表
     *
     * @param from       起始支出日，可为空
     * @param to         结束支出日，可为空
     * @param category   分类，可为空
     * @param sourceType 来源类型，可为空
     * @return 费用列表
     */
    List<ExpenseVO> list(LocalDate from, LocalDate to, String category, String sourceType);

    /**
     * 按来源查询费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @return 费用列表
     */
    List<ExpenseVO> listBySource(String sourceType, Long sourceId);

    /**
     * 新增来源费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param body       请求
     * @return 新建费用
     */
    ExpenseVO create(String sourceType, Long sourceId, com.howe.pregnancy.domain.dto.ExpenseSaveBody body);

    /**
     * 编辑来源费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param expenseId  费用 ID
     * @param body       请求
     * @return 更新后的费用
     */
    ExpenseVO update(String sourceType, Long sourceId, Long expenseId,
                     com.howe.pregnancy.domain.dto.ExpenseSaveBody body);

    /**
     * 删除来源费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param expenseId  费用 ID
     * @param version    费用版本
     */
    void delete(String sourceType, Long sourceId, Long expenseId, Integer version);

    /**
     * 逻辑删除某来源的全部费用（来源删除时调用）
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     */
    void deleteBySource(String sourceType, Long sourceId);

    /**
     * 查询费用统计
     *
     * @param from 起始支出日，可为空
     * @param to   结束支出日，可为空
     * @return 统计结果
     */
    Map<String, Object> statistics(LocalDate from, LocalDate to);

    /**
     * 按来源与槽位读取单条费用
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param slot       槽位
     * @return 费用，不存在返回 null
     */
    ExpenseVO findBySlot(String sourceType, Long sourceId, String slot);

    /**
     * 维护来源的单条总额（待产包物品使用）
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param category   分类
     * @param label      名称
     * @param amount     金额，null 表示清除
     * @param spentDate  支出日期
     * @param requestId  请求 ID
     */
    void upsertTotal(String sourceType, Long sourceId, String category, String label,
                     java.math.BigDecimal amount, LocalDate spentDate, String requestId);
}
