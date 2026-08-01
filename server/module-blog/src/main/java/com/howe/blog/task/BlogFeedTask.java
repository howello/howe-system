package com.howe.blog.task;

import com.howe.blog.domain.vo.BlogFeedSyncResult;
import com.howe.blog.service.IBlogFeedSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 博客朋友圈 RSS 同步任务
 *
 * <p>由 quartz 通过 {@code sys_job.invoke_target = "blogFeedTask.sync()"} 反射调用，
 * bean 名必须与该配置一致。</p>
 *
 * <p>放在 module-blog 而不是 module-quartz：{@code JobInvokeUtil} 对不含包名的 target
 * 走 {@code SpringUtils.getBean(beanName)} 按运行时 bean 名解析，没有编译期依赖；
 * 放 module-quartz 反而要新增 module-quartz → module-blog 依赖，
 * 破坏该模块「只依赖 module-common」的现状。</p>
 *
 * @author howe
 */
@Slf4j
@Component("blogFeedTask")
@RequiredArgsConstructor
public class BlogFeedTask {

    private final IBlogFeedSyncService blogFeedSyncService;

    /**
     * 同步全部启用的订阅源
     */
    public void sync() {
        BlogFeedSyncResult result = blogFeedSyncService.syncAll();
        log.info("朋友圈同步完成：共{}个源，成功{}，失败{}，新增{}条",
                result.total(), result.success(), result.failed(), result.newItems());
    }
}
