package com.howe.pregnancy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregCheckup;
import com.howe.pregnancy.domain.PregCheckupDetail;
import com.howe.pregnancy.domain.PregReport;
import com.howe.pregnancy.domain.dto.CheckupAppointmentBody;
import com.howe.pregnancy.domain.dto.CheckupSaveBody;
import com.howe.pregnancy.domain.dto.CheckupStateBody;
import com.howe.pregnancy.domain.vo.CheckupVO;
import com.howe.pregnancy.domain.vo.ReportVO;
import com.howe.pregnancy.mapper.PregCheckupMapper;
import com.howe.pregnancy.mapper.PregReportMapper;
import com.howe.pregnancy.service.IPregCheckupService;
import com.howe.pregnancy.service.IPregExpenseService;
import com.howe.pregnancy.util.GestationUtils;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 产检服务实现
 *
 * <p>状态机：{@code PLANNED ⇄ BOOKED → COMPLETED}，任意状态可 {@code CANCELLED}。
 * 完成、撤销完成、改期与取消都递增父版本，使旧提醒失效；建议窗口与实际预约分开保存。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PregCheckupServiceImpl implements IPregCheckupService {

    private final PregCheckupMapper checkupMapper;

    private final PregReportMapper reportMapper;

    private final IPregExpenseService expenseService;

    private final PregnancyAccessGuard accessGuard;

    private final PregnancySeedProvider seedProvider;

    private final com.howe.pregnancy.service.IPregTaskService taskService;

    @Override
    public List<CheckupVO> list() {
        accessGuard.requireReadable();
        ensureSeeded();
        return checkupMapper.selectList().stream().map(item -> toVO(item, false)).toList();
    }

    @Override
    public CheckupVO get(Long id) {
        accessGuard.requireReadable();
        PregCheckup checkup = requireCheckup(id);
        return toVO(checkup, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO create(CheckupSaveBody body) {
        accessGuard.requireWritable();
        String requestId = body.getRequestId() == null ? null : body.getRequestId().trim();
        if (requestId != null && !requestId.isEmpty()) {
            PregCheckup existing = checkupMapper.selectByRequest(requestId);
            if (existing != null) {
                String hash = RequestHashUtils.hash(body.getTitle(), body.getSuggestStartDay(),
                        body.getSuggestEndDay(), body.getSuggestedDate(), body.getFocus());
                if (!hash.equals(existing.getRequestHash())) {
                    throw PregnancyException.idempotencyConflict(requestId);
                }
                if ("2".equals(existing.getDelFlag())) {
                    throw PregnancyException.requestConsumed(requestId);
                }
                return toVO(existing, true);
            }
        }
        validateWindow(body.getSuggestStartDay(), body.getSuggestEndDay());

        PregCheckup checkup = new PregCheckup();
        checkup.setTitle(body.getTitle().trim());
        checkup.setSuggestStartDay(body.getSuggestStartDay());
        checkup.setSuggestEndDay(body.getSuggestEndDay());
        checkup.setSuggestedDate(parseOptionalDate(body.getSuggestedDate(), "建议日期"));
        checkup.setHospital(body.getHospital());
        checkup.setLocation(body.getLocation());
        checkup.setFocus(body.getFocus());
        checkup.setStatus(PregnancyConstants.CHECKUP_PLANNED);
        checkup.setRequestId(requestId == null || requestId.isEmpty() ? null : requestId);
        checkup.setRequestHash(RequestHashUtils.hash(body.getTitle(), body.getSuggestStartDay(),
                body.getSuggestEndDay(), body.getSuggestedDate(), body.getFocus()));
        checkup.setCreateBy(SecurityUtils.getUsername());
        checkupMapper.insert(checkup);
        if (body.getItems() != null) {
            replaceDetail(checkup.getId(), PregnancyConstants.DETAIL_ITEM, body.getItems());
        }
        if (body.getPreparations() != null) {
            replaceDetail(checkup.getId(), PregnancyConstants.DETAIL_PREPARATION, body.getPreparations());
        }
        return toVO(checkupMapper.selectById(checkup.getId()), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO update(Long id, com.howe.pregnancy.domain.dto.CheckupUpdateBody body) {
        accessGuard.requireWritable();
        PregCheckup checkup = requireCheckup(id);
        if (body.getVersion() == null) {
            throw PregnancyException.versionConflict();
        }
        if (checkup.getStatus() == null) {
            throw PregnancyException.invalidState("产检状态异常，请刷新");
        }
        if (body.getTitle() != null) {
            if (body.getTitle().isBlank()) {
                throw PregnancyException.invalid("产检标题不能为空");
            }
            checkup.setTitle(body.getTitle().trim());
        }
        validateWindow(body.getSuggestStartDay(), body.getSuggestEndDay());
        if (body.getSuggestStartDay() != null) {
            checkup.setSuggestStartDay(body.getSuggestStartDay());
        }
        if (body.getSuggestEndDay() != null) {
            checkup.setSuggestEndDay(body.getSuggestEndDay());
        }
        if (body.getSuggestedDate() != null) {
            checkup.setSuggestedDate(body.getSuggestedDate().isBlank() ? null
                    : PregnancyTimeUtils.parseDate(body.getSuggestedDate(), "建议日期"));
        }
        if (body.getHospital() != null) {
            checkup.setHospital(body.getHospital());
        }
        if (body.getLocation() != null) {
            checkup.setLocation(body.getLocation());
        }
        if (body.getFocus() != null) {
            checkup.setFocus(body.getFocus());
        }
        checkup.setVersion(body.getVersion());
        checkup.setUpdateBy(SecurityUtils.getUsername());
        if (checkupMapper.update(checkup) == 0) {
            throw PregnancyException.versionConflict();
        }
        if (body.getItems() != null) {
            replaceDetail(id, PregnancyConstants.DETAIL_ITEM, body.getItems());
            bumpVersion(id);
        }
        if (body.getPreparations() != null) {
            replaceDetail(id, PregnancyConstants.DETAIL_PREPARATION, body.getPreparations());
            bumpVersion(id);
        }
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO replaceItems(Long id, Integer version, List<CheckupSaveBody.DetailItem> items) {
        accessGuard.requireWritable();
        requireCheckup(id);
        requireVersion(version);
        replaceDetail(id, PregnancyConstants.DETAIL_ITEM, items);
        bumpVersion(id, version);
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO replacePreparations(Long id, Integer version,
                                         List<CheckupSaveBody.DetailItem> preparations) {
        accessGuard.requireWritable();
        requireCheckup(id);
        requireVersion(version);
        replaceDetail(id, PregnancyConstants.DETAIL_PREPARATION, preparations);
        bumpVersion(id, version);
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO togglePreparation(Long id, Long prepId, Boolean done, Integer version) {
        accessGuard.requireWritable();
        requireCheckup(id);
        requireVersion(version);
        PregCheckupDetail detail = checkupMapper.selectDetailById(prepId);
        if (detail == null || !id.equals(detail.getCheckupId())
                || !PregnancyConstants.DETAIL_PREPARATION.equals(detail.getType())) {
            throw PregnancyException.notFound();
        }
        checkupMapper.updatePreparationDone(prepId, Boolean.TRUE.equals(done), accessGuard.currentUserId());
        bumpVersion(id, version);
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO book(Long id, CheckupAppointmentBody body) {
        accessGuard.requireWritable();
        PregCheckup checkup = requireCheckup(id);
        requireVersion(body.getVersion());
        if (PregnancyConstants.CHECKUP_CANCELLED.equals(checkup.getStatus())) {
            throw PregnancyException.invalidState("已取消的计划不能直接预约");
        }
        if (PregnancyConstants.CHECKUP_COMPLETED.equals(checkup.getStatus())) {
            throw PregnancyException.invalidState("已完成的产检不能修改预约");
        }
        LocalDate appointmentDate = PregnancyTimeUtils.parseDate(body.getAppointmentDate(), "预约日期");
        LocalTime appointmentTime = PregnancyTimeUtils.parseOptionalTime(body.getAppointmentTime(), "预约时刻");
        checkup.setAppointmentDate(appointmentDate);
        checkup.setAppointmentTime(appointmentTime);
        if (body.getHospital() != null) {
            checkup.setHospital(body.getHospital());
        }
        if (body.getLocation() != null) {
            checkup.setLocation(body.getLocation());
        }
        checkup.setStatus(PregnancyConstants.CHECKUP_BOOKED);
        checkup.setVersion(body.getVersion());
        checkup.setUpdateBy(SecurityUtils.getUsername());
        if (checkupMapper.update(checkup) == 0) {
            throw PregnancyException.versionConflict();
        }
        scheduleReminders(checkupMapper.selectById(id));
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO cancelAppointment(Long id, Integer version) {
        accessGuard.requireWritable();
        PregCheckup checkup = requireCheckup(id);
        requireVersion(version);
        if (PregnancyConstants.CHECKUP_COMPLETED.equals(checkup.getStatus())) {
            throw PregnancyException.invalidState("已完成的产检不能撤销预约");
        }
        checkup.setAppointmentDate(null);
        checkup.setAppointmentTime(null);
        if (!PregnancyConstants.CHECKUP_CANCELLED.equals(checkup.getStatus())) {
            checkup.setStatus(PregnancyConstants.CHECKUP_PLANNED);
        }
        checkup.setVersion(version);
        checkup.setUpdateBy(SecurityUtils.getUsername());
        if (checkupMapper.update(checkup) == 0) {
            throw PregnancyException.versionConflict();
        }
        // 撤销预约后不再发送预约提醒
        taskService.cancelRemindersFor(id);
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO complete(Long id, CheckupStateBody body) {
        accessGuard.requireWritable();
        PregCheckup checkup = requireCheckup(id);
        requireVersion(body.getVersion());
        if (PregnancyConstants.CHECKUP_CANCELLED.equals(checkup.getStatus())) {
            throw PregnancyException.invalidState("已取消的计划不能标记完成");
        }
        LocalDate completedDate = PregnancyTimeUtils.parseDate(body.getCompletedDate(), "实际完成日期");
        if (PregnancyTimeUtils.isAfterBusinessToday(completedDate)) {
            throw PregnancyException.futureRecord("实际完成日期");
        }
        checkup.setCompletedDate(completedDate);
        checkup.setStatus(PregnancyConstants.CHECKUP_COMPLETED);
        checkup.setVersion(body.getVersion());
        checkup.setUpdateBy(SecurityUtils.getUsername());
        if (checkupMapper.update(checkup) == 0) {
            throw PregnancyException.versionConflict();
        }
        // 完成后取消不再需要的提醒
        taskService.cancelRemindersFor(id);
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO reopen(Long id, CheckupStateBody body) {
        accessGuard.requireWritable();
        PregCheckup checkup = requireCheckup(id);
        requireVersion(body.getVersion());
        if (!PregnancyConstants.CHECKUP_COMPLETED.equals(checkup.getStatus())) {
            throw PregnancyException.invalidState("只有已完成的产检可以撤销完成");
        }
        checkup.setCompletedDate(null);
        checkup.setStatus(checkup.getAppointmentDate() != null
                ? PregnancyConstants.CHECKUP_BOOKED : PregnancyConstants.CHECKUP_PLANNED);
        checkup.setVersion(body.getVersion());
        checkup.setUpdateBy(SecurityUtils.getUsername());
        if (checkupMapper.update(checkup) == 0) {
            throw PregnancyException.versionConflict();
        }
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckupVO cancel(Long id, CheckupStateBody body) {
        accessGuard.requireWritable();
        PregCheckup checkup = requireCheckup(id);
        requireVersion(body.getVersion());
        if (PregnancyConstants.CHECKUP_CANCELLED.equals(checkup.getStatus())) {
            return toVO(checkup, true);
        }
        checkup.setStatus(PregnancyConstants.CHECKUP_CANCELLED);
        checkup.setVersion(body.getVersion());
        checkup.setUpdateBy(SecurityUtils.getUsername());
        if (checkupMapper.update(checkup) == 0) {
            throw PregnancyException.versionConflict();
        }
        // 取消整次计划不删除报告与费用，但不再发送提醒
        taskService.cancelRemindersFor(id);
        return toVO(checkupMapper.selectById(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int ensureSeeded() {
        JsonNode seeds = seedProvider.checkups();
        if (seeds == null || !seeds.isArray() || seeds.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (JsonNode seed : seeds) {
            String seedKey = seed.path("seedKey").asText(null);
            if (seedKey == null || seedKey.isBlank()) {
                continue;
            }
            // 软删或取消的行保留稳定键，重复执行跳过且不覆盖用户数据
            PregCheckup existing = checkupMapper.selectBySeedKey(seedKey);
            if (existing != null) {
                continue;
            }
            PregCheckup checkup = new PregCheckup();
            checkup.setSeedKey(seedKey);
            checkup.setTitle(seed.path("title").asText("产检"));
            checkup.setSuggestStartDay(seed.path("suggestStartDay").isNumber()
                    ? seed.path("suggestStartDay").asInt() : null);
            checkup.setSuggestEndDay(seed.path("suggestEndDay").isNumber()
                    ? seed.path("suggestEndDay").asInt() : null);
            checkup.setFocus(seed.path("focus").isTextual() ? seed.path("focus").asText() : null);
            checkup.setStatus(PregnancyConstants.CHECKUP_PLANNED);
            checkup.setCreateBy("system");
            checkupMapper.insert(checkup);
            insertDetail(checkup.getId(), PregnancyConstants.DETAIL_ITEM, seed.path("items"));
            insertDetail(checkup.getId(), PregnancyConstants.DETAIL_PREPARATION,
                    seed.path("preparations"));
            created++;
        }
        return created;
    }

    /**
     * 插入种子详情
     *
     * @param checkupId 产检 ID
     * @param type      类型
     * @param nodes     节点
     */
    private void insertDetail(Long checkupId, String type, JsonNode nodes) {
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            return;
        }
        List<PregCheckupDetail> details = new ArrayList<>();
        int order = 0;
        for (JsonNode node : nodes) {
            String title = node.path("title").asText("").trim();
            if (title.isEmpty()) {
                continue;
            }
            PregCheckupDetail detail = new PregCheckupDetail();
            detail.setCheckupId(checkupId);
            detail.setType(type);
            detail.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            detail.setDescription(node.path("description").isTextual()
                    ? node.path("description").asText() : null);
            detail.setSortOrder(order++);
            details.add(detail);
        }
        if (!details.isEmpty()) {
            checkupMapper.insertDetails(details);
        }
    }

    /**
     * 原子替换某一类详情
     *
     * @param checkupId 产检 ID
     * @param type      类型
     * @param items     请求项
     */
    private void replaceDetail(Long checkupId, String type, List<CheckupSaveBody.DetailItem> items) {
        checkupMapper.deleteDetailsByType(checkupId, type);
        if (items == null || items.isEmpty()) {
            return;
        }
        List<PregCheckupDetail> details = new ArrayList<>();
        int order = 0;
        for (CheckupSaveBody.DetailItem item : items) {
            if (item == null || item.getTitle() == null || item.getTitle().isBlank()) {
                throw PregnancyException.invalid("详情标题不能为空");
            }
            PregCheckupDetail detail = new PregCheckupDetail();
            detail.setCheckupId(checkupId);
            detail.setType(type);
            detail.setTitle(item.getTitle().trim());
            detail.setDescription(item.getDescription());
            detail.setEstimatedMinutes(item.getEstimatedMinutes());
            detail.setSortOrder(order++);
            details.add(detail);
        }
        checkupMapper.insertDetails(details);
    }

    /**
     * 按当前规则安排预约提醒
     *
     * <p>规则来自孕期管理员维护的配置；未启用或未配置时刻时不排程。
     * 同一产检同一发送时刻合并为一条消息，去重键不含规则版本。</p>
     *
     * @param checkup 产检
     */
    private void scheduleReminders(PregCheckup checkup) {
        if (checkup.getAppointmentDate() == null) {
            return;
        }
        try {
            com.alibaba.fastjson2.JSONObject rules = reminderRules();
            for (var entry : rules.entrySet()) {
                String code = entry.getKey();
                com.alibaba.fastjson2.JSONObject rule = (com.alibaba.fastjson2.JSONObject) entry.getValue();
                if (!Boolean.TRUE.equals(rule.getBoolean("enabled"))) {
                    continue;
                }
                String time = rule.getString("time");
                if (time == null || !time.matches("^([01][0-9]|2[0-3]):[0-5][0-9]$")) {
                    continue;
                }
                String[] parts = time.split(":");
                LocalDate target = checkup.getAppointmentDate();
                if ("CHECKUP_BEFORE".equals(code)) {
                    target = target.minusDays(1);
                }
                java.time.OffsetDateTime executeAt = target.atTime(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]))
                        .atZone(PregnancyTimeUtils.businessZone()).toOffsetDateTime();
                String dedupKey = "REMINDER:" + checkup.getId() + ":" + target + ":" + time;
                taskService.scheduleReminder(dedupKey, checkup.getId(), checkup.getVersion(),
                        "{\"rule\":\"" + code + "\"}", executeAt);
            }
        } catch (RuntimeException e) {
            // 提醒排程失败不影响预约保存
            log.warn("安排产检提醒失败：checkupId={}", checkup.getId(), e);
        }
    }

    /**
     * 读取已保存的提醒规则
     *
     * @return 规则映射（编码 → 规则）
     */
    private com.alibaba.fastjson2.JSONObject reminderRules() {
        String text = com.howe.common.utils.ConfigUtils.getString(PregnancyConstants.CONFIG_REMINDER_RULES);
        com.alibaba.fastjson2.JSONObject result = new com.alibaba.fastjson2.JSONObject();
        if (text == null || text.isBlank()) {
            return result;
        }
        com.alibaba.fastjson2.JSONArray array = com.alibaba.fastjson2.JSON.parseObject(text)
                .getJSONArray("rules");
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.size(); i++) {
            com.alibaba.fastjson2.JSONObject item = array.getJSONObject(i);
            if (item != null && item.getString("code") != null) {
                result.put(item.getString("code"), item);
            }
        }
        return result;
    }

    /**
     * 递增产检版本
     *
     * @param id 产检 ID
     */
    private void bumpVersion(Long id) {
        PregCheckup current = checkupMapper.selectById(id);
        if (current == null) {
            throw PregnancyException.notFound();
        }
        bumpVersion(id, current.getVersion());
    }

    /**
     * 按预期版本递增
     *
     * @param id      产检 ID
     * @param version 预期版本
     */
    private void bumpVersion(Long id, Integer version) {
        if (checkupMapper.bumpVersion(id, version) == 0) {
            throw PregnancyException.versionConflict();
        }
    }

    /**
     * 校验建议窗口
     *
     * @param start 起始孕日
     * @param end   结束孕日
     */
    private void validateWindow(Integer start, Integer end) {
        if (start != null && start < 0) {
            throw PregnancyException.invalid("起始孕日不能为负数");
        }
        if (end != null && end < 0) {
            throw PregnancyException.invalid("结束孕日不能为负数");
        }
        if (start != null && end != null && end < start) {
            throw PregnancyException.invalid("结束孕日不能早于起始孕日");
        }
    }

    /**
     * 校验版本
     *
     * @param version 版本
     */
    private void requireVersion(Integer version) {
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
    }

    /**
     * 解析可选日期
     *
     * @param text  文本
     * @param field 字段名
     * @return 日期
     */
    private LocalDate parseOptionalDate(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return PregnancyTimeUtils.parseDate(text, field);
    }

    /**
     * 要求产检存在
     *
     * @param id 产检 ID
     * @return 产检
     */
    private PregCheckup requireCheckup(Long id) {
        PregCheckup checkup = checkupMapper.selectById(id);
        if (checkup == null) {
            throw PregnancyException.notFound();
        }
        return checkup;
    }

    /**
     * 转换展示对象
     *
     * @param checkup 产检
     * @param detail  是否加载详情、报告与费用
     * @return 展示对象
     */
    private CheckupVO toVO(PregCheckup checkup, boolean detail) {
        CheckupVO vo = new CheckupVO();
        vo.setId(String.valueOf(checkup.getId()));
        vo.setTitle(checkup.getTitle());
        vo.setSuggestStartDay(checkup.getSuggestStartDay());
        vo.setSuggestEndDay(checkup.getSuggestEndDay());
        vo.setSuggestedDate(PregnancyTimeUtils.formatDate(checkup.getSuggestedDate()));
        vo.setAppointmentDate(PregnancyTimeUtils.formatDate(checkup.getAppointmentDate()));
        vo.setAppointmentTime(checkup.getAppointmentTime() == null ? null
                : checkup.getAppointmentTime().toString());
        vo.setHospital(checkup.getHospital());
        vo.setLocation(checkup.getLocation());
        vo.setCompletedDate(PregnancyTimeUtils.formatDate(checkup.getCompletedDate()));
        vo.setFocus(checkup.getFocus());
        vo.setStatus(checkup.getStatus());
        vo.setVersion(checkup.getVersion());
        vo.setOverdue(deriveOverdue(checkup));
        deriveSuggestedDates(vo, checkup);
        if (detail) {
            JsonNode template = seedProvider.checkup(checkup.getSeedKey());
            if (!template.isMissingNode()) {
                vo.setReference(new CheckupVO.ReferenceVO(template.path("imageUrl").asText(null),
                        template.path("items"), template.path("tips"), template.path("estimatedCosts"),
                        template.path("ultrasoundNotes"), template.path("questions")));
            }
            List<PregCheckupDetail> details = checkupMapper.selectDetails(checkup.getId());
            vo.setItems(details.stream()
                    .filter(item -> PregnancyConstants.DETAIL_ITEM.equals(item.getType()))
                    .map(this::toDetailVO).toList());
            vo.setPreparations(details.stream()
                    .filter(item -> PregnancyConstants.DETAIL_PREPARATION.equals(item.getType()))
                    .map(this::toDetailVO).toList());
            vo.setReports(reportMapper.selectList(null, null, checkup.getId()).stream()
                    .map(this::toReportLite).toList());
            vo.setExpenses(expenseService.listBySource(
                    PregnancyConstants.EXPENSE_SOURCE_CHECKUP, checkup.getId()));
        }
        return vo;
    }

    /**
     * 转换详情
     *
     * @param detail 详情
     * @return 展示对象
     */
    private CheckupVO.DetailVO toDetailVO(PregCheckupDetail detail) {
        CheckupVO.DetailVO vo = new CheckupVO.DetailVO();
        vo.setId(String.valueOf(detail.getId()));
        vo.setTitle(detail.getTitle());
        vo.setDescription(detail.getDescription());
        vo.setSortOrder(detail.getSortOrder());
        vo.setEstimatedMinutes(detail.getEstimatedMinutes());
        vo.setDone(detail.getDone());
        return vo;
    }

    /**
     * 报告摘要（避免产检详情递归加载费用与测量）
     *
     * @param report 报告
     * @return 摘要
     */
    private ReportVO toReportLite(PregReport report) {
        ReportVO vo = new ReportVO();
        vo.setId(String.valueOf(report.getId()));
        vo.setName(report.getName());
        vo.setReportDate(PregnancyTimeUtils.formatDate(report.getReportDate()));
        vo.setCheckupId(report.getCheckupId() == null ? null : String.valueOf(report.getCheckupId()));
        vo.setNote(report.getNote());
        vo.setVersion(report.getVersion());
        return vo;
    }

    /**
     * 派生建议日期
     *
     * <p>未预约、未完成且按孕日窗口排程的计划实时派生；手填日期与实际日期保留。</p>
     *
     * @param vo      展示对象
     * @param checkup 产检
     */
    private void deriveSuggestedDates(CheckupVO vo, PregCheckup checkup) {
        LocalDate dueDate = PregnancyConfig.dueDate();
        if (dueDate == null || checkup.getSuggestStartDay() == null) {
            return;
        }
        boolean fixed = PregnancyConstants.CHECKUP_BOOKED.equals(checkup.getStatus())
                || PregnancyConstants.CHECKUP_COMPLETED.equals(checkup.getStatus())
                || PregnancyConstants.CHECKUP_CANCELLED.equals(checkup.getStatus());
        if (fixed || checkup.getSuggestedDate() != null) {
            return;
        }
        vo.setDerivedWindowStart(PregnancyTimeUtils.formatDate(
                GestationUtils.dateOfGestationalDay(dueDate, checkup.getSuggestStartDay())));
        if (checkup.getSuggestEndDay() != null) {
            vo.setDerivedWindowEnd(PregnancyTimeUtils.formatDate(
                    GestationUtils.dateOfGestationalDay(dueDate, checkup.getSuggestEndDay())));
        }
        vo.setDerivedSuggestedDate(vo.getDerivedWindowStart());
    }

    /**
     * 派生逾期标签
     *
     * @param checkup 产检
     * @return 逾期返回 true
     */
    private Boolean deriveOverdue(PregCheckup checkup) {
        if (PregnancyConstants.CHECKUP_COMPLETED.equals(checkup.getStatus())
                || PregnancyConstants.CHECKUP_CANCELLED.equals(checkup.getStatus())) {
            return false;
        }
        LocalDate target = checkup.getAppointmentDate() != null ? checkup.getAppointmentDate()
                : checkup.getSuggestedDate();
        if (target == null) {
            LocalDate dueDate = PregnancyConfig.dueDate();
            if (dueDate != null && checkup.getSuggestEndDay() != null) {
                target = GestationUtils.dateOfGestationalDay(dueDate, checkup.getSuggestEndDay());
            }
        }
        return target != null && target.isBefore(PregnancyTimeUtils.businessToday());
    }
}
