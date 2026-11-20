package com.howe.pregnancy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregBag;
import com.howe.pregnancy.domain.PregBagItem;
import com.howe.pregnancy.domain.PregExpense;
import com.howe.pregnancy.domain.dto.BagItemSaveBody;
import com.howe.pregnancy.domain.vo.BagItemVO;
import com.howe.pregnancy.domain.vo.BagVO;
import com.howe.pregnancy.mapper.PregBagMapper;
import com.howe.pregnancy.service.IPregBagService;
import com.howe.pregnancy.service.IPregExpenseService;
import com.howe.pregnancy.service.IPregTaskService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancySeedProvider;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import com.howe.pregnancy.util.RequestHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待产包服务实现
 *
 * <p>物品实付金额统一保存在费用明细中，随物品 PATCH 的 {@code expense} 字段原子维护：
 * 省略不改，{@code null} 清除，{@code 0.00} 保留零元明细；切换准备状态不影响金额。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PregBagServiceImpl implements IPregBagService {

    private final PregBagMapper bagMapper;

    private final PregnancyAccessGuard accessGuard;

    private final PregnancySeedProvider seedProvider;

    private final IPregExpenseService expenseService;

    private final IPregTaskService taskService;

    @Override
    public List<BagVO> listBags() {
        accessGuard.requireReadable();
        ensureSeeded();
        List<BagVO> result = new ArrayList<>();
        for (PregBag bag : bagMapper.selectBags()) {
            result.add(toBagVO(bag, true));
        }
        return result;
    }

    @Override
    public List<BagItemVO> listItems(Long bagId, String category, Boolean prepared) {
        accessGuard.requireReadable();
        ensureSeeded();
        validateCategory(category);
        return bagMapper.selectItems(bagId, category, prepared).stream()
                .map(this::toItemVO).toList();
    }

    @Override
    public BagItemVO getItem(Long id) {
        accessGuard.requireReadable();
        PregBagItem item = requireItem(id);
        return toItemVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BagItemVO createItem(BagItemSaveBody body) {
        accessGuard.requireWritable();
        ensureSeeded();
        validateCategory(body.getCategory());
        Long bagId = parseOptionalId(body.getBagId());
        if (bagId == null) {
            throw PregnancyException.invalid("清单ID不能为空");
        }
        if (bagMapper.selectBagById(bagId) == null) {
            throw PregnancyException.notFound();
        }
        Long actor = accessGuard.currentUserId();
        String requestId = requireRequestId(body.getRequestId());
        LocalDate purchaseDate = parsePurchaseDate(body.getPurchaseDate());
        String hash = RequestHashUtils.hash(body.getCategory(), body.getName(), body.getQuantity(),
                body.getUnit(), body.getPurpose(), body.getBrand(), body.getChannel(),
                body.getPrepared(), body.getExpense() == null ? null : body.getExpense().getAmount());

        if (purchaseDate != null) {
            hash = RequestHashUtils.hash(hash, purchaseDate);
        }

        PregBagItem existing = bagMapper.selectItemByRequest(requestId);
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) {
                throw PregnancyException.idempotencyConflict(requestId);
            }
            if ("2".equals(existing.getDelFlag())) {
                throw PregnancyException.requestConsumed(requestId);
            }
            return toItemVO(existing);
        }

        PregBagItem item = new PregBagItem();
        item.setBagId(bagId);
        item.setCategory(body.getCategory());
        item.setName(body.getName().trim());
        item.setQuantity(body.getQuantity());
        item.setUnit(body.getUnit());
        item.setPurpose(body.getPurpose());
        item.setBrand(body.getBrand());
        item.setChannel(body.getChannel());
        item.setPurchaseDate(purchaseDate);
        item.setPrepared(Boolean.TRUE.equals(body.getPrepared()));
        item.setSortOrder(nextSortOrder(bagId));
        item.setActorUserId(actor);
        item.setRequestId(requestId);
        item.setRequestHash(hash);
        item.setCreateBy(SecurityUtils.getUsername());
        bagMapper.insertItem(item);
        applyExpense(item, body.getExpense());
        return toItemVO(bagMapper.selectItemById(item.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BagItemVO updateItem(Long id, com.howe.pregnancy.domain.dto.BagItemUpdateBody body) {
        accessGuard.requireWritable();
        PregBagItem item = requireItem(id);
        if (body.getVersion() == null) {
            throw PregnancyException.versionConflict();
        }
        if (body.getCategory() != null) {
            validateCategory(body.getCategory());
            item.setCategory(body.getCategory());
        }
        if (body.getName() != null) {
            if (body.getName().isBlank()) {
                throw PregnancyException.invalid("物品名称不能为空");
            }
            item.setName(body.getName().trim());
        }
        if (body.getQuantity() != null) {
            item.setQuantity(body.getQuantity());
        }
        if (body.getUnit() != null) {
            item.setUnit(body.getUnit());
        }
        if (body.getPurpose() != null) {
            item.setPurpose(body.getPurpose());
        }
        if (body.getBrand() != null) {
            item.setBrand(body.getBrand());
        }
        if (body.getChannel() != null) {
            item.setChannel(body.getChannel());
        }
        if (body.getPurchaseDate() != null) {
            item.setPurchaseDate(parsePurchaseDate(body.getPurchaseDate()));
        }
        if (body.getPrepared() != null) {
            item.setPrepared(body.getPrepared());
        }
        item.setVersion(body.getVersion());
        item.setUpdateBy(SecurityUtils.getUsername());
        if (bagMapper.updateItem(item) == 0) {
            throw PregnancyException.versionConflict();
        }
        applyExpense(item, body.getExpense());
        return toItemVO(bagMapper.selectItemById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id, Integer version) {
        accessGuard.requireWritable();
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
        requireItem(id);
        if (bagMapper.logicalDeleteItem(id, version, SecurityUtils.getUsername()) == 0) {
            throw PregnancyException.versionConflict();
        }
        // 删除物品时软删其费用并取消未完成生成任务
        expenseService.deleteBySource(PregnancyConstants.EXPENSE_SOURCE_BAG_ITEM, id);
        taskService.cancelByBusiness(PregnancyConstants.TASK_ITEM_CONTENT, id);
    }

    @Override
    public Map<String, Object> summary(Long bagId, String category) {
        accessGuard.requireReadable();
        validateCategory(category);
        Map<String, Object> counts = bagMapper.summary(bagId, category);
        int total = counts == null || counts.get("total") == null
                ? 0 : ((Number) counts.get("total")).intValue();
        int preparedCount = counts == null || counts.get("preparedCount") == null
                ? 0 : ((Number) counts.get("preparedCount")).intValue();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Map<String, Object>> byCategory = new ArrayList<>();
        List<PregBagItem> items = bagMapper.selectItems(bagId, category, null);
        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        for (PregBagItem item : items) {
            PregExpense expense = null;
            var vo = expenseService.findBySlot(PregnancyConstants.EXPENSE_SOURCE_BAG_ITEM,
                    item.getId(), PregnancyConstants.EXPENSE_SLOT_TOTAL);
            if (vo != null) {
                expense = new PregExpense();
                expense.setAmount(vo.getAmount());
            }
            if (expense != null && expense.getAmount() != null) {
                subtotal = subtotal.add(expense.getAmount());
                categoryTotals.merge(item.getCategory(), expense.getAmount(), BigDecimal::add);
            }
        }
        categoryTotals.forEach((key, value) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", key);
            row.put("total", value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            byCategory.add(row);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("preparedCount", preparedCount);
        result.put("unpreparedCount", total - preparedCount);
        result.put("subtotal", subtotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        result.put("byCategory", byCategory);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int ensureSeeded() {
        JsonNode seed = seedProvider.bag();
        if (seed == null || seed.isMissingNode() || seed.isNull()) {
            return 0;
        }
        String seedKey = seed.path("seedKey").asText(PregnancyConstants.DEFAULT_BAG_SEED);
        PregBag bag = bagMapper.selectBagBySeed(seedKey);
        if (bag == null) {
            bag = new PregBag();
            bag.setSeedKey(seedKey);
            bag.setName(seed.path("name").asText("待产包"));
            bag.setNote(seed.path("note").isTextual() ? seed.path("note").asText() : null);
            bag.setSortOrder(0);
            bagMapper.insertBag(bag);
            bag = bagMapper.selectBagById(bag.getId());
        }
        JsonNode items = seed.path("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return 0;
        }
        int created = 0;
        int order = bagMapper.selectItems(bag.getId(), null, null).size();
        for (JsonNode node : items) {
            String itemSeed = node.path("seedKey").asText(null);
            if (itemSeed == null || itemSeed.isBlank()) {
                continue;
            }
            // 已存在（含软删）稳定键跳过，不覆盖用户修改也不恢复软删项
            if (bagMapper.selectItemBySeed(bag.getId(), itemSeed) != null) {
                continue;
            }
            PregBagItem item = new PregBagItem();
            item.setBagId(bag.getId());
            item.setSeedKey(itemSeed);
            item.setCategory(node.path("category").asText(PregnancyConstants.BAG_CATEGORY_MOTHER));
            item.setName(node.path("name").asText("物品"));
            item.setQuantity(node.path("quantity").isTextual() ? node.path("quantity").asText() : null);
            item.setUnit(node.path("unit").isTextual() ? node.path("unit").asText() : null);
            item.setPurpose(node.path("purpose").isTextual() ? node.path("purpose").asText() : null);
            item.setSortOrder(order++);
            item.setPrepared(Boolean.FALSE);
            item.setActorUserId(null);
            item.setCreateBy("system");
            bagMapper.insertItem(item);
            created++;
        }
        return created;
    }

    /**
     * 维护物品实付金额
     *
     * @param item    物品
     * @param expense 金额字段，可为 null 表示省略
     */
    private void applyExpense(PregBagItem item, BagItemSaveBody.ExpenseField expense) {
        if (expense == null) {
            return;
        }
        if (expense.getAmount() == null) {
            // 显式清除
            expenseService.upsertTotal(PregnancyConstants.EXPENSE_SOURCE_BAG_ITEM, item.getId(),
                    expense.getCategory() == null ? "BABY_ITEM" : expense.getCategory(),
                    expense.getLabel(), null, null, null);
            return;
        }
        if (expense.getAmount().isBlank()) {
            throw PregnancyException.invalid("金额不能为空");
        }
        BigDecimal amount = new BigDecimal(expense.getAmount().trim());
        LocalDate spentDate = expense.getSpentDate() == null || expense.getSpentDate().isBlank()
                ? PregnancyTimeUtils.businessToday()
                : PregnancyTimeUtils.parseDate(expense.getSpentDate(), "支出日期");
        expenseService.upsertTotal(PregnancyConstants.EXPENSE_SOURCE_BAG_ITEM, item.getId(),
                expense.getCategory() == null ? "BABY_ITEM" : expense.getCategory(),
                expense.getLabel(), amount, spentDate, null);
    }

    /**
     * 读取可选的购买日期。
     *
     * @param value 日期或空字符串
     * @return 购买日期
     */
    private LocalDate parsePurchaseDate(String value) {
        LocalDate date = PregnancyTimeUtils.parseOptionalDate(value, "购买日期");
        if (PregnancyTimeUtils.isAfterBusinessToday(date)) {
            throw PregnancyException.invalid("购买日期不能晚于今天");
        }
        return date;
    }

    /**
     * 计算下一个排序值
     *
     * @param bagId 清单 ID
     * @return 排序值
     */
    private int nextSortOrder(Long bagId) {
        List<PregBagItem> items = bagMapper.selectItems(bagId, null, null);
        int max = -1;
        for (PregBagItem item : items) {
            if (item.getSortOrder() != null && item.getSortOrder() > max) {
                max = item.getSortOrder();
            }
        }
        return max + 1;
    }

    /**
     * 校验分类
     *
     * @param category 分类
     */
    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        if (!PregnancyConstants.BAG_CATEGORY_MOTHER.equals(category)
                && !PregnancyConstants.BAG_CATEGORY_BABY.equals(category)) {
            throw PregnancyException.invalid("未知的物品分类：" + category);
        }
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
     * 解析可选 ID
     *
     * @param text 文本
     * @return ID
     */
    private Long parseOptionalId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw PregnancyException.invalid("ID 格式不正确：" + text);
        }
    }

    /**
     * 要求物品存在
     *
     * @param id 物品 ID
     * @return 物品
     */
    private PregBagItem requireItem(Long id) {
        PregBagItem item = bagMapper.selectItemById(id);
        if (item == null) {
            throw PregnancyException.notFound();
        }
        return item;
    }

    /**
     * 转换清单展示对象
     *
     * @param bag       清单
     * @param withItems 是否携带物品
     * @return 展示对象
     */
    private BagVO toBagVO(PregBag bag, boolean withItems) {
        BagVO vo = new BagVO();
        vo.setId(String.valueOf(bag.getId()));
        vo.setName(bag.getName());
        vo.setNote(bag.getNote());
        vo.setSortOrder(bag.getSortOrder());
        vo.setDefaultBag(PregnancyConstants.DEFAULT_BAG_SEED.equals(bag.getSeedKey()));
        if (withItems) {
            vo.setItems(bagMapper.selectItems(bag.getId(), null, null).stream()
                    .map(this::toItemVO).toList());
        }
        return vo;
    }

    /**
     * 转换物品展示对象
     *
     * @param item 物品
     * @return 展示对象
     */
    private BagItemVO toItemVO(PregBagItem item) {
        BagItemVO vo = new BagItemVO();
        vo.setId(String.valueOf(item.getId()));
        vo.setBagId(String.valueOf(item.getBagId()));
        vo.setCategory(item.getCategory());
        vo.setName(item.getName());
        vo.setQuantity(item.getQuantity());
        vo.setUnit(item.getUnit());
        vo.setPurpose(item.getPurpose());
        vo.setBrand(item.getBrand());
        vo.setChannel(item.getChannel());
        vo.setPurchaseDate(PregnancyTimeUtils.formatDate(item.getPurchaseDate()));
        vo.setIntroduction(seedProvider.bagIntroduction(item.getCategory(), item.getName()));
        vo.setImageUrl(seedProvider.bagImage(item.getCategory(), item.getName()));
        vo.setSortOrder(item.getSortOrder());
        vo.setPrepared(item.getPrepared());
        vo.setVersion(item.getVersion());
        vo.setAiText(item.getAiText());
        if (item.getAiImageKey() != null && !item.getAiImageKey().isBlank()) {
            vo.setAiImageUrl(com.howe.common.utils.ConfigUtils.getString(
                    com.howe.common.constant.ConfigConstants.STORAGE_R2_PUBLIC_URL)
                    + "/" + item.getAiImageKey());
        }
        vo.setContentTaskId(item.getCurrentContentTaskId() == null
                ? null : String.valueOf(item.getCurrentContentTaskId()));
        vo.setContentState(taskService.contentState(item.getCurrentContentTaskId()));
        vo.setExpense(expenseService.findBySlot(PregnancyConstants.EXPENSE_SOURCE_BAG_ITEM,
                item.getId(), PregnancyConstants.EXPENSE_SLOT_TOTAL));
        return vo;
    }
}
