package com.howe.common.notify.wecom;

import com.howe.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/** 通过 App 自己的机器人配置发送新版本通知。 */
@Slf4j
@Component
public class AppReleaseWeComNotifier {

    private static final int UPDATE_LOG_LIMIT = 300;

    private final AppReleaseWebhookProvider webhookProvider;
    private final WeComRobotClient robotClient;
    private final ObjectProvider<Executor> executorProvider;

    /** 注入发版配置和机器人客户端，未配置线程池时直接执行发送任务。 */
    public AppReleaseWeComNotifier(AppReleaseWebhookProvider webhookProvider, WeComRobotClient robotClient,
            @Qualifier("threadPoolTaskExecutor") ObjectProvider<Executor> executorProvider) {
        this.webhookProvider = webhookProvider;
        this.robotClient = robotClient;
        this.executorProvider = executorProvider;
    }

    /** 在版本发布提交后调用，向该 App 已启用的目标群发送一条版本通知。 */
    public void notifyPublished(String appKey, String appName, String versionName, Integer versionCode,
            String updateLog, boolean forceUpdate) {
        Runnable task = () -> send(appKey, appName, versionName, versionCode, updateLog, forceUpdate);
        try {
            executorProvider.getIfAvailable(() -> Runnable::run).execute(task);
        } catch (RuntimeException e) {
            log.warn("提交 App 发版通知任务失败，改为同步发送：appKey={}, versionCode={}", appKey, versionCode, e);
            task.run();
        }
    }

    /** 查询已启用的群；单群失败后继续处理其他群。 */
    private void send(String appKey, String appName, String versionName, Integer versionCode,
            String updateLog, boolean forceUpdate) {
        try {
            String content = buildContent(appName, versionName, versionCode, updateLog, forceUpdate);
            for (String key : webhookProvider.findEnabledKeys(appKey)) {
                try {
                    WeComRobotResponse response = robotClient.sendText(key, content);
                    if (!response.isSuccess()) {
                        log.warn("App 发版通知发送失败：appKey={}, versionCode={}, errcode={}",
                                appKey, versionCode, response.getErrcode());
                    }
                } catch (RuntimeException e) {
                    log.warn("App 发版通知发送异常：appKey={}, versionCode={}, error={}",
                            appKey, versionCode, e.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException e) {
            log.warn("准备 App 发版通知失败：appKey={}, versionCode={}", appKey, versionCode, e);
        }
    }

    /** 组装包含应用、版本、更新说明和强制更新状态的文案。 */
    private String buildContent(String appName, String versionName, Integer versionCode, String updateLog,
            boolean forceUpdate) {
        StringBuilder text = new StringBuilder("🎉 ").append(appName).append(" App 有新版本啦！\n\n");
        text.append("版本：v").append(StringUtils.defaultIfBlank(versionName, "-"))
                .append(" (").append(versionCode == null ? "-" : versionCode).append(")\n");
        if (StringUtils.isNotBlank(updateLog)) {
            text.append("更新说明：\n").append(StringUtils.abbreviate(updateLog.trim(), "…", UPDATE_LOG_LIMIT + 1))
                    .append('\n');
        }
        return text.append(forceUpdate ? "\n⚠️ 本次为强制更新，需要更新后才能继续使用。"
                : "\n打开 App 即可收到更新提示。").toString();
    }
}
