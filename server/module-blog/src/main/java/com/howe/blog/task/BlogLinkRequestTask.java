package com.howe.blog.task;

import cn.hutool.core.util.StrUtil;
import com.howe.blog.service.WalineLinkSyncService;
import com.howe.common.task.TaskLogContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * waline 友链申请同步任务
 *
 * <p>由 quartz 通过 {@code sys_job.invoke_target = "blogLinkRequestTask.sync(30)"} 反射调用，
 * bean 名必须与该配置一致。</p>
 *
 * <p>参数类型必须是包装类 {@code Integer}（不能是 {@code int}），
 * 因为 {@code JobInvokeUtil.getMethod} 按 {@code Integer.class} 精确匹配。</p>
 *
 * @author howe
 */
@Slf4j
@Component("blogLinkRequestTask")
@RequiredArgsConstructor
public class BlogLinkRequestTask {

    private final WalineLinkSyncService walineLinkSyncService;

    /**
     * 同步友链申请留言
     *
     * @param windowMinutes 时间窗口（分钟），由 quartz invoke_target 参数传入
     */
    public void sync(Integer windowMinutes) {
        try (TaskLogContext.TaskStep step = TaskLogContext.startStep("开始同步 waline 友链申请留言，窗口={}分钟", windowMinutes)){
            WalineLinkSyncService.WalineLinkSyncResult result = walineLinkSyncService.sync(windowMinutes);
            step.success(StrUtil.format("waline 友链同步完成：新增{}条，跳过{}条，翻{}页",
                result.newCount(), result.skipCount(), result.pageCount()));
        }
    }
}
