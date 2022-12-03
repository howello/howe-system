package com.howe.main.listener;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/30 16:26 星期三
 * <p>@Version 1.0
 * <p>@Description 服务启动成功回调
 */
@Component
public class AppStartedListener implements ApplicationListener<ApplicationReadyEvent> {
    /**
     * Handle an application event.
     *
     * @param event the event to respond to
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        System.out.println("服务启动成功！");
    }
}
