package com.howe.common.storage;

import java.io.InputStream;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import org.springframework.stereotype.Component;
import com.howe.common.config.YmlConfig;
import com.howe.common.constant.Constants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.StringUtils;

/**
 * 本地磁盘存储实现
 *
 * <p>
 * 参数设置里 sys.storage.type=local 时启用，用于没有 R2 凭据的本机开发。
 * 生产走 {@link R2StorageService}——容器重建会丢掉本地目录里的文件。
 * </p>
 *
 * @author howe
 */
@Component
public class LocalStorageService implements StorageService
{
    @Override
    public String getType()
    {
        return "local";
    }

    @Override
    public String store(String key, InputStream in, long size, String contentType)
    {
        try (InputStream input = in)
        {
            String target = YmlConfig.getProfile() + "/" + key;
            FileUtil.mkParentDirs(target);
            FileUtil.writeFromStream(input, FileUtil.touch(target));
            return Constants.RESOURCE_PREFIX + "/" + key;
        }
        catch (Exception e)
        {
            throw new ServiceException("文件写入本地磁盘失败：" + e.getMessage());
        }
        finally
        {
            IoUtil.close(in);
        }
    }

    @Override
    public boolean remove(String location)
    {
        if (StringUtils.isEmpty(location))
        {
            return false;
        }
        // 兼容三种入参：/profile/upload/xxx.png、upload/xxx.png、完整 URL
        String key = location;
        if (StringUtils.contains(key, Constants.RESOURCE_PREFIX + "/"))
        {
            key = StringUtils.substringAfter(key, Constants.RESOURCE_PREFIX + "/");
        }
        String target = YmlConfig.getProfile() + "/" + StringUtils.stripStart(key, "/");
        return FileUtil.isFile(target) && FileUtil.del(target);
    }
}
