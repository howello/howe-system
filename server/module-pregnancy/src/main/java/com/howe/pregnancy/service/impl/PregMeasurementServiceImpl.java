package com.howe.pregnancy.service.impl;

import com.github.pagehelper.PageInfo;
import com.howe.common.utils.PageUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.FixedMetric;
import com.howe.pregnancy.domain.MetricSceneDef;
import com.howe.pregnancy.domain.PregMeasurement;
import com.howe.pregnancy.domain.PregReport;
import com.howe.pregnancy.domain.dto.MeasurementSaveBody;
import com.howe.pregnancy.domain.vo.MeasurementVO;
import com.howe.pregnancy.mapper.PregMeasurementMapper;
import com.howe.pregnancy.mapper.PregReportMapper;
import com.howe.pregnancy.service.IPregMeasurementService;
import com.howe.pregnancy.util.BuiltinMetricCatalog;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import com.howe.pregnancy.util.RequestHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 固定指标测量服务实现
 *
 * <p>所有写入都按固定指标目录校验；创建走 {@code requestId} 幂等，编辑与删除走 {@code version}。
 * 实际测量时刻不得晚于业务今天，同一时刻允许存在多条真实记录。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PregMeasurementServiceImpl implements IPregMeasurementService {

    private final PregMeasurementMapper measurementMapper;

    private final PregReportMapper reportMapper;

    private final PregnancyAccessGuard accessGuard;

    @Override
    public PageInfo<MeasurementVO> list(String metricCode, LocalDate from, LocalDate to,
                                    String scene, String unit, Long reportId) {
        accessGuard.requireReadable();
        PageUtils.startPage();
        return new PageInfo<>(measurementMapper.selectList(metricCode, from, to, scene, unit, reportId)).convert(this::toVO);
    }

    @Override
    public MeasurementVO detail(Long id) {
        accessGuard.requireReadable();
        PregMeasurement measurement = measurementMapper.selectById(id);
        if (measurement == null) {
            throw PregnancyException.notFound();
        }
        return toVO(measurement);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeasurementVO create(MeasurementSaveBody body) {
        accessGuard.requireWritable();
        Long actorUserId = accessGuard.currentUserId();
        String requestId = requireRequestId(body.getRequestId());

        // 幂等：同范围同 ID 同内容返回已有资源，不同内容冲突
        PregMeasurement existing = measurementMapper.selectByRequest(actorUserId, requestId);
        String hash = RequestHashUtils.hash(body.getMetricCode(), body.getOccurredAt(), body.getUnit(),
                body.getScene(), body.getNumericValue(), body.getSecondaryValue(),
                body.getNote(), body.getReportId(), body.getDetail());
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) {
                throw PregnancyException.idempotencyConflict(requestId);
            }
            if ("2".equals(existing.getDelFlag())) {
                throw PregnancyException.requestConsumed(requestId);
            }
            return toVO(existing);
        }

        OffsetDateTime occurredAt = PregnancyTimeUtils.parseOccurredAt(body.getOccurredAt(), "测量时间");
        if (PregnancyTimeUtils.isAfterBusinessToday(PregnancyTimeUtils.businessDateOf(occurredAt))) {
            throw PregnancyException.futureRecord("测量时间");
        }
        Long reportId = parseOptionalId(body.getReportId());
        if (reportId != null) {
            PregReport report = reportMapper.selectById(reportId);
            if (report == null) {
                throw PregnancyException.notFound();
            }
        }

        BuiltinMetricCatalog.NormalizedMeasurement normalized = BuiltinMetricCatalog.normalize(
                body.getMetricCode(), body.getUnit(), body.getScene(),
                body.getNumericValue(), body.getSecondaryValue(), body.getDetail());

        PregMeasurement measurement = new PregMeasurement();
        measurement.setMetricCode(normalized.metric().code());
        measurement.setOccurredAt(occurredAt);
        measurement.setRecordDate(PregnancyTimeUtils.businessDateOf(occurredAt));
        measurement.setUnitCode(normalized.unit());
        measurement.setSceneCode(normalized.scene());
        measurement.setNumericValue(normalized.numericValue());
        measurement.setSecondaryValue(normalized.secondaryValue());
        measurement.setDetailJson(normalized.detail().isEmpty() ? null : JSON.toJSONString(normalized.detail()));
        measurement.setNote(body.getNote());
        measurement.setReportId(reportId);
        measurement.setActorUserId(actorUserId);
        measurement.setRequestId(requestId);
        measurement.setRequestHash(hash);
        measurement.setCreateBy(SecurityUtils.getUsername());
        measurementMapper.insert(measurement);
        return toVO(measurementMapper.selectById(measurement.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeasurementVO update(Long id, com.howe.pregnancy.domain.dto.MeasurementUpdateBody body) {
        accessGuard.requireWritable();
        PregMeasurement existing = measurementMapper.selectById(id);
        if (existing == null) {
            throw PregnancyException.notFound();
        }
        if (body.getVersion() == null) {
            throw PregnancyException.versionConflict();
        }

        OffsetDateTime occurredAt = existing.getOccurredAt();
        if (body.getOccurredAt() != null && !body.getOccurredAt().isBlank()) {
            occurredAt = PregnancyTimeUtils.parseOccurredAt(body.getOccurredAt(), "测量时间");
            if (PregnancyTimeUtils.isAfterBusinessToday(PregnancyTimeUtils.businessDateOf(occurredAt))) {
                throw PregnancyException.futureRecord("测量时间");
            }
        }

        Long reportId = body.getReportId() == null ? existing.getReportId()
                : parseOptionalId(body.getReportId());
        if (reportId != null) {
            PregReport report = reportMapper.selectById(reportId);
            if (report == null) {
                throw PregnancyException.notFound();
            }
        }

        // 未出现的字段保持原值
        BigDecimal numeric = body.getNumericValue() == null ? existing.getNumericValue() : body.getNumericValue();
        BigDecimal secondary = body.getSecondaryValue() == null ? existing.getSecondaryValue()
                : body.getSecondaryValue();
        String unit = body.getUnit() == null ? existing.getUnitCode() : body.getUnit();
        String scene = body.getScene() == null ? existing.getSceneCode() : body.getScene();
        Map<String, Object> detail = body.getDetail() == null ? parseDetail(existing.getDetailJson())
                : body.getDetail();

        BuiltinMetricCatalog.NormalizedMeasurement normalized = BuiltinMetricCatalog.normalize(
                existing.getMetricCode(), unit, scene, numeric, secondary, detail);

        existing.setOccurredAt(occurredAt);
        existing.setRecordDate(PregnancyTimeUtils.businessDateOf(occurredAt));
        existing.setUnitCode(normalized.unit());
        existing.setSceneCode(normalized.scene());
        existing.setNumericValue(normalized.numericValue());
        existing.setSecondaryValue(normalized.secondaryValue());
        existing.setDetailJson(normalized.detail().isEmpty() ? null : JSON.toJSONString(normalized.detail()));
        existing.setNote(body.getNote() == null ? existing.getNote() : body.getNote());
        existing.setReportId(reportId);
        existing.setVersion(body.getVersion());
        existing.setUpdateBy(SecurityUtils.getUsername());
        if (measurementMapper.update(existing) == 0) {
            throw PregnancyException.versionConflict();
        }
        return toVO(measurementMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        accessGuard.requireWritable();
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
        if (measurementMapper.logicalDelete(id, version, SecurityUtils.getUsername()) == 0) {
            throw PregnancyException.versionConflict();
        }
    }

    @Override
    public Map<String, Object> statistics(String metricCode, LocalDate from, LocalDate to,
                                          String scene, String unit, String aggregation) {
        accessGuard.requireReadable();
        FixedMetric metric = BuiltinMetricCatalog.require(metricCode);
        String mode = aggregation == null || aggregation.isBlank()
                ? PregnancyConstants.AGGREGATION_RAW : aggregation.trim().toUpperCase();
        if (PregnancyConstants.AGGREGATION_DAY_SUM.equals(mode) && !metric.summable()) {
            throw PregnancyException.invalid(metric.name() + "不支持按日求和");
        }
        if (!PregnancyConstants.AGGREGATION_RAW.equals(mode) && metric.secondaryValue()) {
            // 双值指标（血压）只提供原始点，不能按日合并
            throw PregnancyException.invalid(metric.name() + "不支持该聚合口径");
        }
        if (!PregnancyConstants.AGGREGATION_RAW.equals(mode)
                && !PregnancyConstants.AGGREGATION_DAY_LAST.equals(mode)
                && !PregnancyConstants.AGGREGATION_DAY_SUM.equals(mode)) {
            throw PregnancyException.invalid("未知的聚合口径：" + mode);
        }
        if (from == null) {
            from = PregnancyTimeUtils.businessToday().minusDays(PregnancyConstants.STATISTICS_DEFAULT_DAYS - 1L);
        }
        if (to == null) {
            to = PregnancyTimeUtils.businessToday();
        }
        if (to.isBefore(from)) {
            throw PregnancyException.invalid("结束日期不能早于起始日期");
        }

        List<PregMeasurement> rows = measurementMapper.selectForStatistics(metricCode, from, to, scene, unit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricCode", metric.code());
        result.put("metricName", metric.name());
        result.put("aggregation", mode);
        result.put("from", PregnancyTimeUtils.formatDate(from));
        result.put("to", PregnancyTimeUtils.formatDate(to));

        if (isDistribution(metric)) {
            result.put("distribution", distribution(metric, rows));
            result.put("series", List.of());
            return result;
        }

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (PregMeasurement row : rows) {
            String groupKey = "unit=" + nullSafe(row.getUnitCode()) + "|scene=" + nullSafe(row.getSceneCode());
            grouped.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(point(row, metric));
        }
        List<Map<String, Object>> series = new ArrayList<>();
        grouped.forEach((key, points) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            PregMeasurement first = rows.get(0);
            for (PregMeasurement row : rows) {
                if (("unit=" + nullSafe(row.getUnitCode()) + "|scene=" + nullSafe(row.getSceneCode())).equals(key)) {
                    first = row;
                    break;
                }
            }
            item.put("unit", first.getUnitCode());
            item.put("scene", first.getSceneCode());
            item.put("aggregation", mode);
            item.put("points", aggregate(points, mode));
            series.add(item);
        });
        result.put("series", series);
        return result;
    }

    @Override
    public List<MeasurementVO> listByReport(Long reportId) {
        PregReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return List.of();
        }
        return measurementMapper.selectByReport(reportId).stream().map(this::toVO).toList();
    }

    @Override
    public BigDecimal waterTotal(LocalDate date) {
        BigDecimal total = measurementMapper.sumWaterByDay(date);
        return total == null ? BigDecimal.ZERO : total;
    }

    /**
     * 判断指标是否以分布表达
     *
     * @param metric 指标
     * @return 选项类指标返回 true
     */
    private boolean isDistribution(FixedMetric metric) {
        return "CHOICE".equals(metric.kind());
    }

    /**
     * 组装分布结果
     *
     * @param metric 指标
     * @param rows   原始记录
     * @return 每项含 code、label、count
     */
    private List<Map<String, Object>> distribution(FixedMetric metric, List<PregMeasurement> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        metric.fields().stream().flatMap(field -> field.options() == null ? java.util.stream.Stream.empty()
                : field.options().stream()).forEach(option -> counts.put(option.code(), 0));
        for (PregMeasurement row : rows) {
            Map<String, Object> detail = parseDetail(row.getDetailJson());
            Object value = detail.get("level");
            if (value == null) {
                value = detail.get("form");
            }
            if (value != null) {
                counts.merge(String.valueOf(value), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counts.forEach((code, count) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("count", count);
            result.add(item);
        });
        return result;
    }

    /**
     * 组装单点
     *
     * @param row    记录
     * @param metric 指标
     * @return 点
     */
    private Map<String, Object> point(PregMeasurement row, FixedMetric metric) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", String.valueOf(row.getId()));
        point.put("occurredAt", PregnancyTimeUtils.formatOffset(row.getOccurredAt().toInstant()));
        BigDecimal primary = row.getNumericValue() == null ? BigDecimal.ZERO : row.getNumericValue();
        point.put("value", primary.stripTrailingZeros().toPlainString());
        if (metric.secondaryValue() && row.getSecondaryValue() != null) {
            point.put("secondaryValue", row.getSecondaryValue().stripTrailingZeros().toPlainString());
        }
        return point;
    }

    /**
     * 按聚合口径合并点
     *
     * @param points 原始点
     * @param mode   口径
     * @return 聚合后的点
     */
    private List<Map<String, Object>> aggregate(List<Map<String, Object>> points, String mode) {
        if (PregnancyConstants.AGGREGATION_RAW.equals(mode)) {
            return points;
        }
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> point : points) {
            String occurredAt = String.valueOf(point.get("occurredAt"));
            String day = occurredAt.length() >= 10 ? occurredAt.substring(0, 10) : occurredAt;
            Map<String, Object> current = byDay.get(day);
            if (current == null) {
                byDay.put(day, new LinkedHashMap<>(point));
                continue;
            }
            if (PregnancyConstants.AGGREGATION_DAY_SUM.equals(mode)) {
                BigDecimal left = new BigDecimal(String.valueOf(current.get("value")));
                BigDecimal right = new BigDecimal(String.valueOf(point.get("value")));
                current.put("value", left.add(right).stripTrailingZeros().toPlainString());
            } else {
                byDay.put(day, new LinkedHashMap<>(point));
            }
        }
        return new ArrayList<>(byDay.values());
    }

    /**
     * 转换展示对象
     *
     * @param row 记录
     * @return 展示对象
     */
    private MeasurementVO toVO(PregMeasurement row) {
        MeasurementVO vo = new MeasurementVO();
        vo.setId(String.valueOf(row.getId()));
        vo.setMetricCode(row.getMetricCode());
        FixedMetric metric = BuiltinMetricCatalog.find(row.getMetricCode());
        vo.setMetricName(metric == null ? row.getMetricCode() : metric.name());
        vo.setOccurredAt(PregnancyTimeUtils.formatOffset(row.getOccurredAt().toInstant()));
        vo.setRecordDate(PregnancyTimeUtils.formatDate(row.getRecordDate()));
        vo.setUnit(row.getUnitCode());
        vo.setSceneCode(row.getSceneCode());
        if (row.getSceneCode() != null && metric != null) {
            vo.setSceneLabel(metric.scenes().stream()
                    .filter(item -> item.code().equals(row.getSceneCode()))
                    .map(MetricSceneDef::label).findFirst().orElse(null));
        }
        vo.setNumericValue(row.getNumericValue());
        vo.setSecondaryValue(row.getSecondaryValue());
        vo.setDetail(parseDetail(row.getDetailJson()));
        vo.setNote(row.getNote());
        vo.setReportId(row.getReportId() == null ? null : String.valueOf(row.getReportId()));
        vo.setActorUserId(row.getActorUserId() == null ? null : String.valueOf(row.getActorUserId()));
        vo.setVersion(row.getVersion());
        return vo;
    }

    /**
     * 解析补充字段 JSON
     *
     * @param json JSON 文本
     * @return 字段映射
     */
    private Map<String, Object> parseDetail(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        JSONObject object = JSON.parseObject(json);
        return object == null ? new LinkedHashMap<>() : new LinkedHashMap<>(object);
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
     * @param text ID 文本
     * @return ID，空文本返回 null
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
     * 空值占位
     *
     * @param value 值
     * @return 非空文本
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
