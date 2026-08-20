package com.howe.automation.task;

import com.howe.common.constant.ConfigConstants;
import com.howe.common.task.TaskLogContext;
import com.howe.common.task.TaskLogContext.TaskStep;
import com.howe.common.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 浏览器全局 profile 定时清理任务。
 *
 * <p>通过现有 {@code sys_job} 调度，周期 30 天，到点删除整个全局持久 profile 目录。
 * 不做当前状态判断、不依赖 profile 存活时长、不记录创建时间戳。清理后 profile 为空，
 * 下一次浏览器任务按「未登录 → 凭据自动登录 → 无凭据则 NEEDS_AUTH 失败」走重登链路。</p>
 */
@Slf4j
@Component("browserProfileCleanTask")
public class BrowserProfileCleanTask {

    /**
     * 删除全局浏览器 profile 目录。Quartz 通过 invoke_target 调用（无参方法）。
     */
    public void clean() {
        try (TaskStep step = TaskLogContext.startStep("清理浏览器全局 profile")) {
            String dir = ConfigUtils.getString(ConfigConstants.AUTOMATION_BROWSER_PROFILE_DIR);
            if (dir == null || dir.isBlank()) {
                step.skipped("未配置浏览器 profile 目录，跳过清理");
                return;
            }
            Path profileDir = Paths.get(dir).toAbsolutePath().normalize();
            if (!Files.exists(profileDir)) {
                step.skipped("浏览器 profile 目录不存在，无需清理：" + profileDir);
                return;
            }
            try {
                deleteRecursively(profileDir);
                step.success("已删除浏览器 profile 目录：" + profileDir);
            } catch (Exception e) {
                step.fail("清理浏览器 profile 目录失败：" + profileDir, e);
                throw new IllegalStateException("清理浏览器 profile 目录失败：" + profileDir, e);
            }
        }
    }

    /**
     * 递归删除目录（含内容）。文件按深度优先顺序删除，避免只删父目录残留子项。
     */
    private void deleteRecursively(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new CleanupException(path, e);
                }
            });
        }
    }

    private static final class CleanupException extends RuntimeException {
        private CleanupException(Path path, Throwable cause) {
            super("无法删除：" + path, cause);
        }
    }
}