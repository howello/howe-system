package com.howe.pregnancy.service.impl;

import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregCheckup;
import com.howe.pregnancy.domain.PregExpense;
import com.howe.pregnancy.domain.PregReport;
import com.howe.pregnancy.domain.dto.ExpenseSaveBody;
import com.howe.pregnancy.domain.vo.ExpenseVO;
import com.howe.pregnancy.mapper.PregCheckupMapper;
import com.howe.pregnancy.mapper.PregExpenseMapper;
import com.howe.pregnancy.mapper.PregReportMapper;
import com.howe.pregnancy.service.IPregExpenseService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import com.howe.pregnancy.util.RequestHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一费用服务实现
 *
 * <p>金额表示已发生的实际支出，全部选填；未填写金额不会创建费用行，{@code 0.00}
 * 会创建零元明细。待产包物品使用 {@code TOTAL} 槽位，清空后重填恢复同一费用行。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class PregExpenseServiceImpl implements IPregExpenseService {

    private final PregExpenseMapper expenseMapper;

    private final PregCheckupMapper checkupMapper;

    private final PregReportMapper reportMapper;

    private final PregnancyAccessGuard accessGuard;

    @Override
    public List<ExpenseVO> list(LocalDate from, LocalDate to, String category, String sourceType) {
        accessGuard.requireReadable();
        return expenseMapper.selectList(from, to, category, sourceType)
                .stream().map(this::toVO).toList();
    }

    @Override
    public List<ExpenseVO> listBySource(String sourceType, Long sourceId) {
        return expenseMapper.selectBySource(sourceType, sourceId).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseVO create(String sourceType, Long sourceId, ExpenseSaveBody body) {
        accessGuard.requireWritable();
        assertSource(sourceType, sourceId);
        if (body.getParentVersion() == null) {
            throw PregnancyException.invalid("父资源版本不能为空");
        }
        BigDecimal amount = parseAmount(body.getAmount());
        LocalDate spentDate = parseSpentDate(body.getSpentDate());
        Long actor = accessGuard.currentUserId();
        String requestId = requireRequestId(body.getRequestId());
        String hash = RequestHashUtils.hash(sourceType, sourceId, body.getCategoryCode(),
                body.getLabel(), body.getAmount(), body.getSpentDate());

        PregExpense existing = expenseMapper.selectByRequest(actor, requestId);
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) {
                throw PregnancyException.idempotencyConflict(requestId);
            }
            if ("2".equals(existing.getDelFlag())) {
                throw PregnancyException.requestConsumed(requestId);
            }
            return toVO(existing);
        }

        PregExpense expense = new PregExpense();
        expense.setSourceType(sourceType);
        expense.setSourceId(sourceId);
        expense.setSourceSlot("S" + System.nanoTime());
        expense.setCategoryCode(body.getCategoryCode());
        expense.setLabel(body.getLabel());
        expense.setAmount(amount);
        expense.setSpentDate(spentDate);
        expense.setActorUserId(actor);
        expense.setRequestId(requestId);
        expense.setRequestHash(hash);
        expense.setCreateBy(SecurityUtils.getUsername());
        expenseMapper.insert(expense);
        bumpParent(sourceType, sourceId, body.getParentVersion());
        return toVO(expenseMapper.selectById(expense.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseVO update(String sourceType, Long sourceId, Long expenseId, ExpenseSaveBody body) {
        accessGuard.requireWritable();
        PregExpense expense = requireExpense(sourceType, sourceId, expenseId);
        if (body.getVersion() == null) {
            throw PregnancyException.versionConflict();
        }
        if (body.getParentVersion() == null) {
            throw PregnancyException.invalid("父资源版本不能为空");
        }
        if (body.getCategoryCode() != null) {
            if (body.getCategoryCode().isBlank()) {
                throw PregnancyException.invalid("费用分类不能为空");
            }
            expense.setCategoryCode(body.getCategoryCode());
        }
        if (body.getLabel() != null) {
            expense.setLabel(body.getLabel());
        }
        if (body.getAmount() != null && !body.getAmount().isBlank()) {
            expense.setAmount(parseAmount(body.getAmount()));
        }
        if (body.getSpentDate() != null && !body.getSpentDate().isBlank()) {
            expense.setSpentDate(parseSpentDate(body.getSpentDate()));
        }
        expense.setVersion(body.getVersion());
        expense.setUpdateBy(SecurityUtils.getUsername());
        if (expenseMapper.update(expense) == 0) {
            throw PregnancyException.versionConflict();
        }
        bumpParent(sourceType, sourceId, body.getParentVersion());
        return toVO(expenseMapper.selectById(expenseId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String sourceType, Long sourceId, Long expenseId, Integer version) {
        accessGuard.requireWritable();
        requireExpense(sourceType, sourceId, expenseId);
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
        if (expenseMapper.logicalDelete(expenseId, version) == 0) {
            throw PregnancyException.versionConflict();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySource(String sourceType, Long sourceId) {
        expenseMapper.logicalDeleteBySource(sourceType, sourceId);
    }

    @Override
    public Map<String, Object> statistics(LocalDate from, LocalDate to) {
        accessGuard.requireReadable();
        List<Map<String, Object>> rows = expenseMapper.summarize(from, to);
        BigDecimal total = expenseMapper.sumAmount(from, to);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> bySource = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String category = row.get("categoryCode") == null ? "" : String.valueOf(row.get("categoryCode"));
            String sourceType = row.get("sourceType") == null ? "" : String.valueOf(row.get("sourceType"));
            BigDecimal amount = toDecimal(row.get("total"));
            byCategory.merge(category, amount, BigDecimal::add);
            bySource.merge(sourceType, amount, BigDecimal::add);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", PregnancyTimeUtils.formatDate(from));
        result.put("to", PregnancyTimeUtils.formatDate(to));
        result.put("total", (total == null ? BigDecimal.ZERO : total)
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        result.put("byCategory", toTextMap(byCategory));
        result.put("bySource", toTextMap(bySource));
        return result;
    }

    @Override
    public ExpenseVO findBySlot(String sourceType, Long sourceId, String slot) {
        PregExpense expense = expenseMapper.selectBySourceSlot(sourceType, sourceId, slot);
        return expense == null ? null : toVO(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsertTotal(String sourceType, Long sourceId, String category, String label,
                            BigDecimal amount, LocalDate spentDate, String requestId) {
        PregExpense existing = expenseMapper.selectBySourceSlot(sourceType, sourceId,
                PregnancyConstants.EXPENSE_SLOT_TOTAL);
        if (amount == null) {
            if (existing != null) {
                expenseMapper.logicalDelete(existing.getId(), existing.getVersion());
            }
            return;
        }
        if (existing == null) {
            PregExpense expense = new PregExpense();
            expense.setSourceType(sourceType);
            expense.setSourceId(sourceId);
            expense.setSourceSlot(PregnancyConstants.EXPENSE_SLOT_TOTAL);
            expense.setCategoryCode(category);
            expense.setLabel(label);
            expense.setAmount(amount);
            expense.setSpentDate(spentDate == null ? PregnancyTimeUtils.businessToday() : spentDate);
            expense.setActorUserId(accessGuard.currentUserId());
            expense.setRequestId(requestId);
            expense.setRequestHash(RequestHashUtils.hash(sourceType, sourceId, amount.toPlainString()));
            expense.setCreateBy(SecurityUtils.getUsername());
            expenseMapper.insert(expense);
            return;
        }
        existing.setCategoryCode(category);
        existing.setLabel(label);
        existing.setAmount(amount);
        existing.setSpentDate(spentDate == null ? existing.getSpentDate() : spentDate);
        existing.setUpdateBy(SecurityUtils.getUsername());
        // 待产包总额不允许并发修改导致金额错乱，命中版本冲突即刷新
        if (expenseMapper.update(existing) == 0) {
            throw PregnancyException.versionConflict();
        }
    }

    /**
     * 校验来源真实存在
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     */
    private void assertSource(String sourceType, Long sourceId) {
        switch (sourceType) {
            case PregnancyConstants.EXPENSE_SOURCE_CHECKUP -> {
                PregCheckup checkup = checkupMapper.selectById(sourceId);
                if (checkup == null) {
                    throw PregnancyException.notFound();
                }
            }
            case PregnancyConstants.EXPENSE_SOURCE_REPORT -> {
                PregReport report = reportMapper.selectById(sourceId);
                if (report == null) {
                    throw PregnancyException.notFound();
                }
            }
            case PregnancyConstants.EXPENSE_SOURCE_BAG_ITEM -> {
                // 待产包金额由物品 PATCH 维护，不走独立费用入口
                throw PregnancyException.invalid("待产包金额请在物品详情中维护");
            }
            default -> throw PregnancyException.invalid("未知的费用来源类型：" + sourceType);
        }
    }

    /**
     * 递增父资源版本
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param version    父版本
     */
    private void bumpParent(String sourceType, Long sourceId, Integer version) {
        if (!PregnancyConstants.EXPENSE_SOURCE_CHECKUP.equals(sourceType)) {
            return;
        }
        if (checkupMapper.bumpVersion(sourceId, version) == 0) {
            throw PregnancyException.versionConflict();
        }
    }

    /**
     * 校验费用属于指定来源
     *
     * @param sourceType 来源类型
     * @param sourceId   来源 ID
     * @param expenseId  费用 ID
     * @return 费用
     */
    private PregExpense requireExpense(String sourceType, Long sourceId, Long expenseId) {
        PregExpense expense = expenseMapper.selectById(expenseId);
        if (expense == null || !sourceType.equals(expense.getSourceType())
                || !sourceId.equals(expense.getSourceId())) {
            throw PregnancyException.notFound();
        }
        return expense;
    }

    /**
     * 解析金额
     *
     * @param text 金额文本
     * @return 金额
     */
    private BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) {
            throw PregnancyException.invalid("金额不能为空");
        }
        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.signum() < 0) {
                throw PregnancyException.invalid("金额不能为负数");
            }
            if (amount.stripTrailingZeros().scale() > 2) {
                throw PregnancyException.invalid("金额最多保留两位小数");
            }
            return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (NumberFormatException e) {
            throw PregnancyException.invalid("金额格式不正确");
        } catch (ArithmeticException e) {
            throw PregnancyException.invalid("金额最多保留两位小数");
        }
    }

    /**
     * 解析支出日期
     *
     * @param text 日期文本
     * @return 日期
     */
    private LocalDate parseSpentDate(String text) {
        if (text == null || text.isBlank()) {
            return PregnancyTimeUtils.businessToday();
        }
        LocalDate date = PregnancyTimeUtils.parseDate(text, "支出日期");
        if (PregnancyTimeUtils.isAfterBusinessToday(date)) {
            throw PregnancyException.futureRecord("支出日期");
        }
        return date;
    }

    /**
     * 校验请求 ID
     *
     * @param requestId 请求 ID
     * @return 去空白后的请求 ID
     */
    private String requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw PregnancyException.invalid("请求ID不能为空");
        }
        return requestId.trim();
    }

    /**
     * 转 BigDecimal
     *
     * @param value 原始值
     * @return 数值
     */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    /**
     * 金额映射转字符串映射
     *
     * @param map 金额映射
     * @return 字符串映射
     */
    private Map<String, Object> toTextMap(Map<String, BigDecimal> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(key,
                value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
        return result;
    }

    /**
     * 转换展示对象
     *
     * @param expense 费用
     * @return 展示对象
     */
    private ExpenseVO toVO(PregExpense expense) {
        ExpenseVO vo = new ExpenseVO();
        vo.setId(String.valueOf(expense.getId()));
        vo.setSourceType(expense.getSourceType());
        vo.setSourceId(String.valueOf(expense.getSourceId()));
        vo.setSourceSlot(expense.getSourceSlot());
        vo.setCategoryCode(expense.getCategoryCode());
        vo.setLabel(expense.getLabel());
        vo.setAmount(expense.getAmount());
        vo.setSpentDate(PregnancyTimeUtils.formatDate(expense.getSpentDate()));
        vo.setActorUserId(expense.getActorUserId() == null ? null : String.valueOf(expense.getActorUserId()));
        vo.setVersion(expense.getVersion());
        return vo;
    }

    /**
     * 保留给导出的原始费用读取
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 费用列表
     */
    public List<PregExpense> rawList(LocalDate from, LocalDate to) {
        return new ArrayList<>(expenseMapper.selectList(from, to, null, null));
    }
}
