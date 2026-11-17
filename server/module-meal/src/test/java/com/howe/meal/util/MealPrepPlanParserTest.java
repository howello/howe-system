package com.howe.meal.util;

import com.howe.meal.domain.vo.MealPrepPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 备菜清单结构化结果解析测试
 *
 * <p>覆盖确定性兜底规则：调味品过滤、同名合并、分组内主料在前、字段缺失丢弃。
 * AI 自身的语义效果不在单测范围内。</p>
 *
 * @author howe
 */
class MealPrepPlanParserTest {

    @Test
    @DisplayName("常见调味品被剔除，真实食材保留")
    void filtersCondiments() {
        Map<String, Object> root = groups(
                group("菜摊",
                        item("青椒", "3 个", "false"),
                        item("盐", "适量", "false"),
                        item("生抽", "1 汤匙", "false"),
                        item("食用油", "2 汤匙", "false")));

        MealPrepPlan plan = MealPrepPlanParser.parse(1L, root);

        assertNotNull(plan);
        assertEquals(1, plan.getGroups().size());
        assertEquals(List.of("青椒"), names(plan));
    }

    @Test
    @DisplayName("同分组同名食材合并数量并追加明细")
    void mergesSameItem() {
        Map<String, Object> first = item("辣椒", "3 个", "false");
        detail(first, "青椒", "3 个");
        Map<String, Object> second = item("辣椒", "2 个", "false");
        detail(second, "红椒", "2 个");

        MealPrepPlan plan = MealPrepPlanParser.parse(1L, groups(group("菜摊", first, second)));

        assertNotNull(plan);
        MealPrepPlan.Item merged = plan.getGroups().get(0).getItems().get(0);
        assertEquals("辣椒", merged.getName());
        assertEquals("3 个 + 2 个", merged.getAmount());
        assertEquals(2, merged.getDetails().size());
        assertEquals("青椒", merged.getDetails().get(0).getLabel());
        assertEquals("红椒", merged.getDetails().get(1).getLabel());
    }

    @Test
    @DisplayName("分组内主料排在配料之前，即使模型返回顺序相反")
    void sortsMainBeforeSide() {
        Map<String, Object> root = groups(group("菜摊",
                item("葱", "2 根", "false"),
                item("五花肉", "1 斤", "true")));

        MealPrepPlan plan = MealPrepPlanParser.parse(1L, root);

        assertNotNull(plan);
        List<MealPrepPlan.Item> items = plan.getGroups().get(0).getItems();
        assertEquals("五花肉", items.get(0).getName());
        assertEquals("葱", items.get(1).getName());
        assertTrue(items.get(0).isMain());
        assertFalse(items.get(1).isMain());
    }

    @Test
    @DisplayName("缺少名称或数量、或结构非法的条目被丢弃")
    void dropsInvalidItems() {
        Map<String, Object> noAmount = new LinkedHashMap<>();
        noAmount.put("name", "土豆");
        noAmount.put("main", "false");

        MealPrepPlan plan = MealPrepPlanParser.parse(1L,
                groups(group("菜摊", noAmount, item("萝卜", "1 根", "false"))));

        assertNotNull(plan);
        assertEquals(List.of("萝卜"), names(plan));
    }

    @Test
    @DisplayName("没有有效分组时返回 null，避免写入半成品清单")
    void returnsNullWhenEmpty() {
        assertNull(MealPrepPlanParser.parse(1L, null));
        assertNull(MealPrepPlanParser.parse(1L, Map.of("groups", List.of())));
        assertNull(MealPrepPlanParser.parse(1L, groups(group("菜摊", item("盐", "适量", "false")))));
    }

    @Test
    @DisplayName("布尔字段兼容字符串 true/false 与原生 boolean")
    void parsesBooleanVariants() {
        Map<String, Object> nativeTrue = item("牛腩", "1 斤", "false");
        nativeTrue.put("main", true);

        MealPrepPlan plan = MealPrepPlanParser.parse(1L,
                groups(group("肉摊", nativeTrue, item("姜", "1 块", "false"))));

        assertNotNull(plan);
        assertTrue(plan.getGroups().get(0).getItems().get(0).isMain());
    }

    @Test
    @DisplayName("同一采购区域的重复分组被合并，不出现两个肉摊")
    void mergesDuplicateMarketGroups() {
        MealPrepPlan plan = MealPrepPlanParser.parse(1L, groups(
                group("肉摊", item("五花肉", "1 斤", "true")),
                group("肉摊", item("排骨", "2 斤", "true"))));

        assertNotNull(plan);
        assertEquals(1, plan.getGroups().size());
        assertEquals(2, plan.getGroups().get(0).getItems().size());
    }

    @Test
    @DisplayName("采购区域按固定动线排序，未知区域排在末尾")
    void ordersMarketsByWalkway() {
        MealPrepPlan plan = MealPrepPlanParser.parse(1L, groups(
                group("菜摊", item("白菜", "1 颗", "false")),
                group("肉摊", item("五花肉", "1 斤", "true")),
                group("水产摊", item("鲈鱼", "1 条", "true")),
                group("街边小摊", item("手工面", "1 把", "false"))));

        assertNotNull(plan);
        assertEquals(List.of("肉摊", "水产摊", "菜摊", "街边小摊"), markets(plan));
    }

    @Test
    @DisplayName("采购区域同义写法归一到标准名称并合并")
    void normalizesMarketAliases() {
        MealPrepPlan plan = MealPrepPlanParser.parse(1L, groups(
                group("肉铺", item("五花肉", "1 斤", "true")),
                group("猪肉档", item("排骨", "2 斤", "true"))));

        assertNotNull(plan);
        assertEquals(List.of("肉摊"), markets(plan));
        assertEquals(2, plan.getGroups().get(0).getItems().size());
    }

    @Test
    @DisplayName("克、千克、kg 的数量会被换算成斤/两")
    void convertsWeightUnits() {
        assertEquals("1 斤 2 两", MealPrepPlanParser.toHouseholdAmount("600 克"));
        assertEquals("2 斤", MealPrepPlanParser.toHouseholdAmount("1 千克"));
        assertEquals("1 斤", MealPrepPlanParser.toHouseholdAmount("500g"));
        assertEquals("2 两", MealPrepPlanParser.toHouseholdAmount("100 克"));
        assertEquals("半两", MealPrepPlanParser.toHouseholdAmount("30 克"));
        assertEquals("3 个", MealPrepPlanParser.toHouseholdAmount("3 个"));
    }

    @Test
    @DisplayName("清单条目与明细中的重量单位都会被换算，不再出现克与 kg")
    void convertsUnitsInPlan() {
        Map<String, Object> pork = item("五花肉", "600 克", "true");
        detail(pork, "五花肉", "500g");

        MealPrepPlan plan = MealPrepPlanParser.parse(1L, groups(group("肉摊", pork)));

        assertNotNull(plan);
        MealPrepPlan.Item parsed = plan.getGroups().get(0).getItems().get(0);
        assertEquals("1 斤 2 两", parsed.getAmount());
        assertEquals("1 斤", parsed.getDetails().get(0).getAmount());
        assertFalse(MealPrepPlanParser.hasWeightUnit(parsed.getAmount()));
        assertFalse(MealPrepPlanParser.hasWeightUnit(parsed.getDetails().get(0).getAmount()));
    }

    @Test
    @DisplayName("数量中的重量单位可被识别")
    void detectsWeightUnits() {
        assertTrue(MealPrepPlanParser.hasWeightUnit("600 克"));
        assertTrue(MealPrepPlanParser.hasWeightUnit("1 kg"));
        assertFalse(MealPrepPlanParser.hasWeightUnit("2 斤"));
        assertFalse(MealPrepPlanParser.hasWeightUnit("3 个"));
    }

    @Test
    @DisplayName("中文数字与大小写单位同样被换算")
    void convertsChineseAndMixedCaseUnits() {
        assertEquals("2 斤", MealPrepPlanParser.toHouseholdAmount("一千克"));
        assertEquals("1 斤", MealPrepPlanParser.toHouseholdAmount("半公斤"));
        assertEquals("2 斤", MealPrepPlanParser.toHouseholdAmount("1 kG"));
        assertEquals("1 斤", MealPrepPlanParser.toHouseholdAmount("五百克"));
        assertEquals("3 两", MealPrepPlanParser.toHouseholdAmount("一百五十克"));
        assertEquals("1 斤 2 两", MealPrepPlanParser.toHouseholdAmount("600g"));
    }

    @Test
    @DisplayName("换算后仍含重量单位的条目被丢弃，不带到页面")
    void dropsUnconvertibleAmounts() {
        // 「若干克」无法解析数字，换算后仍含「克」，应整条丢弃而不是原样展示
        Map<String, Object> bad = item("神秘食材", "若干克", "false");
        MealPrepPlan plan = MealPrepPlanParser.parse(1L,
                groups(group("菜摊", bad, item("白菜", "1 颗", "false"))));

        assertNotNull(plan);
        assertEquals(List.of("白菜"), names(plan));
    }

    @Test
    @DisplayName("「其他蔬菜」按蔬菜归类，不会被「其他」吞掉")
    void doesNotSwallowOtherQualifiedMarket() {
        MealPrepPlan plan = MealPrepPlanParser.parse(1L, groups(
                group("其他", item("手工面", "1 把", "false")),
                group("其他蔬菜", item("香菜", "1 把", "false"))));

        assertNotNull(plan);
        // 「其他蔬菜」含「蔬菜」，应归到菜摊；「其他」保持独立分组
        assertEquals(List.of("菜摊", "其他"), markets(plan));
        assertEquals(List.of("香菜"), plan.getGroups().get(0).getItems().stream()
                .map(MealPrepPlan.Item::getName).toList());
    }

    @Test
    @DisplayName("重复市场分组合并后不丢失条目（上限放宽到 400）")
    void keepsAllItemsAcrossDuplicateMarketGroups() {
        Object[] first = new Object[100];
        Object[] second = new Object[100];
        for (int i = 0; i < 100; i++) {
            first[i] = item("菜一" + i, "1 个", "false");
            second[i] = item("菜二" + i, "1 个", "false");
        }
        MealPrepPlan plan = MealPrepPlanParser.parse(1L,
                groups(group("菜摊", first), group("菜摊", second)));

        assertNotNull(plan);
        assertEquals(1, plan.getGroups().size());
        assertEquals(200, plan.getGroups().get(0).getItems().size());
    }

    @Test
    @DisplayName("超过防御上限的条目不丢弃，归入「其他食材」明细")
    void keepsOverflowItemsAsDetails() {
        int total = MealPrepPlanParser.MAX_ITEMS_PER_GROUP + 5;
        Object[] lots = new Object[total];
        for (int i = 0; i < total; i++) {
            lots[i] = item("食材" + i, "1 个", "false");
        }
        MealPrepPlan plan = MealPrepPlanParser.parse(1L, groups(group("菜摊", lots)));

        assertNotNull(plan);
        MealPrepPlan.Group group = plan.getGroups().get(0);
        MealPrepPlan.Item overflow = group.getItems().stream()
                .filter(i -> "其他食材".equals(i.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(5, overflow.getDetails().size());
        assertEquals("食材" + MealPrepPlanParser.MAX_ITEMS_PER_GROUP,
                overflow.getDetails().get(0).getLabel());
    }

    /** 构造根节点 */
    private static Map<String, Object> groups(Object... groups) {
        return Map.of("groups", List.of(groups));
    }

    /** 构造分组 */
    private static Map<String, Object> group(String market, Object... items) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("market", market);
        group.put("items", List.of(items));
        return group;
    }

    /** 构造条目 */
    private static Map<String, Object> item(String name, String amount, String main) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("amount", amount);
        item.put("main", main);
        item.put("details", new java.util.ArrayList<>());
        return item;
    }

    /** 追加明细 */
    @SuppressWarnings("unchecked")
    private static void detail(Map<String, Object> item, String label, String amount) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("label", label);
        detail.put("amount", amount);
        ((List<Object>) item.get("details")).add(detail);
    }

    /** 取全部条目名称 */
    private static List<String> names(MealPrepPlan plan) {
        return plan.getGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .map(MealPrepPlan.Item::getName)
                .toList();
    }

    /** 取分组顺序 */
    private static List<String> markets(MealPrepPlan plan) {
        return plan.getGroups().stream().map(MealPrepPlan.Group::getMarket).toList();
    }
}
