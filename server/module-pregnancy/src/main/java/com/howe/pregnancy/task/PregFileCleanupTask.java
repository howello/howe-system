package com.howe.pregnancy.task;

import com.howe.pregnancy.domain.PregFile;
import com.howe.pregnancy.mapper.PregFileMapper;
import com.howe.pregnancy.storage.PrivateFileStorage;
import com.howe.pregnancy.util.PregnancyConfig;
import com.howe.pregnancy.util.PregnancyTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 私有原图清理任务
 *
 * <p>先在短事务内锁行领取并转为 {@code DELETING}，事务提交后再删除磁盘字节；
 * 进程中断后下一轮会继续处理仍为 DELETING 的文件。TEMP 与 ORPHAN 的保留期
 * 由配置控制，默认 24 小时。</p>
 *
 * <p><b>事务边界：</b>领取与删除必须分两步，避免磁盘 IO 持有数据库事务；
 * 因此本类把「事务内领取」与「事务外删字节」拆成两个组件，
 * 通过代理调用保证 {@code @Transactional} 真正生效（同类内部调用会绕过代理）。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PregFileCleanupTask {

    /** 单次领取上限 */
    private static final int BATCH = 20;

    private final PregFileMapper fileMapper;

    private final PrivateFileStorage storage;

    private final PregFileReclaimer reclaimer;

    /**
     * 清理过期文件
     */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void cleanup() {
        try {
            List<PregFile> claimed = reclaimer.claimExpired(BATCH);
            for (PregFile file : claimed) {
                // 事务已提交到 DELETING；删除失败时下一轮由 DELETING 续跑
                storage.delete(file.getStorageKey());
                reclaimer.release(file.getId());
            }
        } catch (RuntimeException e) {
            log.warn("孕期原图清理失败", e);
        }
    }

    /**
     * 继续处理中断后仍为 DELETING 的文件
     */
    @Scheduled(fixedDelay = 900_000L, initialDelay = 300_000L)
    public void resumeDeleting() {
        try {
            List<PregFile> pending = reclaimer.listDeleting(BATCH);
            for (PregFile file : pending) {
                storage.delete(file.getStorageKey());
                reclaimer.release(file.getId());
            }
            verifyRegistrations();
        } catch (RuntimeException e) {
            log.warn("孕期私有文件续清理失败", e);
        }
    }

    /**
     * 巡检登记行与磁盘字节的一致性
     *
     * <p>只报告缺失，不删除未登记文件，避免误删正在写入的暂存。</p>
     */
    private void verifyRegistrations() {
        List<String> keys = fileMapper.selectAllStorageKeys();
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!storage.exists(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            log.warn("孕期私有文件中有 {} 个登记行缺少磁盘字节，请核对", missing.size());
        }
    }

    /**
     * 事务内领取组件
     *
     * <p>独立 Bean，确保 {@code @Transactional} 经由 Spring 代理执行；
     * 领取后立即提交，磁盘删除在事务外进行。</p>
     */
    @Component
    @RequiredArgsConstructor
    public static class PregFileReclaimer {

        private final PregFileMapper fileMapper;

        /**
         * 在事务内领取过期文件并标记为 DELETING
         *
         * @param batch 单次上限
         * @return 已领取的文件
         */
        @Transactional(rollbackFor = Exception.class)
        public List<PregFile> claimExpired(int batch) {
            LocalDateTime cutoff = PregnancyTimeUtils.now()
                    .minusHours(PregnancyConfig.fileRetainHours()).toLocalDateTime();
            List<PregFile> candidates = fileMapper.claimReclaimable(cutoff, batch);
            List<PregFile> claimed = new ArrayList<>();
            for (PregFile file : candidates) {
                if (fileMapper.markDeleting(file.getId()) > 0) {
                    claimed.add(file);
                }
            }
            return claimed;
        }

        /**
         * 列出已处于 DELETING 但尚未清理的文件
         *
         * @param batch 单次上限
         * @return 文件列表
         */
        public List<PregFile> listDeleting(int batch) {
            return fileMapper.selectByState(com.howe.pregnancy.constant.PregnancyConstants.FILE_DELETING, batch);
        }

        /**
         * 删除登记行
         *
         * @param id 文件 ID
         */
        @Transactional(rollbackFor = Exception.class)
        public void release(Long id) {
            fileMapper.deleteById(id);
        }
    }
}
