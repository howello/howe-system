package com.howe.meal.util;

import com.howe.meal.domain.vo.MealPrepPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 备菜清单结构化结果解析
 *
 * <p>把 AI 返回的 JSON 映射转换为 {@link MealPrepPlan}，并在这里做确定性兜底：
 * 调味品过滤、同分组同名食材合并、组内主料在前的排序、非法结构丢弃。
 * 这些规则不依赖模型是否严格遵循提示词，因此单独放在工具类里便于单测。</p>
 *
 * @author howe
 */
public final class MealPrepPlanParser {

    /**
     * 清单最大分组数，防止异常输出撑爆页面。
     *
     * <p>采购区域只有固定的几个标准分类，上限远大于实际数量，不会造成业务条目丢失。</p>
     */
    public static final int MAX_GROUPS = 20;

    /**
     * 防御性条目上限
     *
     * <p>只用于拦住异常到不可能来自真实订单的输出（例如模型返回上万条重复项），
     * 不是业务截断线：一单 100 道菜、每道菜用料 JSON 不设长度上限时，
     * 合并后条目数仍可能远大于此。超限时改为合并进已有分组而不是静默丢弃，
     * 详见 {@link #parseGroup(Object)} 与 {@link #mergeGroup(MealPrepPlan.Group, MealPrepPlan.Group)}。</p>
     */
    public static final int MAX_ITEMS_PER_GROUP = 2000;

    /** 单条名称与数量长度上限。 */
    public static final int MAX_TEXT_LENGTH = 60;

    /**
     * 常见调味品名称集合
     *
     * <p>提示词已要求模型剔除，这里再做一次确定性过滤：即使模型漏删，也不会进入采购清单。
     * 只做精确匹配，避免把「干辣椒」「辣椒」这类真实食材误删；「干辣椒段」这类调味形态才剔除。</p>
     */
    public static final Set<String> CONDIMENTS = Set.of(
            "生抽", "老抽", "酱油", "蒸鱼豉油", "食用油", "菜籽油", "花生油", "香油", "芝麻油",
            "料酒", "黄酒", "白糖", "冰糖", "红糖", "盐", "食盐", "鸡精", "味精", "胡椒粉", "花椒粉",
            "淀粉", "生粉", "香醋", "陈醋", "米醋", "白醋", "蚝油", "大料", "八角", "香叶", "桂皮",
            "花椒", "干辣椒段", "辣椒段", "豆瓣酱", "甜面酱", "黄豆酱", "番茄酱", "辣椒油", "孜然",
            "五香粉", "十三香", "老干妈", "豆豉", "腐乳", "芝麻");

    private MealPrepPlanParser() {
    }

    /**
     * 采购区域的标准名称与展示顺序
     *
     * <p>模型可能返回「肉铺」「蔬菜摊」「海鲜」等同义写法，也可能把同一采购区域拆成多个分组。
     * 这里按标准名称归一、按固定动线顺序输出，保证同一采购区域的条目相邻。
     * 未命中的分组保留原始名称并排在末尾，避免丢数据。</p>
     */
    public static final List<String> MARKET_ORDER = List.of(
            "肉摊", "水产摊", "菜摊", "豆制品摊", "蛋类", "干货粮油", "其他");

    /**
     * 采购区域同义词映射：原始写法 → 标准名称
     *
     * <p>按前缀/包含关系匹配（见 {@link #normalizeMarket(String)}），越具体的规则要放在越前面。</p>
     */
    private static final Map<String, String> MARKET_ALIASES = buildMarketAliases();

    /**
     * 数量中的重量/容量单位，出现即视为不合格，需要换算为生活化单位
     *
     * <p>同时覆盖阿拉伯数字与中文数字写法（「600 克」「一千克」「半公斤」「1 kG」「500ml」）。
     * 单位交替顺序按长度降序排列，避免「毫升」先被「升」匹配、中文数字先被短词截断。</p>
     */
    private static final java.util.regex.Pattern WEIGHT_UNIT = java.util.regex.Pattern.compile(
            "(\\d+(?:\\.\\d+)?|[一二三四五六七八九十百两半]+)\\s*"
                    + "(千克|公斤|毫升|kg|ml|克|g|升|l)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * 解析 AI 结构化输出为清单
     *
     * @param orderId    订单 ID
     * @param structured AI 结构化结果
     * @return 清单；结构非法或过滤后为空时返回 null
     */
    public static MealPrepPlan parse(Long orderId, Object structured) {
        if (!(structured instanceof Map<?, ?> root)) {
            return null;
        }
        Object rawGroups = root.get("groups");
        if (!(rawGroups instanceof List<?> groupList) || groupList.isEmpty()) {
            return null;
        }
        MealPrepPlan plan = new MealPrepPlan();
        plan.setOrderId(orderId);
        plan.setStatus(MealPrepPlan.STATUS_READY);
        plan.setGeneratedAt(System.currentTimeMillis());
        // 先按采购区域归并：同名或同义的市场写进同一个分组，再按固定动线排序
        Map<String, MealPrepPlan.Group> byMarket = new LinkedHashMap<>();
        for (Object rawGroup : groupList) {
            MealPrepPlan.Group group = parseGroup(rawGroup);
            if (group == null) {
                continue;
            }
            MealPrepPlan.Group exists = byMarket.get(group.getMarket());
            if (exists == null) {
                byMarket.put(group.getMarket(), group);
                continue;
            }
            // 同一采购区域的重复分组：把条目并进已有分组，同名再合并
            mergeGroup(exists, group);
        }
        if (byMarket.isEmpty()) {
            return null;
        }
        plan.setGroups(orderGroups(byMarket));
        return plan;
    }

    /**
     * 把后出现的同市场分组并入已有分组
     *
     * <p>同名条目合并数量与明细；新名称条目一律追加，不做数量截断——
     * 上游没有能证明条目数有上界的约束（一单 100 道菜、每道菜用料 JSON 不限长），
     * 截断会静默丢掉合法食材。仅当总量超过防御性上限
     * {@link #MAX_ITEMS_PER_GROUP} 时，才把剩余条目并入同名项或「其他食材」条目的明细，
     * 仍然不丢弃信息。</p>
     *
     * @param target 已有分组
     * @param extra  后出现的同市场分组
     */
    private static void mergeGroup(MealPrepPlan.Group target, MealPrepPlan.Group extra) {
        for (MealPrepPlan.Item item : extra.getItems()) {
            MealPrepPlan.Item sameName = findByName(target.getItems(), item.getName());
            if (sameName != null) {
                mergeItem(sameName, item);
                continue;
            }
            if (target.getItems().size() < MAX_ITEMS_PER_GROUP) {
                target.getItems().add(item);
                continue;
            }
            // 超防御上限：并入「其他食材」条目的明细，保证食材名与数量仍然可见
            appendOverflow(target, item);
        }
        // 合并后重新按主料在前排序
        sortGroup(target);
    }

    /**
     * 超出防御上限时把条目并入「其他食材」条目的明细
     *
     * @param group 目标分组
     * @param item  待并入条目
     */
    private static void appendOverflow(MealPrepPlan.Group group, MealPrepPlan.Item item) {
        MealPrepPlan.Item overflow = findByName(group.getItems(), "其他食材");
        if (overflow == null) {
            overflow = new MealPrepPlan.Item();
            overflow.setName("其他食材");
            overflow.setAmount("详见明细");
            overflow.setMain(false);
            group.getItems().add(overflow);
        }
        MealPrepPlan.Detail detail = new MealPrepPlan.Detail();
        detail.setLabel(item.getName());
        detail.setAmount(item.getAmount());
        overflow.getDetails().add(detail);
    }

    /**
     * 判断名称是否为常见调味品
     *
     * @param name 食材名
     * @return 是调味品返回 true
     */
    public static boolean isCondiment(String name) {
        return name != null && CONDIMENTS.contains(name.trim());
    }

    /**
     * 是否仍含重量/容量单位
     *
     * <p>生活化单位（斤、两、根、个、条等）通过。判定分两层：</p>
     * <ol>
     *   <li>带数字单位的文本（「600 克」「一千克」「1 kG」）由 {@link #WEIGHT_UNIT} 命中；</li>
     *   <li>无数字的「若干克」「适量毫升」这类文本按结尾单位判断，
     *       避免正则要求数字时漏判。单位只在末尾匹配，避免「巧克力」这类词误伤。</li>
     * </ol>
     *
     * @param amount 数量文本
     * @return 含非法单位返回 true
     */
    public static boolean hasWeightUnit(String amount) {
        if (amount == null || amount.isEmpty()) {
            return false;
        }
        if (WEIGHT_UNIT.matcher(amount).find()) {
            return true;
        }
        String lower = amount.toLowerCase();
        for (String unit : WEIGHT_UNIT_SUFFIXES) {
            if (lower.endsWith(unit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 无数字时按结尾识别的重量单位（小写，长词在前）
     */
    private static final List<String> WEIGHT_UNIT_SUFFIXES = List.of(
            "千克", "公斤", "毫升", "kg", "ml", "克", "g", "升", "l");

    /**
     * 归一采购区域名称
     *
     * <p>匹配优先级：标准名精确 → 同义词精确 → 标准名包含 → 同义词包含。
     * 逐级匹配避免「其他蔬菜」这类带标准名后缀的表述被「其他」抢先吞掉：
     * 「其他」只参与前两级精确匹配，只有整串就是「其他」时才归到其他组。</p>
     *
     * @param raw 模型返回的市场名
     * @return 标准名称；无法识别时返回清理后的原名
     */
    public static String normalizeMarket(String raw) {
        String market = raw == null ? "" : raw.trim();
        if (market.isEmpty()) {
            return "";
        }
        // 1. 标准名精确命中
        for (String standard : MARKET_ORDER) {
            if (market.equals(standard)) {
                return standard;
            }
        }
        // 2. 同义词精确命中（「其他」不在别名表里，天然只走精确标准名）
        String alias = MARKET_ALIASES.get(market);
        if (alias != null) {
            return alias;
        }
        // 3. 标准名包含：跳过「其他」，避免「其他蔬菜」被归到其他组
        for (String standard : MARKET_ORDER) {
            if ("其他".equals(standard)) {
                continue;
            }
            if (market.contains(standard)) {
                return standard;
            }
        }
        // 4. 同义词包含：按别名表顺序，长词在前避免被短词提前命中
        for (Map.Entry<String, String> entry : MARKET_ALIASES.entrySet()) {
            if (market.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return market;
    }

    /**
     * 构造采购区域同义词表
     *
     * @return 原始关键词 → 标准名称
     */
    private static Map<String, String> buildMarketAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        // 肉摊
        for (String word : List.of("肉铺", "肉店", "肉档", "猪肉", "牛羊肉", "生肉", "肉食")) {
            aliases.put(word, "肉摊");
        }
        // 水产摊
        for (String word : List.of("水产", "海鲜", "鱼摊", "鱼档", "河鲜", "活鲜")) {
            aliases.put(word, "水产摊");
        }
        // 菜摊
        for (String word : List.of("蔬菜", "菜市场", "菜档", "青菜", "果蔬", "蔬菜水果")) {
            aliases.put(word, "菜摊");
        }
        // 豆制品摊
        for (String word : List.of("豆制品", "豆腐摊", "豆腐店", "豆制品摊")) {
            aliases.put(word, "豆制品摊");
        }
        // 蛋类
        for (String word : List.of("鸡蛋", "蛋品", "禽蛋")) {
            aliases.put(word, "蛋类");
        }
        // 干货粮油
        for (String word : List.of("干货", "粮油", "米面", "主食", "调料", "杂粮", "干杂")) {
            aliases.put(word, "干货粮油");
        }
        return aliases;
    }

    /**
     * 按固定动线顺序输出分组：已知采购区域按 {@link #MARKET_ORDER}，未知的排在末尾并保持原顺序
     *
     * @param byMarket 归并后的分组
     * @return 排好序的分组列表
     */
    private static List<MealPrepPlan.Group> orderGroups(Map<String, MealPrepPlan.Group> byMarket) {
        List<MealPrepPlan.Group> ordered = new ArrayList<>(byMarket.size());
        for (String market : MARKET_ORDER) {
            MealPrepPlan.Group group = byMarket.get(market);
            if (group != null) {
                ordered.add(group);
            }
        }
        for (Map.Entry<String, MealPrepPlan.Group> entry : byMarket.entrySet()) {
            if (!MARKET_ORDER.contains(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return ordered;
    }

    /**
     * 在已有条目中按名称查找
     *
     * @param items 条目列表
     * @param name  名称
     * @return 命中的条目或 null
     */
    private static MealPrepPlan.Item findByName(List<MealPrepPlan.Item> items, String name) {
        for (MealPrepPlan.Item item : items) {
            if (item.getName().equals(name)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 分组内重新排序：主料在前、配料在后
     *
     * @param group 待排序分组
     */
    private static void sortGroup(MealPrepPlan.Group group) {
        List<MealPrepPlan.Item> main = new ArrayList<>();
        List<MealPrepPlan.Item> side = new ArrayList<>();
        for (MealPrepPlan.Item item : group.getItems()) {
            (item.isMain() ? main : side).add(item);
        }
        group.getItems().clear();
        group.getItems().addAll(main);
        group.getItems().addAll(side);
    }

    /**
     * 解析单个采购区域分组
     *
     * @param rawGroup AI 返回的分组
     * @return 分组或 null
     */
    private static MealPrepPlan.Group parseGroup(Object rawGroup) {
        if (!(rawGroup instanceof Map<?, ?> groupMap)) {
            return null;
        }
        String market = normalizeMarket(trimTo(groupMap.get("market")));
        Object rawItems = groupMap.get("items");
        if (market.isEmpty() || !(rawItems instanceof List<?> itemList)) {
            return null;
        }
        Map<String, MealPrepPlan.Item> merged = new LinkedHashMap<>();
        List<MealPrepPlan.Item> overflow = new ArrayList<>();
        for (Object rawItem : itemList) {
            MealPrepPlan.Item item = parseItem(rawItem);
            if (item == null) {
                continue;
            }
            // 同一分组内同名食材再次出现时合并数量，不重复列两行
            MealPrepPlan.Item exists = merged.get(item.getName());
            if (exists != null) {
                mergeItem(exists, item);
                continue;
            }
            if (merged.size() >= MAX_ITEMS_PER_GROUP) {
                // 超防御上限：暂存后并入「其他食材」明细，不静默丢弃
                overflow.add(item);
                continue;
            }
            merged.put(item.getName(), item);
        }
        if (merged.isEmpty() && overflow.isEmpty()) {
            return null;
        }
        MealPrepPlan.Group group = new MealPrepPlan.Group();
        group.setMarket(market);
        // 组内主料在前、配料在后：即使模型返回顺序打乱，也由后端再排一次
        for (MealPrepPlan.Item item : merged.values()) {
            if (item.isMain()) {
                group.getItems().add(item);
            }
        }
        for (MealPrepPlan.Item item : merged.values()) {
            if (!item.isMain()) {
                group.getItems().add(item);
            }
        }
        // 超防御上限的剩余条目并入「其他食材」明细，食材名与数量仍然可见
        for (MealPrepPlan.Item item : overflow) {
            appendOverflow(group, item);
        }
        return group;
    }

    /**
     * 转换单条食材；名称或数量为空、属于常见调味品、或换算后仍含重量单位时丢弃
     *
     * <p>最后一次 {@link #hasWeightUnit(String)} 校验是硬约束：宁可少列一条，
     * 也不把「600 克」这类禁止单位带到采购清单页面。</p>
     *
     * @param rawItem AI 返回的条目
     * @return 食材条目或 null
     */
    private static MealPrepPlan.Item parseItem(Object rawItem) {
        if (!(rawItem instanceof Map<?, ?> itemMap)) {
            return null;
        }
        String name = trimTo(itemMap.get("name"));
        String amount = toHouseholdAmount(trimTo(itemMap.get("amount")));
        if (name.isEmpty() || amount.isEmpty() || isCondiment(name) || hasWeightUnit(amount)) {
            return null;
        }
        MealPrepPlan.Item item = new MealPrepPlan.Item();
        item.setName(name);
        item.setAmount(amount);
        item.setMain(parseBoolean(itemMap.get("main")));
        Object rawDetails = itemMap.get("details");
        if (rawDetails instanceof List<?> detailList) {
            for (Object rawDetail : detailList) {
                if (!(rawDetail instanceof Map<?, ?> detailMap)) {
                    continue;
                }
                String label = trimTo(detailMap.get("label"));
                if (label.isEmpty()) {
                    continue;
                }
                String detailAmount = toHouseholdAmount(trimTo(detailMap.get("amount")));
                // 明细数量同样不允许残留重量单位，无法换算时留空而不是展示非法单位
                MealPrepPlan.Detail detail = new MealPrepPlan.Detail();
                detail.setLabel(label);
                detail.setAmount(hasWeightUnit(detailAmount) ? "" : detailAmount);
                item.getDetails().add(detail);
            }
        }
        return item;
    }

    /**
     * 把重量/容量单位换算成菜场生活化单位
     *
     * <p>模型偶尔仍会返回「600 克」「1 kg」「500ml」「一千克」「半公斤」。这里做确定性换算：
     * 500 克 ≈ 1 斤，50 克 ≈ 1 两；不足 1 两的保留「半两」；液体按 500ml ≈ 1 斤处理。
     * 换算后仍残留重量单位的文本会被 {@link #hasWeightUnit(String)} 识别，由调用方兜底。</p>
     *
     * @param amount 原始数量
     * @return 换算后的数量
     */
    public static String toHouseholdAmount(String amount) {
        if (amount == null || amount.isEmpty()) {
            return "";
        }
        java.util.regex.Matcher matcher = WEIGHT_UNIT.matcher(amount);
        if (!matcher.find()) {
            return amount;
        }
        // 逐个替换，保证「肉 600 克」「鸡蛋 500g」这类混合文本也能处理
        StringBuilder result = new StringBuilder();
        matcher.reset();
        int last = 0;
        while (matcher.find()) {
            result.append(amount, last, matcher.start());
            result.append(gramsToHousehold(weightInGrams(matcher.group())));
            last = matcher.end();
        }
        result.append(amount.substring(last));
        return result.toString().trim();
    }

    /**
     * 把带单位的数值折算为克（毫升按 1:1）
     *
     * <p>同时支持阿拉伯数字与中文数字写法，例如「600 克」「1.2kg」「一千克」「半公斤」「500ml」。
     * 中文数字只覆盖家常用量常见的「半、一、两、三……十、百」组合，够用且不会误解析普通文本。</p>
     *
     * @param text 带单位的数量文本
     * @return 折算后的克数；无法解析返回 -1
     */
    private static double weightInGrams(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?|[一二三四五六七八九十百两半]+)\\s*(千克|公斤|毫升|kg|ml|克|g|升|l)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (!matcher.find()) {
            return -1;
        }
        double value = parseQuantity(matcher.group(1));
        if (value <= 0) {
            return -1;
        }
        String unit = matcher.group(2).toLowerCase();
        return switch (unit) {
            case "千克", "公斤", "kg", "升", "l" -> value * 1000D;
            case "毫升", "ml" -> value;
            default -> value;
        };
    }

    /**
     * 解析数量：阿拉伯数字直接转，中文数字按十/百进位解析
     *
     * @param raw 阿拉伯数字或中文数字
     * @return 数值；无法解析返回 -1
     */
    private static double parseQuantity(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }
        if (raw.matches("\\d+(?:\\.\\d+)?")) {
            return Double.parseDouble(raw);
        }
        double total = 0;
        double current = 0;
        for (char c : raw.toCharArray()) {
            int digit = chineseDigit(c);
            if (digit >= 0) {
                current = digit;
            } else if (c == '十') {
                total += (current == 0 ? 1 : current) * 10D;
                current = 0;
            } else if (c == '百') {
                total += (current == 0 ? 1 : current) * 100D;
                current = 0;
            } else if (c == '半') {
                total += 0.5D;
            } else {
                return -1;
            }
        }
        total += current;
        return total > 0 ? total : -1;
    }

    /**
     * 中文数字单字转数值
     *
     * @param c 单字
     * @return 0-9；非数字返回 -1
     */
    private static int chineseDigit(char c) {
        return switch (c) {
            case '零' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    /**
     * 克数换算为斤/两
     *
     * @param grams 克数（毫升按 1:1 折算）
     * @return 生活化数量，如「1 斤 2 两」「半两」
     */
    private static String gramsToHousehold(double grams) {
        if (grams <= 0) {
            return "适量";
        }
        double jin = grams / 500D;
        if (jin >= 1D) {
            double wholeJin = Math.floor(jin);
            double restLiang = Math.round((jin - wholeJin) * 10D);
            if (restLiang >= 10D) {
                wholeJin += 1D;
                restLiang = 0D;
            }
            return restLiang > 0
                    ? formatNumber(wholeJin) + " 斤 " + formatNumber(restLiang) + " 两"
                    : formatNumber(wholeJin) + " 斤";
        }
        double liang = grams / 50D;
        if (liang >= 1D) {
            return formatNumber(Math.round(liang * 10D) / 10D) + " 两";
        }
        return grams < 25D ? "少量" : "半两";
    }

    /**
     * 去掉多余的小数尾巴（1.0 → 1，1.20 → 1.2）
     *
     * @param value 数值
     * @return 文本
     */
    private static String formatNumber(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 10D) / 10D);
    }

    /**
     * 合并同一分组内的重复食材：数量拼接、明细追加、主料标记取并集
     *
     * @param target 已存在的条目
     * @param extra  新出现的同名条目
     */
    private static void mergeItem(MealPrepPlan.Item target, MealPrepPlan.Item extra) {
        String amount = extra.getAmount();
        if (amount != null && !amount.isEmpty() && !target.getAmount().contains(amount)) {
            target.setAmount(target.getAmount() + " + " + amount);
        }
        target.setMain(target.isMain() || extra.isMain());
        target.getDetails().addAll(extra.getDetails());
    }

    /**
     * 解析布尔字段：兼容 boolean 与字符串 true/false
     *
     * @param raw 原始值
     * @return 布尔值
     */
    private static boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        return raw != null && "true".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    /**
     * 截断并清理文本
     *
     * @param raw 原始值
     * @return 清理后的文本，可能为空串
     */
    private static String trimTo(Object raw) {
        if (raw == null) {
            return "";
        }
        String text = String.valueOf(raw).trim();
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }
}
