package com.howe.pregnancy.util;

import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.FixedMetric;
import com.howe.pregnancy.domain.MetricFieldDef;
import com.howe.pregnancy.domain.MetricOptionDef;
import com.howe.pregnancy.domain.MetricSceneDef;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 固定指标目录
 *
 * <p>目标模型把指标从数据库移到代码：13 个内置指标编码稳定、不可改作其他语义，
 * 字段、单位、精度、场景与选项都由本目录定义并用于服务端校验。</p>
 *
 * <p>写入规则（与数据字典一致）：</p>
 * <ul>
 *   <li>单值指标写 {@code numeric_value}；血压写主值收缩压与次值舒张压；</li>
 *   <li>受控补充字段写 {@code detail_json}（form / level / description / name / dose）；</li>
 *   <li>便便主值为正整数次数；饮水按日求和；</li>
 *   <li>血糖场景必填；HCG 与孕酮保留原报告单位并分别统计。</li>
 * </ul>
 *
 * @author howe
 */
public final class BuiltinMetricCatalog {

    /** 血糖场景：空腹 */
    public static final String SCENE_FASTING = "FASTING";

    /** 血糖场景：餐后 */
    public static final String SCENE_AFTER_MEAL = "AFTER_MEAL";

    /** 血糖场景：睡前 */
    public static final String SCENE_BEFORE_SLEEP = "BEFORE_SLEEP";

    /** 便便性状：正常 */
    public static final String STOOL_NORMAL = "NORMAL";

    /** 便便性状：偏稀 */
    public static final String STOOL_LOOSE = "LOOSE";

    /** 便便性状：偏干 */
    public static final String STOOL_DRY = "DRY";

    /** 指标编码：体重 */
    public static final String METRIC_WEIGHT = "weight";

    /** 指标编码：HCG */
    public static final String METRIC_HCG = "hcg";

    /** 指标编码：孕酮 */
    public static final String METRIC_PROGESTERONE = "progesterone";

    /** 指标编码：血压 */
    public static final String METRIC_BLOOD_PRESSURE = "blood_pressure";

    /** 指标编码：血糖 */
    public static final String METRIC_BLOOD_GLUCOSE = "blood_glucose";

    /** 指标编码：体温 */
    public static final String METRIC_TEMPERATURE = "temperature";

    /** 指标编码：胎心 */
    public static final String METRIC_FETAL_HEART_RATE = "fetal_heart_rate";

    /** 指标编码：便便 */
    public static final String METRIC_STOOL = "stool";

    /** 指标编码：饮水量 */
    public static final String METRIC_WATER = "water";

    /** 指标编码：心情 */
    public static final String METRIC_MOOD = "mood";

    /** 指标编码：睡眠 */
    public static final String METRIC_SLEEP = "sleep";

    /** 指标编码：症状 */
    public static final String METRIC_SYMPTOM = "symptom";

    /** 指标编码：用药与营养补充 */
    public static final String METRIC_SUPPLEMENT = "supplement";

    /** 全部指标按默认展示顺序排列 */
    private static final List<FixedMetric> METRICS = buildMetrics();

    /**
     * 工具类不允许实例化
     */
    private BuiltinMetricCatalog() {
    }

    /**
     * 取全部固定指标定义
     *
     * <p>顺序即默认展示顺序；调用方不得修改返回内容。</p>
     *
     * @return 指标定义列表
     */
    public static List<FixedMetric> all() {
        return METRICS;
    }

    /**
     * 按编码取指标定义
     *
     * @param code 指标编码
     * @return 指标定义；未知编码返回 null
     */
    public static FixedMetric find(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return METRICS.stream().filter(item -> item.code().equals(code)).findFirst().orElse(null);
    }

    /**
     * 要求编码存在，否则按校验失败处理
     *
     * @param code 指标编码
     * @return 指标定义
     */
    public static FixedMetric require(String code) {
        FixedMetric metric = find(code);
        if (metric == null) {
            throw PregnancyException.invalid("未知的指标编码：" + code);
        }
        return metric;
    }

    /**
     * 构造全部指标定义
     *
     * @return 指标定义列表
     */
    private static List<FixedMetric> buildMetrics() {
        List<FixedMetric> list = new ArrayList<>(13);
        list.add(decimal(METRIC_WEIGHT, "体重", 10, true, List.of("kg"), 1, false, false));
        list.add(decimal(METRIC_HCG, "HCG", 20, true, List.of("mIU/mL", "IU/L"), 2, false, false));
        list.add(decimal(METRIC_PROGESTERONE, "孕酮", 30, true,
                List.of("ng/mL", "nmol/L"), 2, false, false));
        list.add(bloodPressure());
        list.add(bloodGlucose());
        list.add(decimal(METRIC_TEMPERATURE, "体温", 50, true, List.of("℃"), 1, false, false));
        list.add(decimal(METRIC_FETAL_HEART_RATE, "胎心", 60, true, List.of("次/分"), 0, false, false));
        list.add(stool());
        list.add(decimal(METRIC_WATER, "饮水量", 80, true, List.of("mL"), 0, false, true));
        list.add(mood());
        list.add(decimal(METRIC_SLEEP, "睡眠", 100, false, List.of("h"), 1, false, false));
        list.add(symptom());
        list.add(supplement());
        return List.copyOf(list);
    }

    /**
     * 构造单字段数值指标
     *
     * @param code        编码
     * @param name        名称
     * @param sortOrder   默认排序
     * @param visible     是否默认展示
     * @param units       允许单位
     * @param scale       精度
     * @param secondary   是否有次数值
     * @param summable    是否允许按日求和
     * @return 指标定义
     */
    private static FixedMetric decimal(String code, String name, int sortOrder, boolean visible,
                                       List<String> units, int scale, boolean secondary, boolean summable) {
        MetricFieldDef value = new MetricFieldDef("value", name, "DECIMAL", true,
                units, scale, null, summable, secondary);
        return new FixedMetric(code, name, "DECIMAL", sortOrder, visible,
                List.of(value), List.of(), false, units, secondary, summable, false);
    }

    /**
     * 构造血压指标
     *
     * @return 指标定义
     */
    private static FixedMetric bloodPressure() {
        MetricFieldDef systolic = new MetricFieldDef("systolic", "收缩压", "DECIMAL", true,
                List.of("mmHg"), 0, null, false, false);
        MetricFieldDef diastolic = new MetricFieldDef("diastolic", "舒张压", "DECIMAL", true,
                List.of("mmHg"), 0, null, false, false);
        return new FixedMetric(METRIC_BLOOD_PRESSURE, "血压", "BLOOD_PRESSURE", 40, true,
                List.of(systolic, diastolic), List.of(), false, List.of("mmHg"), true, false, false);
    }

    /**
     * 构造血糖指标
     *
     * @return 指标定义
     */
    private static FixedMetric bloodGlucose() {
        MetricFieldDef value = new MetricFieldDef("value", "血糖", "DECIMAL", true,
                List.of("mmol/L"), 1, null, false, false);
        List<MetricSceneDef> scenes = List.of(
                new MetricSceneDef(SCENE_FASTING, "空腹", 1),
                new MetricSceneDef(SCENE_AFTER_MEAL, "餐后", 2),
                new MetricSceneDef(SCENE_BEFORE_SLEEP, "睡前", 3));
        return new FixedMetric(METRIC_BLOOD_GLUCOSE, "血糖", "DECIMAL", 45, true,
                List.of(value), scenes, true, List.of("mmol/L"), false, false, false);
    }

    /**
     * 构造便便指标
     *
     * @return 指标定义
     */
    private static FixedMetric stool() {
        List<MetricOptionDef> forms = List.of(
                new MetricOptionDef(STOOL_NORMAL, "正常", 1),
                new MetricOptionDef(STOOL_LOOSE, "偏稀", 2),
                new MetricOptionDef(STOOL_DRY, "偏干", 3));
        MetricFieldDef count = new MetricFieldDef("count", "次数", "DECIMAL", false,
                List.of(), 0, null, false, false);
        MetricFieldDef form = new MetricFieldDef("form", "性状", "CHOICE", false,
                List.of(), 0, forms, false, false);
        return new FixedMetric(METRIC_STOOL, "便便", "DETAIL_ONLY", 70, true,
                List.of(form, count), List.of(), false, List.of(), false, false, true);
    }

    /**
     * 构造心情指标
     *
     * @return 指标定义
     */
    private static FixedMetric mood() {
        List<MetricOptionDef> levels = List.of(
                new MetricOptionDef("VERY_LOW", "很差", 1),
                new MetricOptionDef("LOW", "偏差", 2),
                new MetricOptionDef("NORMAL", "一般", 3),
                new MetricOptionDef("GOOD", "不错", 4),
                new MetricOptionDef("VERY_GOOD", "很好", 5));
        MetricFieldDef level = new MetricFieldDef("level", "心情", "CHOICE", true,
                List.of(), 0, levels, false, false);
        return new FixedMetric(METRIC_MOOD, "心情", "CHOICE", 90, false,
                List.of(level), List.of(), false, List.of(), false, false, true);
    }

    /**
     * 构造症状指标
     *
     * @return 指标定义
     */
    private static FixedMetric symptom() {
        MetricFieldDef description = new MetricFieldDef("description", "症状描述", "TEXT", false,
                List.of(), 0, null, false, false);
        return new FixedMetric(METRIC_SYMPTOM, "症状", "TEXT", 110, false,
                List.of(description), List.of(), false, List.of(), false, false, true);
    }

    /**
     * 构造用药与营养补充指标
     *
     * @return 指标定义
     */
    private static FixedMetric supplement() {
        MetricFieldDef name = new MetricFieldDef("name", "名称", "TEXT", false,
                List.of(), 0, null, false, false);
        MetricFieldDef dose = new MetricFieldDef("dose", "数量或剂量", "TEXT", false,
                List.of(), 0, null, false, false);
        return new FixedMetric(METRIC_SUPPLEMENT, "用药与营养补充", "TEXT", 120, false,
                List.of(name, dose), List.of(), false, List.of(), false, false, true);
    }

    /**
     * 校验并规范化测量值
     *
     * <p>返回值是写入数据库的三元组：主值、次值与受控补充 JSON 字段。
     * 所有校验都在这里完成，Service 只负责持久化。</p>
     *
     * @param metricCode    指标编码
     * @param unit          单位
     * @param scene         场景编码
     * @param numericValue  主值
     * @param secondaryValue 次值
     * @param detail        补充字段
     * @return 规范化结果
     */
    public static NormalizedMeasurement normalize(String metricCode, String unit, String scene,
                                                  BigDecimal numericValue, BigDecimal secondaryValue,
                                                  Map<String, Object> detail) {
        FixedMetric metric = require(metricCode);
        Map<String, Object> detailCopy = detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail);

        // 单位
        String normalizedUnit = unit == null || unit.isBlank() ? null : unit.trim();
        if (normalizedUnit != null && !metric.units().isEmpty() && !metric.units().contains(normalizedUnit)) {
            throw PregnancyException.invalid(metric.name() + "不支持单位：" + normalizedUnit);
        }
        if (metric.secondaryValue()) {
            if (numericValue == null || secondaryValue == null) {
                throw PregnancyException.invalid(metric.name() + "需要同时填写主值与次值");
            }
        } else if (metric.detailOnly()) {
            if (numericValue == null && detailCopy.isEmpty()) {
                throw PregnancyException.invalid(metric.name() + "需要填写内容");
            }
        } else if (numericValue == null) {
            throw PregnancyException.invalid(metric.name() + "需要填写数值");
        }

        // 场景
        String normalizedScene = scene == null || scene.isBlank() ? null : scene.trim();
        if (metric.sceneRequired() && normalizedScene == null) {
            throw PregnancyException.invalid(metric.name() + "需要选择场景");
        }
        if (normalizedScene != null && !metric.scenes().isEmpty()
                && metric.scenes().stream().noneMatch(item -> item.code().equals(normalizedScene))) {
            throw PregnancyException.invalid(metric.name() + "不支持场景：" + normalizedScene);
        }

        // 受控补充字段
        for (Map.Entry<String, Object> entry : new ArrayList<>(detailCopy.entrySet())) {
            String key = entry.getKey();
            MetricFieldDef field = metric.fields().stream()
                    .filter(item -> item.code().equals(key)).findFirst().orElse(null);
            if (field == null || "value".equals(key) || "systolic".equals(key) || "diastolic".equals(key)) {
                throw PregnancyException.invalid("未知的补充字段：" + key);
            }
            Object value = entry.getValue();
            if (value == null || (value instanceof String text && text.isBlank())) {
                detailCopy.remove(key);
                continue;
            }
            if ("CHOICE".equals(field.type())) {
                String code = String.valueOf(value);
                boolean valid = field.options() != null
                        && field.options().stream().anyMatch(option -> option.code().equals(code));
                if (!valid) {
                    throw PregnancyException.invalid(field.label() + "取值不合法：" + code);
                }
            }
        }

        // 数值精度与范围
        if (numericValue != null) {
            numericValue = normalizeScale(numericValue, metric);
            if (numericValue.signum() < 0) {
                throw PregnancyException.invalid(metric.name() + "不能为负数");
            }
        }
        if (secondaryValue != null) {
            if (secondaryValue.signum() < 0) {
                throw PregnancyException.invalid(metric.name() + "不能为负数");
            }
            secondaryValue = secondaryValue.stripTrailingZeros();
        }
        if (METRIC_STOOL.equals(metricCode) && numericValue != null) {
            if (numericValue.stripTrailingZeros().scale() > 0 || numericValue.signum() <= 0) {
                throw PregnancyException.invalid("便便次数必须为正整数");
            }
        }
        if (METRIC_MOOD.equals(metricCode) && !detailCopy.containsKey("level")) {
            throw PregnancyException.invalid("心情需要选择等级");
        }

        return new NormalizedMeasurement(metric, normalizedUnit, normalizedScene,
                numericValue, secondaryValue, detailCopy);
    }

    /**
     * 按指标精度规范化数值
     *
     * @param value  数值
     * @param metric 指标
     * @return 规范化后的数值
     */
    private static BigDecimal normalizeScale(BigDecimal value, FixedMetric metric) {
        int scale = metric.fields().stream()
                .filter(item -> "value".equals(item.code()) || "count".equals(item.code()))
                .mapToInt(MetricFieldDef::scale).findFirst().orElse(6);
        if (value.stripTrailingZeros().scale() > scale) {
            throw PregnancyException.invalid(metric.name() + "最多保留 " + scale + " 位小数");
        }
        return value.stripTrailingZeros();
    }

    /**
     * 规范化结果
     *
     * @param metric         指标定义
     * @param unit           单位
     * @param scene          场景
     * @param numericValue   主值
     * @param secondaryValue 次值
     * @param detail         补充字段
     */
    public record NormalizedMeasurement(FixedMetric metric, String unit, String scene,
                                        BigDecimal numericValue, BigDecimal secondaryValue,
                                        Map<String, Object> detail) {
    }
}
