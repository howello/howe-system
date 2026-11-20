package com.howe.pregnancy.domain;

import java.util.List;

/**
 * 固定指标定义
 *
 * <p>由 {@code BuiltinMetricCatalog} 在代码中提供，不落库、不可由用户新增或修改。
 * 稳定编码不得改作其他语义。</p>
 *
 * @param code          指标编码
 * @param name          指标名称
 * @param kind          写入形态（DECIMAL 单值 / BLOOD_PRESSURE 双值 / CHOICE 选项 /
 *                      TEXT 文本 / DETAIL_ONLY 仅补充字段）
 * @param sortOrder     默认展示顺序
 * @param visible       是否默认展示入口
 * @param fields        字段定义
 * @param scenes        场景定义
 * @param sceneRequired 是否必须选择场景
 * @param units         允许单位
 * @param secondaryValue 是否使用次数值
 * @param summable      是否允许按日求和
 * @param detailOnly    主值可为空、内容全部来自补充字段
 * @author howe
 */
public record FixedMetric(String code, String name, String kind, int sortOrder, boolean visible,
                          List<MetricFieldDef> fields, List<MetricSceneDef> scenes, boolean sceneRequired,
                          List<String> units, boolean secondaryValue, boolean summable, boolean detailOnly) {
}
