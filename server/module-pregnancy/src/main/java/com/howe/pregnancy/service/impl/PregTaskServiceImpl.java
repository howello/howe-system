package com.howe.pregnancy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.AiClient;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiImage;
import com.howe.ai.api.dto.AiImageRequest;
import com.howe.ai.api.dto.AiImageResult;
import com.howe.common.utils.ConfigUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.pregnancy.domain.PregBagItem;
import com.howe.pregnancy.domain.PregCheckup;
import com.howe.pregnancy.domain.PregTask;
import com.howe.pregnancy.domain.vo.TaskVO;
import com.howe.pregnancy.mapper.PregBagMapper;
import com.howe.pregnancy.mapper.PregCheckupMapper;
import com.howe.pregnancy.mapper.PregTaskMapper;
import com.howe.pregnancy.service.IPregTaskService;
import com.howe.pregnancy.util.PregnancyAccessGuard;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyException;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import com.howe.pregnancy.util.PregnancySeedProvider;
import com.howe.pregnancy.util.RequestHashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 后台任务服务实现
 *
 * <p>提醒、物品 AI 内容与导出共用一张任务表。扫描线程按 {@code status + next_run_at}
 * 领取，领取与提交都在短事务内完成并核对租约令牌；外部调用在事务外执行。
 * 企微结果不明记 {@code UNKNOWN}，不自动重发。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class PregTaskServiceImpl implements IPregTaskService {

    /** 单次扫描领取上限 */
    private static final int CLAIM_BATCH = 5;

    /** 扫描间隔（毫秒） */
    private static final long DISPATCH_INTERVAL = 15_000L;

    /** 恢复扫描间隔（毫秒） */
    private static final long RECOVER_INTERVAL = 60_000L;

    /** 退避秒数 */
    private static final long[] BACKOFF_SECONDS = {60L, 300L};

    private final PregTaskMapper taskMapper;

    private final PregBagMapper bagMapper;

    private final PregCheckupMapper checkupMapper;

    private final PregnancyAccessGuard accessGuard;

    private final AiClient aiClient;

    private final PregnancySeedProvider seedProvider;

    private final com.howe.pregnancy.service.IPregExportService exportService;

    /** 孕期任务专用执行器：容器里有多个 Executor，必须按名字取 */
    private final Executor executor;

    /**
     * 显式构造器
     *
     * <p>容器中存在多个 {@link Executor} bean（框架线程池、AI 执行器、分享缓存执行器等），
     * 必须用 {@link Qualifier} 指定孕期专用的有界执行器，否则启动时注入歧义。</p>
     *
     * @param taskMapper   任务数据层
     * @param bagMapper    待产包数据层
     * @param checkupMapper 产检数据层
     * @param accessGuard  访问守卫
     * @param aiClient     AI 网关
     * @param seedProvider 物品介绍种子
     * @param exportService 导出服务
     * @param executor     孕期任务执行器
     */
    public PregTaskServiceImpl(PregTaskMapper taskMapper,
                               PregBagMapper bagMapper,
                               PregCheckupMapper checkupMapper,
                               PregnancyAccessGuard accessGuard,
                               AiClient aiClient,
                               PregnancySeedProvider seedProvider,
                               com.howe.pregnancy.service.IPregExportService exportService,
                               @Qualifier("pregnancyTaskExecutor") Executor executor) {
        this.taskMapper = taskMapper;
        this.bagMapper = bagMapper;
        this.checkupMapper = checkupMapper;
        this.accessGuard = accessGuard;
        this.aiClient = aiClient;
        this.seedProvider = seedProvider;
        this.exportService = exportService;
        this.executor = executor;
    }

    @Override
    public TaskVO getTask(Long taskId) {
        PregTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw PregnancyException.notFound();
        }
        assertTaskVisible(task);
        return toVO(task);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TaskVO generateItemContent(Long itemId, Integer version, String requestId) {
        accessGuard.requireWritable();
        PregBagItem item = bagMapper.selectItemByIdForUpdate(itemId);
        if (item == null) {
            throw PregnancyException.notFound();
        }
        if (version == null) {
            throw PregnancyException.versionConflict();
        }
        if (!version.equals(item.getVersion())) {
            throw PregnancyException.versionConflict();
        }
        String dedup = "ITEM_CONTENT:" + itemId + ":" + (requestId == null ? UUID.randomUUID() : requestId);
        PregTask existing = taskMapper.selectByDedupKey(dedup);
        if (existing != null) {
            return toVO(existing);
        }
        if (seedProvider.bagIntroduction(item.getCategory(), item.getName()) != null) {
            throw PregnancyException.invalid("该物品已有清单介绍");
        }
        if (item.getCurrentContentTaskId() != null) {
            PregTask active = taskMapper.selectById(item.getCurrentContentTaskId());
            if (active != null && item.getVersion().equals(active.getBusinessVersion())
                    && (PregnancyConstants.TASK_PENDING.equals(active.getStatus())
                    || PregnancyConstants.TASK_RUNNING.equals(active.getStatus())
                    || PregnancyConstants.TASK_RETRY_WAIT.equals(active.getStatus()))) {
                return toVO(active);
            }
        }
        PregTask task = new PregTask();
        task.setKind(PregnancyConstants.TASK_ITEM_CONTENT);
        task.setBusinessId(itemId);
        task.setBusinessVersion(item.getVersion());
        task.setRequesterUserId(accessGuard.currentUserId());
        task.setDedupKey(dedup);
        JSONObject payload = new JSONObject();
        payload.put("name", item.getName());
        payload.put("purpose", item.getPurpose());
        payload.put("category", item.getCategory());
        task.setPayloadJson(payload.toJSONString());
        task.setStageJson("{}");
        task.setStatus(PregnancyConstants.TASK_PENDING);
        task.setAttempt(0);
        task.setMaxAttempts(PregnancyConfig.taskMaxAttempts());
        task.setNextRunAt(PregnancyTimeUtils.now());
        taskMapper.insert(task);
        // 只有当前任务可以写回物品内容
        bagMapper.updateCurrentTask(itemId, task.getId());
        return toVO(taskMapper.selectById(task.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO retryItemContent(Long itemId) {
        accessGuard.requireWritable();
        PregBagItem item = bagMapper.selectItemById(itemId);
        if (item == null) {
            throw PregnancyException.notFound();
        }
        PregTask task = taskMapper.selectLatestByBusiness(PregnancyConstants.TASK_ITEM_CONTENT, itemId);
        if (task == null) {
            throw PregnancyException.invalidState("当前没有可重试的生成任务");
        }
        if (PregnancyConstants.TASK_UNKNOWN.equals(task.getStatus())) {
            throw PregnancyException.invalidState("结果不明的任务不能重试，请重新生成");
        }
        if (!item.getId().equals(task.getBusinessId())
                || (item.getCurrentContentTaskId() != null
                && !item.getCurrentContentTaskId().equals(task.getId()))) {
            throw PregnancyException.invalidState("当前任务已被替换，请重新生成");
        }
        // 显式重试会开启新一轮受限尝试：累计 attempt 不归零，上限调整为当前累计次数加一轮
        int attempt = task.getAttempt() == null ? 0 : task.getAttempt();
        int maxAttempts = attempt + PregnancyConfig.taskMaxAttempts();
        taskMapper.reschedulePending(task.getId(), task.getBusinessVersion(), null,
                PregnancyTimeUtils.now().toLocalDateTime());
        // 上限提高后，原本已耗尽的任务重新可被领取
        taskMapper.extendAttempts(task.getId(), maxAttempts);
        taskMapper.updateStatus(task.getId(), PregnancyConstants.TASK_PENDING, null,
                PregnancyTimeUtils.now().toLocalDateTime());
        return toVO(taskMapper.selectById(task.getId()));
    }

    @Override
    public String contentState(Long taskId) {
        if (taskId == null) {
            return "NONE";
        }
        PregTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return "NONE";
        }
        return task.getStatus();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByBusiness(String kind, Long businessId) {
        taskMapper.cancelByBusiness(kind, businessId, PregnancyTimeUtils.now().toLocalDateTime());
    }

    @Override
    @Scheduled(fixedDelay = DISPATCH_INTERVAL, initialDelay = 20_000L)
    public void dispatchDueTasks() {
        try {
            expireFinishedExports();
        } catch (RuntimeException e) {
            log.warn("孕期导出过期扫描失败", e);
        }
        List<Long> ids;
        try {
            ids = taskMapper.selectDueIds(PregnancyTimeUtils.now().toLocalDateTime(), CLAIM_BATCH);
        } catch (RuntimeException e) {
            log.warn("孕期任务扫描失败", e);
            return;
        }
        for (Long id : ids) {
            String leaseToken = UUID.randomUUID().toString();
            OffsetDateTime leaseUntil = PregnancyTimeUtils.now()
                    .plusMinutes(PregnancyConfig.taskLeaseMinutes());
            if (taskMapper.claim(id, leaseToken, leaseUntil.toLocalDateTime(),
                    PregnancyTimeUtils.now().toLocalDateTime()) == 0) {
                continue;
            }
            executor.execute(() -> runTask(id, leaseToken));
        }
    }

    @Override
    @Scheduled(fixedDelay = RECOVER_INTERVAL, initialDelay = 45_000L)
    public void recoverExpiredTasks() {
        List<PregTask> expired;
        try {
            expired = taskMapper.selectExpiredRunning(PregnancyTimeUtils.now().toLocalDateTime(), 20);
        } catch (RuntimeException e) {
            log.warn("孕期任务恢复扫描失败", e);
            return;
        }
        for (PregTask task : expired) {
            try {
                recoverOne(task);
            } catch (RuntimeException e) {
                log.warn("恢复孕期任务失败：id={}", task.getId(), e);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleReminder(String dedupKey, Long businessId, Integer businessVersion,
                                 String payloadJson, OffsetDateTime executeAt) {
        PregTask existing = taskMapper.selectByDedupKey(dedupKey);
        if (existing != null) {
            // 已发送、运行中或结果不明的发生标识保留发送保护
            if (PregnancyConstants.TASK_SUCCEEDED.equals(existing.getStatus())
                    || PregnancyConstants.TASK_RUNNING.equals(existing.getStatus())
                    || PregnancyConstants.TASK_UNKNOWN.equals(existing.getStatus())) {
                return;
            }
            if (PregnancyConstants.TASK_PENDING.equals(existing.getStatus())
                    || PregnancyConstants.TASK_RETRY_WAIT.equals(existing.getStatus())) {
                taskMapper.reschedulePending(existing.getId(), businessVersion, payloadJson,
                        executeAt.toLocalDateTime());
            }
            return;
        }
        PregTask task = new PregTask();
        task.setKind(PregnancyConstants.TASK_REMINDER);
        task.setBusinessId(businessId);
        task.setBusinessVersion(businessVersion);
        task.setRequesterUserId(null);
        task.setDedupKey(dedupKey);
        task.setPayloadJson(payloadJson);
        task.setStageJson("{}");
        task.setStatus(PregnancyConstants.TASK_PENDING);
        task.setAttempt(0);
        task.setMaxAttempts(PregnancyConfig.taskMaxAttempts());
        task.setNextRunAt(executeAt);
        taskMapper.insert(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRemindersFor(Long checkupId) {
        taskMapper.cancelByBusiness(PregnancyConstants.TASK_REMINDER, checkupId,
                PregnancyTimeUtils.now().toLocalDateTime());
    }

    @Override
    public TaskVO latestReminder() {
        accessGuard.requireManager();
        PregTask task = taskMapper.selectLatestReminder();
        return task == null ? null : toVO(task);
    }

    /**
     * 执行单个任务
     *
     * @param taskId     任务 ID
     * @param leaseToken 租约令牌
     */
    private void runTask(Long taskId, String leaseToken) {
        PregTask task = taskMapper.selectById(taskId);
        if (task == null || !PregnancyConstants.TASK_RUNNING.equals(task.getStatus())
                || !leaseToken.equals(task.getLeaseToken())) {
            return;
        }
        try {
            switch (task.getKind()) {
                case PregnancyConstants.TASK_ITEM_CONTENT -> runItemContent(task, leaseToken);
                case PregnancyConstants.TASK_REMINDER -> runReminder(task, leaseToken);
                case PregnancyConstants.TASK_EXPORT -> runExport(task, leaseToken);
                default -> taskMapper.complete(taskId, leaseToken, PregnancyConstants.TASK_SKIPPED,
                        task.getStageJson(), null, null, null, PregnancyTimeUtils.now().toLocalDateTime());
            }
        } catch (Exception e) {
            log.error("孕期任务执行失败：id={}, kind={}", taskId, task.getKind(), e);
            failTask(task, leaseToken, "INTERNAL_ERROR", e.getMessage(), null);
        }
    }

    /**
     * 执行物品 AI 内容任务
     *
     * <p>文本与图片分阶段保存；AI 不可用只记失败，不回滚物品本身。</p>
     *
     * @param task       任务
     * @param leaseToken 租约令牌
     */
    private void runItemContent(PregTask task, String leaseToken) {
        PregBagItem item = bagMapper.selectItemById(task.getBusinessId());
        if (item == null || item.getCurrentContentTaskId() == null
                || !item.getCurrentContentTaskId().equals(task.getId())) {
            finishSkipped(task, leaseToken, "ITEM_REPLACED");
            return;
        }
        JSONObject stage = parseObject(task.getStageJson());
        String text = stage.getString("text");
        String imageKey = stage.getString("imageKey");

        if (text == null) {
            if (!isAiConfigured()) {
                failTask(task, leaseToken, "AI_DISABLED", "AI 未启用", null);
                return;
            }
            try {
                String prompt = "请用 3 句话介绍待产物品「" + item.getName() + "」"
                        + (item.getPurpose() == null ? "" : "（用途：" + item.getPurpose() + "）")
                        + "，只写一般性说明，不提供医疗建议。";
                AiChatRequest request = AiChatRequest.builder()
                        .scene("pregnancy.bag.item")
                        .prompt(prompt)
                        .build();
                AiChatResult result = aiClient.chat(request);
                text = result == null || result.getText() == null ? null : result.getText().trim();
                if (text == null || text.isEmpty()) {
                    failTask(task, leaseToken, "AI_EMPTY", "AI 未返回内容", null);
                    return;
                }
                stage.put("text", text);
                touchStage(task, leaseToken, stage);
            } catch (RuntimeException e) {
                failTask(task, leaseToken, "AI_TEXT_FAILED", "AI 文本生成失败", null);
                return;
            }
        }

        if (imageKey == null) {
            try {
                AiImageRequest request = AiImageRequest.builder()
                        .scene("pregnancy.bag.item")
                        .prompt("待产物品示意配图：" + item.getName() + "，简洁干净的实物风格")
                        .size("1024*1024")
                        .build();
                AiImageResult result = aiClient.image(request);
                String url = null;
                String key = null;
                if (result != null && result.getImages() != null && !result.getImages().isEmpty()) {
                    AiImage first = result.getImages().get(0);
                    if (first != null) {
                        url = first.getUrl();
                        key = first.getKey();
                    }
                }
                if (url != null || key != null) {
                    imageKey = key != null ? key : url;
                    stage.put("imageKey", imageKey);
                    touchStage(task, leaseToken, stage);
                } else {
                    failTask(task, leaseToken, "AI_IMAGE_FAILED", "AI 配图生成失败", stage.toJSONString());
                    return;
                }
            } catch (RuntimeException e) {
                failTask(task, leaseToken, "AI_IMAGE_FAILED", "AI 配图生成失败", stage.toJSONString());
                return;
            }
        }

        // 回写前再次核对当前任务，旧任务不得覆盖新内容
        if (bagMapper.updateAiContent(item.getId(), task.getId(), text, imageKey) == 0) {
            finishSkipped(task, leaseToken, "ITEM_REPLACED");
            return;
        }
        taskMapper.complete(task.getId(), leaseToken, PregnancyConstants.TASK_SUCCEEDED,
                stage.toJSONString(), null, null, null, PregnancyTimeUtils.now().toLocalDateTime());
    }

    /**
     * 执行提醒任务
     *
     * <p>发送前核对业务版本与预约状态；晚到超过窗口记 SKIPPED；结果不明转 UNKNOWN。</p>
     *
     * @param task       任务
     * @param leaseToken 租约令牌
     */
    private void runReminder(PregTask task, String leaseToken) {
        PregCheckup checkup = checkupMapper.selectById(task.getBusinessId());
        if (checkup == null) {
            finishSkipped(task, leaseToken, "CHECKUP_MISSING");
            return;
        }
        if (!PregnancyConstants.CHECKUP_BOOKED.equals(checkup.getStatus())
                && !PregnancyConstants.CHECKUP_PLANNED.equals(checkup.getStatus())) {
            finishSkipped(task, leaseToken, "CHECKUP_INACTIVE");
            return;
        }
        if (task.getBusinessVersion() != null && !task.getBusinessVersion().equals(checkup.getVersion())) {
            finishSkipped(task, leaseToken, "CHECKUP_VERSION_CHANGED");
            return;
        }
        OffsetDateTime now = PregnancyTimeUtils.now();
        if (task.getNextRunAt() != null
                && task.getNextRunAt().plusMinutes(PregnancyConfig.reminderLateMinutes()).isBefore(now)) {
            finishSkipped(task, leaseToken, "REMINDER_LATE");
            return;
        }
        String webhookKey = PregnancyConfig.wecomKey();
        if (webhookKey == null || webhookKey.isBlank() || !PregnancyConfig.wecomEnabled()) {
            finishSkipped(task, leaseToken, "WECOM_DISABLED");
            return;
        }
        String content = buildReminderText(checkup);
        JSONObject stage = parseObject(task.getStageJson());
        stage.put("stage", "SEND");
        touchStage(task, leaseToken, stage);

        var response = weComClient().sendText(webhookKey, content);
        if (response != null && response.isSuccess()) {
            JSONObject done = parseObject(task.getStageJson());
            done.put("stage", "SENT");
            taskMapper.complete(task.getId(), leaseToken, PregnancyConstants.TASK_SUCCEEDED,
                    done.toJSONString(), null, null, now.toLocalDateTime(),
                    PregnancyTimeUtils.now().toLocalDateTime());
            return;
        }
        // 无法区分未送达与结果不明时按 UNKNOWN 处理，不自动重发
        String error = response == null ? "无响应" : response.getErrmsg();
        boolean definiteFailure = error != null && (error.contains("频率") || error.contains("超过限制"));
        if (definiteFailure) {
            failTask(task, leaseToken, "WECOM_RATE_LIMITED", error, null);
        } else {
            taskMapper.complete(task.getId(), leaseToken, PregnancyConstants.TASK_UNKNOWN,
                    task.getStageJson(), null, null, null, PregnancyTimeUtils.now().toLocalDateTime());
        }
    }

    /**
     * 执行导出任务（实现由导出服务提供，此处保留统一分派点）
     *
     * @param task       任务
     * @param leaseToken 租约令牌
     */
    private void runExport(PregTask task, String leaseToken) {
        // 导出服务的返回值只表示结果是否已由它落库；租约丢失时保持原状，等恢复扫描处理
        exportService.build(task.getId(), leaseToken);
    }

    /**
     * 标记任务失败并按退避安排重试
     *
     * @param task       任务
     * @param leaseToken 租约令牌
     * @param errorCode  错误码
     * @param message    可读错误
     * @param stageJson  阶段结果
     */
    private void failTask(PregTask task, String leaseToken, String errorCode, String message,
                          String stageJson) {
        int attempt = task.getAttempt() == null ? 1 : task.getAttempt();
        int maxAttempts = task.getMaxAttempts() == null
                ? PregnancyConfig.taskMaxAttempts() : task.getMaxAttempts();
        LocalDateTime now = PregnancyTimeUtils.now().toLocalDateTime();
        if (attempt >= maxAttempts) {
            taskMapper.fail(task.getId(), leaseToken, PregnancyConstants.TASK_FAILED,
                    errorCode, attempt, maxAttempts,
                    now, stageJson, now);
            return;
        }
        long backoff = BACKOFF_SECONDS[Math.min(attempt - 1, BACKOFF_SECONDS.length - 1)];
        taskMapper.fail(task.getId(), leaseToken, PregnancyConstants.TASK_RETRY_WAIT,
                errorCode, attempt, maxAttempts,
                now.plusSeconds(backoff), stageJson, now);
    }

    /**
     * 标记任务跳过
     *
     * @param task       任务
     * @param leaseToken 租约令牌
     * @param errorCode  原因
     */
    private void finishSkipped(PregTask task, String leaseToken, String errorCode) {
        taskMapper.complete(task.getId(), leaseToken, PregnancyConstants.TASK_SKIPPED,
                task.getStageJson(), null, null, null, PregnancyTimeUtils.now().toLocalDateTime());
    }

    /**
     * 保存阶段结果（保持租约）
     *
     * @param task       任务
     * @param leaseToken 租约令牌
     * @param stage      阶段结果
     */
    private void touchStage(PregTask task, String leaseToken, JSONObject stage) {
        int renewed = taskMapper.renewLease(task.getId(), leaseToken,
                PregnancyTimeUtils.now().plusMinutes(PregnancyConfig.taskLeaseMinutes()).toLocalDateTime(),
                PregnancyTimeUtils.now().toLocalDateTime());
        if (renewed == 0) {
            throw new IllegalStateException("任务租约已失效");
        }
        // 阶段结果随任务保存，供中断后恢复；状态与租约保持不变
        if (taskMapper.updateStage(task.getId(), leaseToken, stage.toJSONString(),
                PregnancyTimeUtils.now().toLocalDateTime()) == 0) {
            throw new IllegalStateException("任务租约已失效");
        }
        task.setStageJson(stage.toJSONString());
    }

    /**
     * 恢复过期任务
     *
     * @param task 任务
     */
    private void recoverOne(PregTask task) {
        JSONObject stage = parseObject(task.getStageJson());
        String target;
        if (PregnancyConstants.TASK_REMINDER.equals(task.getKind())
                && "SEND".equals(stage.getString("stage"))) {
            // 发送中的租约过期且无明确回执，按 UNKNOWN 处理
            target = PregnancyConstants.TASK_UNKNOWN;
        } else if (PregnancyConstants.TASK_REMINDER.equals(task.getKind())) {
            target = PregnancyConstants.TASK_RETRY_WAIT;
        } else {
            target = task.getAttempt() != null && task.getAttempt() >= (task.getMaxAttempts() == null
                    ? PregnancyConfig.taskMaxAttempts() : task.getMaxAttempts())
                    ? PregnancyConstants.TASK_FAILED : PregnancyConstants.TASK_RETRY_WAIT;
        }
        taskMapper.recoverExpired(task.getId(), task.getLeaseToken(), target,
                "LEASE_EXPIRED", PregnancyTimeUtils.now().toLocalDateTime());
    }

    /**
     * 标记过期的导出结果
     */
    private void expireFinishedExports() {
        taskMapper.expireFinishedExports(PregnancyTimeUtils.now().toLocalDateTime());
    }

    /**
     * 组装提醒文案
     *
     * @param checkup 产检
     * @return 文案
     */
    private String buildReminderText(PregCheckup checkup) {
        StringBuilder builder = new StringBuilder("【孕期待产提醒】\n");
        builder.append(checkup.getTitle());
        if (checkup.getAppointmentDate() != null) {
            builder.append("\n预约日期：").append(PregnancyTimeUtils.formatDate(checkup.getAppointmentDate()));
            if (checkup.getAppointmentTime() != null) {
                builder.append(" ").append(checkup.getAppointmentTime());
            }
        }
        if (checkup.getHospital() != null) {
            builder.append("\n医院：").append(checkup.getHospital());
        }
        if (checkup.getLocation() != null) {
            builder.append("\n地点：").append(checkup.getLocation());
        }
        List<com.howe.pregnancy.domain.PregCheckupDetail> preparations =
                checkupMapper.selectDetails(checkup.getId());
        List<String> pending = preparations.stream()
                .filter(item -> PregnancyConstants.DETAIL_PREPARATION.equals(item.getType()))
                .filter(item -> item.getDone() == null || !item.getDone())
                .map(com.howe.pregnancy.domain.PregCheckupDetail::getTitle)
                .toList();
        if (!pending.isEmpty()) {
            builder.append("\n待准备：").append(String.join("、", pending));
        }
        builder.append("\n请登录「宝宝」查看详情。");
        return builder.toString();
    }

    /**
     * 判断 AI 是否可用
     *
     * @return 可用返回 true
     */
    private boolean isAiConfigured() {
        return aiClient != null;
    }

    /**
     * 取企微客户端（延迟解析，避免循环依赖）
     *
     * @return 客户端
     */
    private com.howe.common.notify.wecom.WeComRobotClient weComClient() {
        return com.howe.common.utils.spring.SpringUtils.getBean(com.howe.common.notify.wecom.WeComRobotClient.class);
    }

    /**
     * 判断任务对当前账号可见
     *
     * @param task 任务
     */
    private void assertTaskVisible(PregTask task) {
        if (PregnancyConstants.TASK_EXPORT.equals(task.getKind())) {
            if (task.getRequesterUserId() == null
                    || !task.getRequesterUserId().equals(accessGuard.currentUserId())) {
                throw PregnancyException.notFound();
            }
            return;
        }
        if (PregnancyConstants.TASK_REMINDER.equals(task.getKind())) {
            accessGuard.requireManager();
            return;
        }
        accessGuard.requireReadable();
    }

    /**
     * 解析阶段 JSON
     *
     * @param json JSON 文本
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
     * 转换任务摘要
     *
     * @param task 任务
     * @return 摘要
     */
    private TaskVO toVO(PregTask task) {
        TaskVO vo = new TaskVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setKind(task.getKind());
        vo.setStatus(task.getStatus());
        vo.setAttempt(task.getAttempt());
        vo.setNextRetryAt(PregnancyTimeUtils.formatOffset(task.getNextRunAt() == null
                ? null : task.getNextRunAt().toInstant()));
        vo.setResultRef(task.getResultRef());
        vo.setExpiresAt(PregnancyTimeUtils.formatOffset(task.getExpiresAt() == null
                ? null : task.getExpiresAt().toInstant()));
        vo.setErrorCode(task.getErrorCode());
        JSONObject stage = parseObject(task.getStageJson());
        vo.setStage(stage.getString("stage"));
        vo.setText(stage.getString("text"));
        vo.setImageUrl(stage.getString("imageKey"));
        if (PregnancyConstants.TASK_FAILED.equals(task.getStatus())) {
            vo.setMessage("任务失败，可重试");
        } else if (PregnancyConstants.TASK_UNKNOWN.equals(task.getStatus())) {
            vo.setMessage("发送结果不明，请勿重复提交");
        }
        return vo;
    }

    /**
     * 请求摘要辅助（保持工具类引用）
     *
     * @param task 任务
     * @return 摘要
     */
    private String fingerprint(PregTask task) {
        return RequestHashUtils.hash(task.getKind(), task.getBusinessId(), task.getDedupKey());
    }

    /**
     * 未使用配置读取（供任务扩展）
     *
     * @param key 配置键
     * @return 值
     */
    private String config(String key) {
        return ConfigUtils.getString(key);
    }
}
