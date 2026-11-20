package com.howe.pregnancy.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.poi.excel.BigExcelWriter;
import cn.hutool.poi.excel.ExcelUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregCheckup;
import com.howe.pregnancy.domain.PregDietRecord;
import com.howe.pregnancy.domain.PregExpense;
import com.howe.pregnancy.domain.PregFile;
import com.howe.pregnancy.domain.PregMeasurement;
import com.howe.pregnancy.domain.PregReport;
import com.howe.pregnancy.domain.PregTask;
import com.howe.pregnancy.domain.dto.ExportCreateBody;
import com.howe.pregnancy.domain.vo.ExportVO;
import com.howe.pregnancy.mapper.PregCheckupMapper;
import com.howe.pregnancy.mapper.PregDietMapper;
import com.howe.pregnancy.mapper.PregExpenseMapper;
import com.howe.pregnancy.mapper.PregFileMapper;
import com.howe.pregnancy.mapper.PregMeasurementMapper;
import com.howe.pregnancy.mapper.PregReportMapper;
import com.howe.pregnancy.mapper.PregTaskMapper;
import com.howe.pregnancy.service.IPregExportService;
import com.howe.pregnancy.storage.PrivateFileStorage;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 导出服务实现
 *
 * <p>统一生成私有 ZIP：CSV 使用 UTF-8 BOM，XLSX 使用 Hutool BigExcelWriter（底层复用
 * 已引入的 POI）；manifest 记录快照时刻、归档文件名与 SHA-256。原图直接复制字节，
 * 不重新编码。ZIP 生成后独立保留至到期。</p>
 *
 * <p>构建截止时间固定为快照建立后 2 小时；解绑原图保留 24 小时，
 * 覆盖构建窗口与重试。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PregExportServiceImpl implements IPregExportService {

    /** 导出根目录 */
    private static final String EXPORT_DIR = "exports";

    /** 快照子目录 */
    private static final String SNAPSHOT_DIR = "snapshots";

    private final PregTaskMapper taskMapper;

    private final PregMeasurementMapper measurementMapper;

    private final PregCheckupMapper checkupMapper;

    private final PregReportMapper reportMapper;

    private final PregDietMapper dietMapper;

    private final PregExpenseMapper expenseMapper;

    private final PregFileMapper fileMapper;

    private final PrivateFileStorage storage;

    private final PregnancyAccessGuard accessGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportVO create(ExportCreateBody body) {
        accessGuard.requireReadable();
        Long requester = accessGuard.currentUserId();
        String format = body.getFormat() == null ? "CSV" : body.getFormat().toUpperCase();
        if (!"CSV".equals(format) && !"XLSX".equals(format)) {
            throw PregnancyException.invalid("导出格式只支持 CSV 或 XLSX");
        }
        String requestId = body.getRequestId() == null || body.getRequestId().isBlank()
                ? UUID.randomUUID().toString() : body.getRequestId().trim();
        String dedupKey = "EXPORT:" + requester + ":" + requestId;
        JSONObject payload = new JSONObject();
        payload.put("format", format);
        payload.put("includeRecords", Boolean.TRUE.equals(body.getIncludeRecords()));
        payload.put("includeCheckupsAndExpenses", Boolean.TRUE.equals(body.getIncludeCheckupsAndExpenses()));
        payload.put("includeReportPhotos", Boolean.TRUE.equals(body.getIncludeReportPhotos()));
        payload.put("includeOtherPhotos", Boolean.TRUE.equals(body.getIncludeOtherPhotos()));
        PregTask existing = taskMapper.selectByDedupKey(dedupKey);
        if (existing != null) {
            if (!payload.equals(parseObject(existing.getPayloadJson()))) {
                throw PregnancyException.idempotencyConflict(requestId);
            }
            return toVO(existing);
        }

        PregTask task = new PregTask();
        task.setKind(PregnancyConstants.TASK_EXPORT);
        task.setBusinessId(null);
        task.setBusinessVersion(null);
        task.setRequesterUserId(requester);
        task.setDedupKey(dedupKey);
        task.setPayloadJson(payload.toJSONString());
        task.setStageJson("{}");
        task.setStatus(PregnancyConstants.TASK_PENDING);
        task.setAttempt(0);
        task.setMaxAttempts(PregnancyConfig.taskMaxAttempts());
        task.setNextRunAt(PregnancyTimeUtils.now());
        taskMapper.insert(task);
        return toVO(taskMapper.selectById(task.getId()));
    }

    @Override
    public List<ExportVO> list() {
        accessGuard.requireReadable();
        return taskMapper.selectExportsByRequester(accessGuard.currentUserId())
                .stream().map(this::toVO).toList();
    }

    @Override
    public ExportVO get(Long id) {
        accessGuard.requireReadable();
        return toVO(requireOwnTask(id));
    }

    @Override
    public ExportContent content(Long id) {
        accessGuard.requireReadable();
        PregTask task = requireOwnTask(id);
        if (!PregnancyConstants.TASK_SUCCEEDED.equals(task.getStatus())) {
            throw PregnancyException.invalidState("导出尚未完成");
        }
        if (task.getExpiresAt() != null && task.getExpiresAt().isBefore(PregnancyTimeUtils.now())) {
            throw PregnancyException.exportExpired();
        }
        if (task.getResultRef() == null || task.getResultRef().isBlank()) {
            throw PregnancyException.exportExpired();
        }
        // 申请者失去读取权限后不再允许下载
        if (!accessGuard.hasReadAccess(accessGuard.currentUserId())) {
            throw PregnancyException.notFound();
        }
        try {
            InputStream input = storage.open(task.getResultRef());
            long size = storage.size(task.getResultRef());
            return new ExportContent(input, "pregnancy-export-" + id + ".zip", size);
        } catch (IOException e) {
            throw PregnancyException.exportExpired();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean build(Long taskId, String leaseToken) {
        PregTask task = taskMapper.selectById(taskId);
        if (task == null || !PregnancyConstants.TASK_RUNNING.equals(task.getStatus())
                || !leaseToken.equals(task.getLeaseToken())) {
            return false;
        }
        JSONObject payload = parseObject(task.getPayloadJson());
        JSONObject stage = parseObject(task.getStageJson());
        LocalDateTime buildDeadline = task.getExpiresAt() == null ? null
                : task.getExpiresAt().toLocalDateTime();
        if (buildDeadline == null) {
            buildDeadline = PregnancyTimeUtils.now()
                    .plusMinutes(PregnancyConfig.exportBuildWindowMinutes()).toLocalDateTime();
            stage.put("stage", "SNAPSHOT");
            stage.put("snapshotAt", PregnancyTimeUtils.now().toString());
            stage.put("buildDeadline", buildDeadline.toString());
            if (taskMapper.updateStage(taskId, leaseToken, stage.toJSONString(),
                    PregnancyTimeUtils.now().toLocalDateTime()) == 0) {
                return false;
            }
        }

        String zipKey = EXPORT_DIR + "/" + taskId + "/" + UUID.randomUUID().toString().replace("-", "") + ".zip";
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"),
                "preg-export-" + taskId + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(tempDir);
            LocalDate today = PregnancyTimeUtils.businessToday();
            LocalDate from = null;
            List<PregMeasurement> measurements = Boolean.TRUE.equals(payload.getBoolean("includeRecords"))
                    ? measurementMapper.selectList(null, from, today, null, null, null) : List.of();
            List<PregCheckup> checkups = Boolean.TRUE.equals(payload.getBoolean("includeCheckupsAndExpenses"))
                    ? checkupMapper.selectList() : List.of();
            List<PregExpense> expenses = Boolean.TRUE.equals(payload.getBoolean("includeCheckupsAndExpenses"))
                    ? expenseMapper.selectList(from, today, null, null) : List.of();

            String format = payload.getString("format");
            Path dataFile = "XLSX".equals(format)
                    ? writeXlsx(tempDir, measurements, checkups, expenses)
                    : writeCsv(tempDir, measurements, checkups, expenses);

            List<Path> photoFiles = new ArrayList<>();
            List<Map<String, Object>> manifest = new ArrayList<>();
            if (Boolean.TRUE.equals(payload.getBoolean("includeReportPhotos"))) {
                collectReportPhotos(tempDir, manifest, photoFiles, buildDeadline);
            }
            if (Boolean.TRUE.equals(payload.getBoolean("includeOtherPhotos"))) {
                collectDietPhotos(tempDir, manifest, photoFiles, buildDeadline);
            }
            manifest.add(0, Map.of(
                    "kind", "data",
                    "archiveName", dataFile.getFileName().toString(),
                    "sha256", sha256(dataFile)
            ));

            Path manifestFile = tempDir.resolve("manifest.json");
            Files.writeString(manifestFile, buildManifestJson(taskId, stage, dataFile, manifest),
                    StandardCharsets.UTF_8);

            Path zipTemp = tempDir.resolve(dataFile.getFileName() + ".zip");
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(zipTemp))) {
                // 逐项写入，保持原图字节与数据文件不变
                try (cn.hutool.core.compress.ZipWriter writer = new cn.hutool.core.compress.ZipWriter(out, StandardCharsets.UTF_8)) {
                    writer.add(dataFile.getFileName().toString(), Files.newInputStream(dataFile));
                    for (Map<String, Object> entry : manifest) {
                        Object archiveName = entry.get("archiveName");
                        if (archiveName == null || "data".equals(entry.get("kind"))) {
                            continue;
                        }
                        Path source = tempDir.resolve(String.valueOf(archiveName));
                        if (Files.isRegularFile(source)) {
                            writer.add(String.valueOf(archiveName), Files.newInputStream(source));
                        }
                    }
                    writer.add("manifest.json", Files.newInputStream(manifestFile));
                }
            }
            try (InputStream input = Files.newInputStream(zipTemp)) {
                storage.write(zipKey, input);
            }
            long size = storage.size(zipKey);
            OffsetDateTime now = PregnancyTimeUtils.now();
            stage.put("stage", "READY");
            stage.put("fileSize", size);
            if (taskMapper.complete(taskId, leaseToken, PregnancyConstants.TASK_SUCCEEDED,
                    stage.toJSONString(), zipKey, now.plusHours(PregnancyConfig.exportReadyHours()).toLocalDateTime(),
                    null, now.toLocalDateTime()) == 0) {
                storage.delete(zipKey);
                return false;
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("孕期导出构建失败：taskId={}", taskId, e);
            // 由导出服务按明确失败落库：调用方不再重复标记，避免状态与阶段被旧快照覆盖
            taskMapper.fail(taskId, leaseToken, PregnancyConstants.TASK_FAILED,
                    "EXPORT_FAILED", task.getAttempt(), task.getMaxAttempts(),
                    PregnancyTimeUtils.now().toLocalDateTime(), stage.toJSONString(),
                    PregnancyTimeUtils.now().toLocalDateTime());
            return true;
        } finally {
            try {
                FileUtil.del(tempDir.toFile());
            } catch (RuntimeException ignored) {
                // 临时目录清理失败不影响结果
            }
        }
    }

    /**
     * 收集报告原图
     *
     * @param tempDir       临时目录
     * @param manifest      manifest 条目
     * @param photoFiles    已写入文件
     * @param buildDeadline 构建截止时间
     */
    private void collectReportPhotos(Path tempDir, List<Map<String, Object>> manifest,
                                     List<Path> photoFiles, LocalDateTime buildDeadline) {
        List<PregReport> reports = reportMapper.selectList(null, null, null);
        for (PregReport report : reports) {
            List<PregFile> files = reportMapper.selectFilesByReport(report.getId());
            int index = 1;
            for (PregFile file : files) {
                assertBeforeDeadline(buildDeadline);
                String archiveName = "photos/report-" + report.getId() + "-" + index + extensionOf(file.getMimeType());
                copyPhoto(file, tempDir.resolve(archiveName), photoFiles);
                manifest.add(Map.of(
                        "kind", "report-photo",
                        "ownerId", String.valueOf(report.getId()),
                        "originalName", file.getOriginalName(),
                        "archiveName", archiveName,
                        "sha256", file.getSha256()
                ));
                index++;
            }
        }
    }

    /**
     * 收集饮食原图
     *
     * @param tempDir       临时目录
     * @param manifest      manifest 条目
     * @param photoFiles    已写入文件
     * @param buildDeadline 构建截止时间
     */
    private void collectDietPhotos(Path tempDir, List<Map<String, Object>> manifest,
                                   List<Path> photoFiles, LocalDateTime buildDeadline) {
        List<PregDietRecord> records = dietMapper.selectList(null, null, null);
        for (PregDietRecord record : records) {
            List<PregFile> files = fileMapper.selectByOwner(PregnancyConstants.FILE_OWNER_DIET, record.getId());
            int index = 1;
            for (PregFile file : files) {
                assertBeforeDeadline(buildDeadline);
                String archiveName = "photos/diet-" + record.getId() + "-" + index + extensionOf(file.getMimeType());
                copyPhoto(file, tempDir.resolve(archiveName), photoFiles);
                manifest.add(Map.of(
                        "kind", "diet-photo",
                        "ownerId", String.valueOf(record.getId()),
                        "originalName", file.getOriginalName(),
                        "archiveName", archiveName,
                        "sha256", file.getSha256()
                ));
                index++;
            }
        }
    }

    /**
     * 复制原图字节
     *
     * @param file     文件
     * @param target   目标路径
     * @param collected 已写入文件
     */
    private void copyPhoto(PregFile file, Path target, List<Path> collected) {
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = storage.open(file.getStorageKey())) {
                Files.copy(input, target);
            }
            collected.add(target);
        } catch (IOException e) {
            log.warn("导出读取原图失败：fileId={}", file.getId(), e);
        }
    }

    /**
     * 校验构建截止时间
     *
     * @param buildDeadline 截止时间
     */
    private void assertBeforeDeadline(LocalDateTime buildDeadline) {
        if (LocalDateTime.now().isAfter(buildDeadline)) {
            throw PregnancyException.invalidState("导出构建已超时，请重新申请");
        }
    }

    /**
     * 写 CSV
     *
     * @param dir          临时目录
     * @param measurements 测量
     * @param checkups     产检
     * @param expenses     费用
     * @return 数据文件
     * @throws IOException 写失败
     */
    private Path writeCsv(Path dir, List<PregMeasurement> measurements, List<PregCheckup> checkups,
                          List<PregExpense> expenses) throws IOException {
        Path file = dir.resolve("pregnancy-data.csv");
        StringBuilder builder = new StringBuilder("﻿");
        builder.append("类型,记录ID,日期/时刻,指标或名称,数值,单位,场景,备注\n");
        for (PregMeasurement row : measurements) {
            builder.append("measurement,").append(row.getId()).append(',')
                    .append(csv(row.getOccurredAt().toString())).append(',')
                    .append(csv(row.getMetricCode())).append(',')
                    .append(csv(row.getNumericValue() == null ? "" : row.getNumericValue().toPlainString())).append(',')
                    .append(csv(row.getUnitCode())).append(',')
                    .append(csv(row.getSceneCode())).append(',')
                    .append(csv(row.getNote())).append('\n');
        }
        for (PregCheckup row : checkups) {
            builder.append("checkup,").append(row.getId()).append(',')
                    .append(csv(PregnancyTimeUtils.formatDate(row.getAppointmentDate()))).append(',')
                    .append(csv(row.getTitle())).append(',')
                    .append(csv(row.getStatus())).append(',')
                    .append(csv(row.getHospital())).append(',')
                    .append(csv(row.getLocation())).append(',')
                    .append(csv(row.getFocus())).append('\n');
        }
        for (PregExpense row : expenses) {
            builder.append("expense,").append(row.getId()).append(',')
                    .append(csv(PregnancyTimeUtils.formatDate(row.getSpentDate()))).append(',')
                    .append(csv(row.getLabel())).append(',')
                    .append(csv(row.getAmount() == null ? "" : row.getAmount().toPlainString())).append(',')
                    .append(csv(row.getCategoryCode())).append(',')
                    .append(csv(row.getSourceType())).append(',')
                    .append(csv(null)).append('\n');
        }
        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * 写 XLSX
     *
     * @param dir          临时目录
     * @param measurements 测量
     * @param checkups     产检
     * @param expenses     费用
     * @return 数据文件
     */
    private Path writeXlsx(Path dir, List<PregMeasurement> measurements, List<PregCheckup> checkups,
                           List<PregExpense> expenses) {
        Path file = dir.resolve("pregnancy-data.xlsx");
        try (BigExcelWriter writer = ExcelUtil.getBigWriter(file.toFile(), "pregnancy-data")) {
            writer.writeRow(List.of("类型", "记录ID", "日期/时刻", "指标或名称", "数值", "单位", "场景", "备注"));
            for (PregMeasurement row : measurements) {
                writer.writeRow(List.of("measurement", String.valueOf(row.getId()),
                        row.getOccurredAt().toString(),
                        row.getMetricCode(),
                        row.getNumericValue() == null ? "" : row.getNumericValue().toPlainString(),
                        row.getUnitCode() == null ? "" : row.getUnitCode(),
                        row.getSceneCode() == null ? "" : row.getSceneCode(),
                        row.getNote() == null ? "" : row.getNote()));
            }
            for (PregCheckup row : checkups) {
                writer.writeRow(List.of("checkup", String.valueOf(row.getId()),
                        PregnancyTimeUtils.formatDate(row.getAppointmentDate()),
                        row.getTitle(), row.getStatus(),
                        row.getHospital() == null ? "" : row.getHospital(),
                        row.getLocation() == null ? "" : row.getLocation(),
                        row.getFocus() == null ? "" : row.getFocus()));
            }
            for (PregExpense row : expenses) {
                writer.writeRow(List.of("expense", String.valueOf(row.getId()),
                        PregnancyTimeUtils.formatDate(row.getSpentDate()),
                        row.getLabel() == null ? "" : row.getLabel(),
                        row.getAmount() == null ? "" : row.getAmount().toPlainString(),
                        row.getCategoryCode(), row.getSourceType(), ""));
            }
        }
        return file;
    }

    /**
     * 组装 manifest
     *
     * @param taskId   任务 ID
     * @param stage    阶段结果
     * @param dataFile 数据文件
     * @param entries  条目
     * @return JSON 文本
     */
    private String buildManifestJson(Long taskId, JSONObject stage, Path dataFile,
                                     List<Map<String, Object>> entries) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("taskId", String.valueOf(taskId));
        manifest.put("snapshotAt", stage.getString("snapshotAt"));
        manifest.put("buildDeadline", stage.getString("buildDeadline"));
        manifest.put("dataFile", dataFile.getFileName().toString());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            if ("data".equals(entry.get("kind"))) {
                items.add(entry);
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(entry);
            items.add(item);
        }
        manifest.put("items", items);
        return JSON.toJSONString(manifest);
    }

    /**
     * 读取申请者自己的任务
     *
     * @param id 任务 ID
     * @return 任务
     */
    private PregTask requireOwnTask(Long id) {
        PregTask task = taskMapper.selectById(id);
        if (task == null || !PregnancyConstants.TASK_EXPORT.equals(task.getKind())) {
            throw PregnancyException.notFound();
        }
        if (task.getRequesterUserId() == null
                || !task.getRequesterUserId().equals(accessGuard.currentUserId())) {
            // 其他账号一律按不存在处理，不能用已知 ID 绕过
            throw PregnancyException.notFound();
        }
        return task;
    }

    /**
     * 转换展示对象
     *
     * @param task 任务
     * @return 展示对象
     */
    private ExportVO toVO(PregTask task) {
        ExportVO vo = new ExportVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setStatus(task.getStatus());
        JSONObject stage = parseObject(task.getStageJson());
        vo.setStage(stage.getString("stage"));
        vo.setSnapshotAt(stage.getString("snapshotAt"));
        vo.setExpiresAt(PregnancyTimeUtils.formatOffset(task.getExpiresAt() == null
                ? null : task.getExpiresAt().toInstant()));
        Object size = stage.get("fileSize");
        vo.setFileSize(size == null ? null : String.valueOf(size));
        vo.setSummary(stage.getString("summary"));
        if (PregnancyConstants.TASK_FAILED.equals(task.getStatus())) {
            vo.setMessage("导出失败，可重新申请");
        } else if (PregnancyConstants.TASK_SUCCEEDED.equals(task.getStatus())) {
            vo.setContentUrl("/pregnancy/exports/" + task.getId() + "/content");
        }
        return vo;
    }

    /**
     * 计算文件 SHA-256
     *
     * @param file 文件
     * @return 摘要
     */
    private String sha256(Path file) {
        try {
            return cn.hutool.crypto.digest.DigestUtil.sha256Hex(Files.readAllBytes(file));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 解析 JSON 对象
     *
     * @param json JSON
     * @return 对象
     */
    private JSONObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject object = JSON.parseObject(json);
            return object == null ? new JSONObject() : object;
        } catch (RuntimeException e) {
            return new JSONObject();
        }
    }

    /**
     * CSV 字段转义
     *
     * @param value 值
     * @return 转义文本
     */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\r", " ").replace("\n", " ");
        if (text.contains(",") || text.contains("\"") || text.contains(";")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /**
     * 取扩展名
     *
     * @param mimeType MIME
     * @return 扩展名
     */
    private String extensionOf(String mimeType) {
        if (mimeType == null) {
            return ".bin";
        }
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/jpeg" -> ".jpg";
            default -> ".bin";
        };
    }
}
