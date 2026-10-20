package com.howe.meal.constant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家庭点餐唯一主类体系
 *
 * <p>分类是「唯一主类」而不是标签：9 个主类名称固定，一道菜最多归一个。
 * 本类集中保存主类定义（名称、说明、依据、图标、排序）、判定顺序、旧分类名映射与
 * 供 AI 场景复用的固定提示词片段，避免把同一套规则散落在 Service、SQL 与前端。</p>
 *
 * <p>「招牌」不是主类：它只做点餐端的推荐位，不落库、不参与判定，因此不在本类清单中。</p>
 *
 * @author howe
 */
public final class MealCategoryTaxonomy {

    /** 主类：岷县 */
    public static final String MINXIAN = "岷县";
    /** 主类：荤菜 */
    public static final String MEAT = "荤菜";
    /** 主类：半荤 */
    public static final String HALF_MEAT = "半荤";
    /** 主类：素菜 */
    public static final String VEGETABLE = "素菜";
    /** 主类：水产 */
    public static final String AQUATIC = "水产";
    /** 主类：蛋豆 */
    public static final String EGG_BEAN = "蛋豆";
    /** 主类：凉卤 */
    public static final String COLD = "凉卤";
    /** 主类：汤羹 */
    public static final String SOUP = "汤羹";
    /** 主类：面点（面食 + 主食） */
    public static final String STAPLE = "面点";

    /** 平台公共分类的归属部门 ID */
    public static final long PUBLIC_DEPT_ID = 0L;

    /** 一个人工或 AI 试图使用的非法分类名：招牌只是推荐位 */
    public static final String SIGNATURE = "招牌";

    /** 固定主类定义，按展示顺序排列 */
    private static final List<Definition> DEFINITIONS = buildDefinitions();

    /** 旧分类名 → 新主类名的映射，供迁移脚本与兼容逻辑使用 */
    private static final Map<String, String> LEGACY_NAME_MAPPING = buildLegacyMapping();

    private MealCategoryTaxonomy() {
    }

    /**
     * 取全部主类定义（不可变，按排序升序）
     *
     * @return 主类定义列表
     */
    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    /**
     * 取全部主类名称（按排序升序）
     *
     * @return 主类名称列表
     */
    public static List<String> names() {
        List<String> names = new ArrayList<>(DEFINITIONS.size());
        for (Definition definition : DEFINITIONS) {
            names.add(definition.name());
        }
        return names;
    }

    /**
     * 判断名称是否为合法的 9 个主类之一
     *
     * @param name 分类名称
     * @return 是否为合法主类
     */
    public static boolean isPrimaryCategory(String name) {
        return name != null && LEGACY_NAME_MAPPING.containsValue(name);
    }

    /**
     * 判断名称是否为「招牌」这类不允许作为分类写入的名称
     *
     * @param name 分类名称
     * @return 是否为招牌
     */
    public static boolean isSignature(String name) {
        return name != null && SIGNATURE.equals(name.trim());
    }

    /**
     * 把旧分类名映射为新主类名
     *
     * @param legacyName 旧分类名
     * @return 对应的新主类名；无法映射返回 null
     */
    public static String mapLegacyName(String legacyName) {
        if (legacyName == null) {
            return null;
        }
        return LEGACY_NAME_MAPPING.get(legacyName.trim());
    }

    /**
     * 生成追加到 AI system 提示词之后的固定分类规则
     *
     * <p>该片段由代码常量生成，不受 {@code sys_config} 覆盖，保证分类口径稳定。
     * 三个 AI 场景（生成、审核、更新）都复用同一份文本。</p>
     *
     * @return 固定分类规则文本
     */
    public static String promptFragment() {
        StringBuilder builder = new StringBuilder();
        builder.append("【分类规则】菜品只有一个主类，必须从下列 9 个名称中选择一个：")
                .append(String.join("、", names())).append("。\n");
        for (Definition definition : DEFINITIONS) {
            builder.append("- ").append(definition.name()).append("：")
                    .append(definition.description()).append("；").append(definition.basis()).append("\n");
        }
        builder.append("判定顺序（命中即停）：")
                .append("1. 招牌不是分类，不参与判定；")
                .append("2. 岷县地方特色 → 岷县；")
                .append("3. 饺子/包子/饼/面条/馒头/花卷等面食，或米饭/炒饭/粥/煲仔饭等主食 → 面点")
                .append("（例外：酸汤水饺、汤面等以面食/主食为主体的带汤菜也归面点，汤只是做法）；")
                .append("4. 汤/羹/煲汤 → 汤羹；")
                .append("5. 凉拌/卤味冷盘 → 凉卤；")
                .append("6. 主料是鱼虾蟹贝蛙 → 水产；")
                .append("7. 主料是蛋/豆腐/豆制品 → 蛋豆；")
                .append("8. 肉为主 → 荤菜，肉少菜多 → 半荤；")
                .append("9. 完全无肉、以蔬菜菌菇为主 → 素菜。\n");
        builder.append("只能返回上述 9 个名称之一，不得自造分类，不得返回「招牌」。");
        return builder.toString();
    }

    /**
     * 构造主类定义
     *
     * @return 主类定义列表
     */
    private static List<Definition> buildDefinitions() {
        List<Definition> list = new ArrayList<>(9);
        list.add(new Definition(MINXIAN, "🏔️", 1, "岷县地方特色菜",
                "只要顾客认「岷县」就优先归这里；普通家常菜不要硬归岷县。示例：岷县浆水面、岷县凉粉、岷县点心、当归炖鸡、洋芋拌汤、面片"));
        list.add(new Definition(MEAT, "🍖", 2, "以肉为主、肉量明显的菜",
                "肉少菜多的不放；鱼虾蟹贝不放；汤羹不放。示例：红烧肉、回锅肉、辣子鸡、小炒黄牛肉、糖醋排骨、火锅"));
        list.add(new Definition(HALF_MEAT, "🥘", 3, "肉少菜多、肉丝肉片配大量蔬菜、肉末配菜",
                "肉为主的不放；纯素不放。示例：鱼香肉丝、肉末豆角、蚂蚁上树、芹菜肉丝、蒜苔肉丝、青椒肉丝"));
        list.add(new Definition(VEGETABLE, "🥬", 4, "纯素菜、时蔬、菌菇、素炒",
                "含肉的不放；豆腐/蛋为主的不放。示例：清炒时蔬、手撕包菜、蒜蓉西兰花、干锅花菜、菌菇小炒"));
        list.add(new Definition(AQUATIC, "🐟", 5, "鱼、虾、蟹、贝、蛙等水产为主料",
                "鱼香肉丝不放；水产汤归汤羹；凉拌水产归凉卤。示例：水煮鱼、酸菜鱼、红烧鱼、辣炒虾、干锅牛蛙、辣炒花甲"));
        list.add(new Definition(EGG_BEAN, "🥚", 6, "蛋类菜、豆腐、豆制品为主",
                "只有少量豆腐/蛋的荤菜不放；汤里的豆腐蛋不归这里。示例：麻婆豆腐、家常豆腐、番茄炒蛋、蒸蛋、香干炒肉丝"));
        list.add(new Definition(COLD, "🥗", 7, "凉拌菜、卤味冷盘、冷菜",
                "热炒、热炖、热汤不放。示例：拍黄瓜、口水鸡、夫妻肺片、卤味拼盘、凉拌木耳"));
        list.add(new Definition(SOUP, "🥣", 8, "汤、羹、煲汤、带汤炖品",
                "干锅、炒菜、面点不放。示例：番茄蛋汤、酸辣汤、当归鸡汤、排骨汤、豆腐羹"));
        list.add(new Definition(STAPLE, "🍜", 9, "饺子、包子、饼、面条、馒头、花卷等面食，以及米饭、炒饭、粥、煲仔饭等主食",
                "含肉面点也归面点，不归荤菜。示例：水饺、煎饺、葱油饼、牛肉面、包子、花卷、黄金炒饭、排骨煲仔饭"));
        return Collections.unmodifiableList(list);
    }

    /**
     * 构造旧分类名 → 新主类名映射
     *
     * <p>覆盖当前 19 个公共分类，以及历史家庭可能自建的常见同义分类名。</p>
     *
     * @return 映射表
     */
    private static Map<String, String> buildLegacyMapping() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("岷县", MINXIAN);
        map.put("热菜", MEAT);
        map.put("荤菜", MEAT);
        map.put("肉", MEAT);
        map.put("半荤半素", HALF_MEAT);
        map.put("半荤", HALF_MEAT);
        map.put("素菜", VEGETABLE);
        map.put("蔬菜", VEGETABLE);
        map.put("鱼", AQUATIC);
        map.put("水产", AQUATIC);
        map.put("蛋", EGG_BEAN);
        map.put("豆制品", EGG_BEAN);
        map.put("蛋豆", EGG_BEAN);
        map.put("凉拌", COLD);
        map.put("卤", COLD);
        map.put("凉卤", COLD);
        map.put("汤", SOUP);
        map.put("汤羹", SOUP);
        map.put("煮", STAPLE);
        map.put("面点", STAPLE);
        // 做法类旧分类不携带主料信息，统一映射为空（交给菜名/用料规则再判）
        map.put("炒", null);
        map.put("炖", null);
        map.put("炸", null);
        map.put("蒸", null);
        map.put("烤", null);
        map.put("主食", STAPLE);
        return Collections.unmodifiableMap(map);
    }

    /**
     * 主类定义
     *
     * @param name        分类名称（稳定标识）
     * @param icon        分类图标（emoji 文本）
     * @param sort        排序
     * @param description 分类说明（一句话定位）
     * @param basis       分类依据（放什么 / 不放什么 / 示例）
     * @author howe
     */
    public record Definition(String name, String icon, int sort, String description, String basis) {
    }
}
