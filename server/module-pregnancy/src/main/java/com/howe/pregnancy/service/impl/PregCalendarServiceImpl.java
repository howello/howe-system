package com.howe.pregnancy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregCheckup;
import com.howe.pregnancy.domain.PregDietRecord;
import com.howe.pregnancy.domain.PregMeasurement;
import com.howe.pregnancy.domain.PregReport;
import com.howe.pregnancy.domain.PregTodo;
import com.howe.pregnancy.domain.vo.CalendarDayVO;
import com.howe.pregnancy.domain.vo.CalendarMonthVO;
import com.howe.pregnancy.domain.vo.GrowthVO;
import com.howe.pregnancy.domain.vo.MeasurementVO;
import com.howe.pregnancy.mapper.PregCheckupMapper;
import com.howe.pregnancy.mapper.PregDietMapper;
import com.howe.pregnancy.mapper.PregFileMapper;
import com.howe.pregnancy.mapper.PregMeasurementMapper;
import com.howe.pregnancy.mapper.PregReportMapper;
import com.howe.pregnancy.mapper.PregTodoMapper;
import com.howe.pregnancy.service.IPregCalendarService;
import com.howe.pregnancy.service.IPregMeasurementService;
import com.howe.pregnancy.util.BuiltinMetricCatalog;
import com.howe.pregnancy.util.GestationUtils;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancySeedProvider;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日历与每日变化服务实现
 *
 * <p>日历没有第二份业务记录表：月摘要与当天详情都从各业务表实时聚合。
 * 每日变化读取随模块发布的只读 JSON 资源，按孕日与孕周匹配，
 * 单侧缺日时回退同孕周的「本周概况」。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class PregCalendarServiceImpl implements IPregCalendarService {

    private final PregMeasurementMapper measurementMapper;

    private final PregTodoMapper todoMapper;

    private final PregCheckupMapper checkupMapper;

    private final PregReportMapper reportMapper;

    private final PregDietMapper dietMapper;

    private final PregFileMapper fileMapper;

    private final IPregMeasurementService measurementService;

    private final PregnancyAccessGuard accessGuard;

    private final PregnancySeedProvider contentProvider;

    @Override
    public CalendarMonthVO month(LocalDate from, LocalDate to) {
        accessGuard.requireReadable();
        if (from == null || to == null) {
            throw PregnancyException.invalid("月摘要必须提供起止日期");
        }
        if (to.isBefore(from)) {
            throw PregnancyException.invalid("结束日期不能早于起始日期");
        }
        long span = ChronoUnit.DAYS.between(from, to) + 1;
        if (span > PregnancyConstants.CALENDAR_MAX_SPAN_DAYS) {
            throw PregnancyException.invalid("月摘要跨度不能超过 " + PregnancyConstants.CALENDAR_MAX_SPAN_DAYS + " 天");
        }
        LocalDate dueDate = PregnancyConfig.dueDate();
        Map<LocalDate, Integer> measurementCounts = toCountMap(measurementMapper.countByDay(from, to));
        Map<LocalDate, Integer> todoCounts = toCountMap(todoMapper.countByDay(from, to));
        Map<LocalDate, Integer> checkupCounts = toCountMap(checkupMapper.countByDay(from, to));
        Map<LocalDate, Integer> reportCounts = toCountMap(reportMapper.countByDay(from, to));
        Map<LocalDate, Integer> dietCounts = toCountMap(dietMapper.countByDay(from, to));

        CalendarMonthVO vo = new CalendarMonthVO();
        vo.setBusinessToday(PregnancyTimeUtils.formatDate(PregnancyTimeUtils.businessToday()));
        vo.setDueDate(PregnancyTimeUtils.formatDate(dueDate));
        List<CalendarMonthVO.DaySummary> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            CalendarMonthVO.DaySummary summary = new CalendarMonthVO.DaySummary();
            summary.setDate(PregnancyTimeUtils.formatDate(date));
            summary.setGestationalDay(GestationUtils.gestationalDay(dueDate, date));
            summary.setGestationalAge(GestationUtils.formatGestation(dueDate, date));
            List<String> markers = new ArrayList<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            addMarker(markers, counts, PregnancyConstants.MARKER_MEASUREMENT,
                    measurementCounts.get(date), "records");
            addMarker(markers, counts, PregnancyConstants.MARKER_TODO, todoCounts.get(date), "todos");
            addMarker(markers, counts, PregnancyConstants.MARKER_CHECKUP, checkupCounts.get(date), "checkups");
            addMarker(markers, counts, PregnancyConstants.MARKER_REPORT, reportCounts.get(date), "reports");
            addMarker(markers, counts, PregnancyConstants.MARKER_DIET, dietCounts.get(date), "diets");
            summary.setMarkers(markers);
            summary.setCounts(counts);
            days.add(summary);
        }
        vo.setDays(days);
        return vo;
    }

    @Override
    public CalendarDayVO day(LocalDate date) {
        Set<String> roles = accessGuard.requireReadable();
        boolean canWrite = accessGuard.canWrite(roles);
        LocalDate dueDate = PregnancyConfig.dueDate();
        LocalDate today = PregnancyTimeUtils.businessToday();
        boolean future = date.isAfter(today);

        CalendarDayVO vo = new CalendarDayVO();
        vo.setDate(PregnancyTimeUtils.formatDate(date));
        vo.setBusinessToday(PregnancyTimeUtils.formatDate(today));
        vo.setGestationalDay(GestationUtils.gestationalDay(dueDate, date));
        vo.setGestationalAge(GestationUtils.formatGestation(dueDate, date));

        vo.setRecordGroups(groupMeasurements(date));
        vo.setDiets(dietItems(date));
        vo.setCheckups(checkupItems(date));
        vo.setReports(reportItems(date));
        vo.setTodos(todoItems(date));
        vo.setWaterTotal(measurementService.waterTotal(date).stripTrailingZeros().toPlainString());

        CalendarDayVO.AllowedActions actions = new CalendarDayVO.AllowedActions();
        // 未来日期只允许预约与待办；实际记录入口按角色与日期共同决定
        actions.setCanAddMeasurement(canWrite && !future);
        actions.setCanAddDiet(canWrite && !future);
        actions.setCanAddReport(canWrite && !future);
        actions.setCanAddTodo(canWrite);
        actions.setCanBookCheckup(canWrite);
        actions.setCanCompleteCheckup(canWrite && !future);
        vo.setAllowedActions(actions);
        return vo;
    }

    @Override
    public GrowthVO growth(LocalDate from, LocalDate to) {
        accessGuard.requireReadable();
        if (from == null || to == null) {
            throw PregnancyException.invalid("每日变化必须提供起止日期");
        }
        if (to.isBefore(from)) {
            throw PregnancyException.invalid("结束日期不能早于起始日期");
        }
        long span = ChronoUnit.DAYS.between(from, to) + 1;
        if (span > PregnancyConstants.GROWTH_MAX_SPAN_DAYS) {
            throw PregnancyException.invalid("每日变化单次最多 " + PregnancyConstants.GROWTH_MAX_SPAN_DAYS + " 天");
        }
        LocalDate dueDate = PregnancyConfig.dueDate();

        GrowthVO vo = new GrowthVO();
        vo.setDueDate(PregnancyTimeUtils.formatDate(dueDate));
        vo.setBusinessToday(PregnancyTimeUtils.formatDate(PregnancyTimeUtils.businessToday()));
        List<GrowthVO.DayGrowth> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            GrowthVO.DayGrowth day = new GrowthVO.DayGrowth();
            day.setDate(PregnancyTimeUtils.formatDate(date));
            Integer gestationalDay = GestationUtils.gestationalDay(dueDate, date);
            Integer week = GestationUtils.week(dueDate, date);
            day.setGestationalDay(gestationalDay);
            day.setGestationalAge(GestationUtils.formatGestation(dueDate, date));

            if (dueDate == null) {
                day.setStatus("DUE_DATE_UNSET");
                days.add(day);
                continue;
            }
            if (gestationalDay != null && gestationalDay < 0) {
                day.setStatus("BEFORE_PREGNANCY");
                days.add(day);
                continue;
            }
            GrowthVO.SideContent mother = sideContent("mother", gestationalDay, week);
            GrowthVO.SideContent baby = sideContent("baby", gestationalDay, week);
            day.setMother(mother);
            day.setBaby(baby);
            day.setStatus(mother == null && baby == null ? "NO_CONTENT" : "AVAILABLE");
            days.add(day);
        }
        vo.setDays(days);
        return vo;
    }

    /**
     * 组装单侧内容
     *
     * @param side            侧别（mother/baby）
     * @param gestationalDay  孕日
     * @param week            孕周
     * @return 内容，缺失返回 null
     */
    private GrowthVO.SideContent sideContent(String side, Integer gestationalDay, Integer week) {
        JsonNode root = contentProvider.growth();
        if (root == null) {
            return null;
        }
        JsonNode daily = root.path(side + "Daily");
        if (GestationUtils.hasDailyContent(gestationalDay)) {
            JsonNode text = daily.path(String.valueOf(gestationalDay));
            if (text.isTextual() && !text.asText().isBlank()) {
                GrowthVO.SideContent content = new GrowthVO.SideContent();
                content.setSource("DAILY");
                content.setText(text.asText());
                content.setWeekly(false);
                return content;
            }
        }
        if (GestationUtils.hasWeeklyContent(week)) {
            String title = "mother".equals(side) ? "准妈妈本周概况" : "胎宝宝本周概况";
            for (JsonNode section : root.path("weeks").path(String.valueOf(week)).path("sections")) {
                if (title.equals(section.path("title").asText())) {
                    GrowthVO.SideContent content = new GrowthVO.SideContent();
                    content.setSource("WEEKLY");
                    List<String> paragraphs = new ArrayList<>();
                    section.path("paragraphs").forEach(node -> paragraphs.add(node.asText()));
                    content.setText(String.join("\n\n", paragraphs));
                    content.setWeekly(true);
                    return content;
                }
            }
        }
        return null;
    }

    /**
     * 组装测量分组
     *
     * @param date 日期
     * @return 分组
     */
    private List<CalendarDayVO.RecordGroup> groupMeasurements(LocalDate date) {
        List<PregMeasurement> rows = measurementMapper.selectList(null, date, date, null, null, null);
        Map<String, CalendarDayVO.RecordGroup> groups = new LinkedHashMap<>();
        for (PregMeasurement row : rows) {
            CalendarDayVO.RecordGroup group = groups.computeIfAbsent(row.getMetricCode(), code -> {
                CalendarDayVO.RecordGroup created = new CalendarDayVO.RecordGroup();
                created.setMetricCode(code);
                var metric = BuiltinMetricCatalog.find(code);
                created.setMetricName(metric == null ? code : metric.name());
                created.setRecords(new ArrayList<>());
                return created;
            });
            group.getRecords().add(toMeasurementVO(row));
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * 转换测量展示对象
     *
     * @param row 记录
     * @return 展示对象
     */
    private MeasurementVO toMeasurementVO(PregMeasurement row) {
        MeasurementVO vo = new MeasurementVO();
        vo.setId(String.valueOf(row.getId()));
        vo.setMetricCode(row.getMetricCode());
        var metric = BuiltinMetricCatalog.find(row.getMetricCode());
        vo.setMetricName(metric == null ? row.getMetricCode() : metric.name());
        vo.setOccurredAt(PregnancyTimeUtils.formatOffset(row.getOccurredAt().toInstant()));
        vo.setRecordDate(PregnancyTimeUtils.formatDate(row.getRecordDate()));
        vo.setUnit(row.getUnitCode());
        vo.setSceneCode(row.getSceneCode());
        vo.setNumericValue(row.getNumericValue());
        vo.setSecondaryValue(row.getSecondaryValue());
        vo.setNote(row.getNote());
        vo.setVersion(row.getVersion());
        return vo;
    }

    /**
     * 组装饮食摘要
     *
     * @param date 日期
     * @return 饮食列表
     */
    private List<CalendarDayVO.DietItem> dietItems(LocalDate date) {
        List<CalendarDayVO.DietItem> result = new ArrayList<>();
        for (PregDietRecord record : dietMapper.selectByDay(date)) {
            CalendarDayVO.DietItem item = new CalendarDayVO.DietItem();
            item.setId(String.valueOf(record.getId()));
            item.setMealType(record.getMealType());
            item.setOccurredAt(PregnancyTimeUtils.formatOffset(record.getOccurredAt().toInstant()));
            item.setContent(record.getContent());
            item.setFileCount(fileMapper.selectByOwner(PregnancyConstants.FILE_OWNER_DIET, record.getId()).size());
            result.add(item);
        }
        return result;
    }

    /**
     * 组装产检摘要
     *
     * @param date 日期
     * @return 产检列表
     */
    private List<CalendarDayVO.CheckupItem> checkupItems(LocalDate date) {
        List<CalendarDayVO.CheckupItem> result = new ArrayList<>();
        for (PregCheckup checkup : checkupMapper.selectByDay(date)) {
            CalendarDayVO.CheckupItem item = new CalendarDayVO.CheckupItem();
            item.setId(String.valueOf(checkup.getId()));
            item.setTitle(checkup.getTitle());
            item.setStatus(checkup.getStatus());
            item.setAppointmentTime(checkup.getAppointmentTime() == null ? null
                    : checkup.getAppointmentTime().toString());
            item.setHospital(checkup.getHospital());
            result.add(item);
        }
        return result;
    }

    /**
     * 组装报告摘要
     *
     * @param date 日期
     * @return 报告列表
     */
    private List<CalendarDayVO.ReportItem> reportItems(LocalDate date) {
        List<CalendarDayVO.ReportItem> result = new ArrayList<>();
        for (PregReport report : reportMapper.selectByDay(date)) {
            CalendarDayVO.ReportItem item = new CalendarDayVO.ReportItem();
            item.setId(String.valueOf(report.getId()));
            item.setName(report.getName());
            item.setFileCount(fileMapper.selectByOwner(PregnancyConstants.FILE_OWNER_REPORT, report.getId()).size());
            result.add(item);
        }
        return result;
    }

    /**
     * 组装待办摘要
     *
     * @param date 日期
     * @return 待办列表
     */
    private List<CalendarDayVO.TodoItem> todoItems(LocalDate date) {
        List<CalendarDayVO.TodoItem> result = new ArrayList<>();
        for (PregTodo todo : todoMapper.selectByDay(date)) {
            CalendarDayVO.TodoItem item = new CalendarDayVO.TodoItem();
            item.setId(String.valueOf(todo.getId()));
            item.setTitle(todo.getTitle());
            item.setDone(todo.getDone());
            item.setDueTime(todo.getDueTime() == null ? null : todo.getDueTime().toString());
            result.add(item);
        }
        return result;
    }

    /**
     * 把统计结果转成日期索引
     *
     * @param rows 统计行
     * @return 日期到数量的映射
     */
    private Map<LocalDate, Integer> toCountMap(List<Map<String, Object>> rows) {
        Map<LocalDate, Integer> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("recordDate");
            Object total = row.get("total");
            if (date == null) {
                date = row.get("dueDate");
            }
            if (date == null || total == null) {
                continue;
            }
            map.put(PregnancyTimeUtils.toLocalDate((java.util.Date) date),
                    ((Number) total).intValue());
        }
        return map;
    }

    /**
     * 追加标记与数量
     *
     * @param markers 标记列表
     * @param counts  数量映射
     * @param marker  标记编码
     * @param total   数量
     * @param key     数量键
     */
    private void addMarker(List<String> markers, Map<String, Integer> counts,
                           String marker, Integer total, String key) {
        if (total == null || total <= 0) {
            return;
        }
        markers.add(marker);
        counts.put(key, total);
    }

}
