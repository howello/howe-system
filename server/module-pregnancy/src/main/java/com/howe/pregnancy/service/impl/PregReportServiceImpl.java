package com.howe.pregnancy.service.impl;

import com.github.pagehelper.PageInfo;
import com.howe.common.utils.PageUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregCheckup;
import com.howe.pregnancy.domain.PregDietRecord;
import com.howe.pregnancy.domain.PregFile;
import com.howe.pregnancy.domain.PregReport;
import com.howe.pregnancy.domain.dto.DietSaveBody;
import com.howe.pregnancy.domain.dto.ReportSaveBody;
import com.howe.pregnancy.domain.vo.DietVO;
import com.howe.pregnancy.domain.vo.ReportVO;
import com.howe.pregnancy.mapper.PregCheckupMapper;
import com.howe.pregnancy.mapper.PregDietMapper;
import com.howe.pregnancy.mapper.PregFileMapper;
import com.howe.pregnancy.mapper.PregReportMapper;
import com.howe.pregnancy.service.IPregFileService;
import com.howe.pregnancy.service.IPregReportService;
import com.howe.pregnancy.service.IPregExpenseService;
import com.howe.pregnancy.service.IPregMeasurementService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import com.howe.pregnancy.util.RequestHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 报告与饮食服务实现
 *
 * <p>报告与饮食共用私有文件流程：创建或编辑时按传入顺序原子替换绑定；
 * 删除报告只解除测量关联、解绑原图并软删报告费用，不删除测量与产检。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PregReportServiceImpl implements IPregReportService {

    private final PregReportMapper reportMapper;

    private final PregDietMapper dietMapper;

    private final PregCheckupMapper checkupMapper;

    private final PregFileMapper fileMapper;

    private final IPregFileService fileService;

    private final IPregMeasurementService measurementService;

    private final IPregExpenseService expenseService;

    private final PregnancyAccessGuard accessGuard;

    @Override
    public PageInfo<ReportVO> listReports(LocalDate from, LocalDate to, Long checkupId) {
        accessGuard.requireReadable();
        PageUtils.startPage();
        return new PageInfo<>(reportMapper.selectList(from, to, checkupId)).convert(this::toReportVO);
    }

    @Override
    public ReportVO getReport(Long id) {
        accessGuard.requireReadable();
        PregReport report = reportMapper.selectById(id);
        if (report == null) {
            throw PregnancyException.notFound();
        }
        return toReportVO(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO createReport(ReportSaveBody body) {
        accessGuard.requireWritable();
        Long actor = accessGuard.currentUserId();
        String requestId = requireRequestId(body.getRequestId());
        LocalDate reportDate = PregnancyTimeUtils.parseDate(body.getReportDate(), "报告日期");
        if (PregnancyTimeUtils.isAfterBusinessToday(reportDate)) {
            throw PregnancyException.futureRecord("报告日期");
        }
        Long checkupId = parseOptionalId(body.getCheckupId());
        if (checkupId != null) {
            PregCheckup checkup = checkupMapper.selectById(checkupId);
            if (checkup == null) {
                throw PregnancyException.notFound();
            }
        }
        List<Long> fileIds = parseFileIds(body.getFileIds());
        if (fileIds.isEmpty()) {
            throw PregnancyException.invalid("报告至少需要 1 张原图");
        }
        String hash = RequestHashUtils.hash(body.getName(), body.getReportDate(), body.getCheckupId(),
                body.getNote(), body.getFileIds());

        PregReport existing = reportMapper.selectByRequest(actor, requestId);
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) {
                throw PregnancyException.idempotencyConflict(requestId);
            }
            if ("2".equals(existing.getDelFlag())) {
                throw PregnancyException.requestConsumed(requestId);
            }
            return toReportVO(existing);
        }

        PregReport report = new PregReport();
        report.setName(body.getName().trim());
        report.setReportDate(reportDate);
        report.setCheckupId(checkupId);
        report.setNote(body.getNote());
        report.setActorUserId(actor);
        report.setRequestId(requestId);
        report.setRequestHash(hash);
        report.setCreateBy(SecurityUtils.getUsername());
        reportMapper.insert(report);
        fileService.bind(PregnancyConstants.FILE_OWNER_REPORT, report.getId(), fileIds);
        return toReportVO(reportMapper.selectById(report.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO updateReport(Long id, com.howe.pregnancy.domain.dto.ReportUpdateBody body) {
        accessGuard.requireWritable();
        PregReport report = reportMapper.selectById(id);
        if (report == null) {
            throw PregnancyException.notFound();
        }
        if (body.getVersion() == null) {
            throw PregnancyException.versionConflict();
        }
        if (body.getName() != null) {
            if (body.getName().isBlank()) {
                throw PregnancyException.invalid("报告名称不能为空");
            }
            report.setName(body.getName().trim());
        }
        if (body.getReportDate() != null && !body.getReportDate().isBlank()) {
            LocalDate date = PregnancyTimeUtils.parseDate(body.getReportDate(), "报告日期");
            if (PregnancyTimeUtils.isAfterBusinessToday(date)) {
                throw PregnancyException.futureRecord("报告日期");
            }
            report.setReportDate(date);
        }
        if (body.getCheckupId() != null) {
            Long checkupId = parseOptionalId(body.getCheckupId());
            if (checkupId != null && checkupMapper.selectById(checkupId) == null) {
                throw PregnancyException.notFound();
            }
            report.setCheckupId(checkupId);
        }
        if (body.getNote() != null) {
            report.setNote(body.getNote());
        }
        report.setVersion(body.getVersion());
        report.setUpdateBy(SecurityUtils.getUsername());
        if (reportMapper.update(report) == 0) {
            throw PregnancyException.versionConflict();
        }
        // 省略 fileIds 不改关联；显式传列表则原子替换
        if (body.getFileIds() != null) {
            List<Long> fileIds = parseFileIds(body.getFileIds());
            if (fileIds.isEmpty()) {
                throw PregnancyException.invalid("报告至少需要 1 张原图");
            }
            fileService.bind(PregnancyConstants.FILE_OWNER_REPORT, id, fileIds);
        }
        return toReportVO(reportMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(Long id, Integer version) {
        accessGuard.requireWritable();
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
        if (reportMapper.selectById(id) == null) {
            throw PregnancyException.notFound();
        }
        // 报告费用、测量关联与文件绑定在同一事务内处理
        expenseService.deleteBySource(PregnancyConstants.EXPENSE_SOURCE_REPORT, id);
        reportMapper.detachMeasurements(id);
        fileService.detachAll(PregnancyConstants.FILE_OWNER_REPORT, id);
        if (reportMapper.logicalDelete(id, version) == 0) {
            throw PregnancyException.versionConflict();
        }
    }

    @Override
    public PageInfo<DietVO> listDiets(String mealType, LocalDate from, LocalDate to) {
        accessGuard.requireReadable();
        PageUtils.startPage();
        return new PageInfo<>(dietMapper.selectList(mealType, from, to)).convert(this::toDietVO);
    }

    @Override
    public DietVO getDiet(Long id) {
        accessGuard.requireReadable();
        PregDietRecord record = dietMapper.selectById(id);
        if (record == null) {
            throw PregnancyException.notFound();
        }
        return toDietVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DietVO createDiet(DietSaveBody body) {
        accessGuard.requireWritable();
        Long actor = accessGuard.currentUserId();
        String requestId = requireRequestId(body.getRequestId());
        OffsetDateTime occurredAt = PregnancyTimeUtils.parseOccurredAt(body.getOccurredAt(), "饮食时间");
        if (PregnancyTimeUtils.isAfterBusinessToday(PregnancyTimeUtils.businessDateOf(occurredAt))) {
            throw PregnancyException.futureRecord("饮食时间");
        }
        validateMealType(body.getMealType());
        List<Long> fileIds = parseFileIds(body.getFileIds());
        String hash = RequestHashUtils.hash(body.getMealType(), body.getOccurredAt(), body.getContent(),
                body.getNote(), body.getExtraText(), body.getFileIds());

        PregDietRecord existing = dietMapper.selectByRequest(actor, requestId);
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) {
                throw PregnancyException.idempotencyConflict(requestId);
            }
            if ("2".equals(existing.getDelFlag())) {
                throw PregnancyException.requestConsumed(requestId);
            }
            return toDietVO(existing);
        }

        PregDietRecord record = new PregDietRecord();
        record.setMealType(body.getMealType());
        record.setOccurredAt(occurredAt);
        record.setRecordDate(PregnancyTimeUtils.businessDateOf(occurredAt));
        record.setContent(body.getContent().trim());
        record.setNote(body.getNote());
        record.setExtraText(body.getExtraText());
        record.setActorUserId(actor);
        record.setRequestId(requestId);
        record.setRequestHash(hash);
        record.setCreateBy(SecurityUtils.getUsername());
        dietMapper.insert(record);
        if (!fileIds.isEmpty()) {
            fileService.bind(PregnancyConstants.FILE_OWNER_DIET, record.getId(), fileIds);
        }
        return toDietVO(dietMapper.selectById(record.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DietVO updateDiet(Long id, com.howe.pregnancy.domain.dto.DietUpdateBody body) {
        accessGuard.requireWritable();
        PregDietRecord record = dietMapper.selectById(id);
        if (record == null) {
            throw PregnancyException.notFound();
        }
        if (body.getVersion() == null) {
            throw PregnancyException.versionConflict();
        }
        if (body.getMealType() != null) {
            validateMealType(body.getMealType());
            record.setMealType(body.getMealType());
        }
        if (body.getOccurredAt() != null && !body.getOccurredAt().isBlank()) {
            OffsetDateTime occurredAt = PregnancyTimeUtils.parseOccurredAt(body.getOccurredAt(), "饮食时间");
            if (PregnancyTimeUtils.isAfterBusinessToday(PregnancyTimeUtils.businessDateOf(occurredAt))) {
                throw PregnancyException.futureRecord("饮食时间");
            }
            record.setOccurredAt(occurredAt);
            record.setRecordDate(PregnancyTimeUtils.businessDateOf(occurredAt));
        }
        if (body.getContent() != null) {
            if (body.getContent().isBlank()) {
                throw PregnancyException.invalid("饮食内容不能为空");
            }
            record.setContent(body.getContent().trim());
        }
        if (body.getNote() != null) {
            record.setNote(body.getNote());
        }
        if (body.getExtraText() != null) {
            record.setExtraText(body.getExtraText());
        }
        record.setVersion(body.getVersion());
        record.setUpdateBy(SecurityUtils.getUsername());
        if (dietMapper.update(record) == 0) {
            throw PregnancyException.versionConflict();
        }
        if (body.getFileIds() != null) {
            fileService.bind(PregnancyConstants.FILE_OWNER_DIET, id, parseFileIds(body.getFileIds()));
        }
        return toDietVO(dietMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDiet(Long id, Integer version) {
        accessGuard.requireWritable();
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
        if (dietMapper.selectById(id) == null) {
            throw PregnancyException.notFound();
        }
        fileService.detachAll(PregnancyConstants.FILE_OWNER_DIET, id);
        if (dietMapper.logicalDelete(id, version) == 0) {
            throw PregnancyException.versionConflict();
        }
        // 饮水记录属于指标，不随饮食删除
    }

    /**
     * 校验餐次
     *
     * @param mealType 餐次
     */
    private void validateMealType(String mealType) {
        Set<String> allowed = Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");
        if (!allowed.contains(mealType)) {
            throw PregnancyException.invalid("未知的餐次：" + mealType);
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
     * 解析文件 ID 列表
     *
     * @param fileIds 文本列表
     * @return ID 列表
     */
    private List<Long> parseFileIds(List<String> fileIds) {
        if (fileIds == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String text : fileIds) {
            Long id = parseOptionalId(text);
            if (id == null) {
                throw PregnancyException.invalid("文件ID不能为空");
            }
            result.add(id);
        }
        return result;
    }

    /**
     * 转换报告展示对象
     *
     * @param report 报告
     * @return 展示对象
     */
    private ReportVO toReportVO(PregReport report) {
        ReportVO vo = new ReportVO();
        vo.setId(String.valueOf(report.getId()));
        vo.setName(report.getName());
        vo.setReportDate(PregnancyTimeUtils.formatDate(report.getReportDate()));
        vo.setCheckupId(report.getCheckupId() == null ? null : String.valueOf(report.getCheckupId()));
        vo.setNote(report.getNote());
        vo.setActorUserId(report.getActorUserId() == null ? null : String.valueOf(report.getActorUserId()));
        vo.setVersion(report.getVersion());
        vo.setFiles(fileService.listByOwner(PregnancyConstants.FILE_OWNER_REPORT, report.getId()));
        vo.setMeasurements(measurementService.listByReport(report.getId()));
        vo.setExpenses(expenseService.listBySource(PregnancyConstants.EXPENSE_SOURCE_REPORT, report.getId()));
        return vo;
    }

    /**
     * 转换饮食展示对象
     *
     * @param record 记录
     * @return 展示对象
     */
    private DietVO toDietVO(PregDietRecord record) {
        DietVO vo = new DietVO();
        vo.setId(String.valueOf(record.getId()));
        vo.setMealType(record.getMealType());
        vo.setOccurredAt(PregnancyTimeUtils.formatOffset(record.getOccurredAt().toInstant()));
        vo.setRecordDate(PregnancyTimeUtils.formatDate(record.getRecordDate()));
        vo.setContent(record.getContent());
        vo.setNote(record.getNote());
        vo.setExtraText(record.getExtraText());
        vo.setActorUserId(record.getActorUserId() == null ? null : String.valueOf(record.getActorUserId()));
        vo.setVersion(record.getVersion());
        vo.setFiles(fileService.listByOwner(PregnancyConstants.FILE_OWNER_DIET, record.getId()));
        return vo;
    }

    /**
     * 查询报告的已绑定文件（内部使用）
     *
     * @param reportId 报告 ID
     * @return 文件列表
     */
    public List<PregFile> rawFiles(Long reportId) {
        return fileMapper.selectByOwner(PregnancyConstants.FILE_OWNER_REPORT, reportId);
    }
}
