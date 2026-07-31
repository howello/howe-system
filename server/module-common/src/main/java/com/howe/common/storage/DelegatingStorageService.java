package com.howe.common.storage;

import java.io.InputStream;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;

/**
 * 存储服务门面
 *
 * <p>
 * 存储类型放在参数配置表里、可以随时改，所以不能用启动期生效的
 * {@code @ConditionalOnProperty} 二选一，改为每次调用时按当前配置分发。
 * 注入 {@link StorageService} 拿到的就是这个门面。
 * </p>
 *
 * @author howe
 */
@Primary
@Component
public class DelegatingStorageService implements StorageService
{
    private final LocalStorageService localStorageService;

    private final R2StorageService r2StorageService;

    public DelegatingStorageService(LocalStorageService localStorageService, R2StorageService r2StorageService)
    {
        this.localStorageService = localStorageService;
        this.r2StorageService = r2StorageService;
    }

    @Override
    public String getType()
    {
        return current().getType();
    }

    @Override
    public String store(String key, InputStream in, long size, String contentType)
    {
        return current().store(key, in, size, contentType);
    }

    @Override
    public boolean remove(String location)
    {
        return current().remove(location);
    }

    /**
     * 按当前配置选择实现，缺省走 R2
     */
    private StorageService current()
    {
        String type = ConfigUtils.getString(ConfigConstants.STORAGE_TYPE, "r2");
        return "local".equalsIgnoreCase(type) ? localStorageService : r2StorageService;
    }
}
