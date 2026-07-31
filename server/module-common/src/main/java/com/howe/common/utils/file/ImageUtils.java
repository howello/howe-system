package com.howe.common.utils.file;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.howe.common.config.YmlConfig;
import com.howe.common.constant.Constants;
import com.howe.common.utils.StringUtils;

/**
 * 图片处理工具类
 *
 * @author howe
 */
public class ImageUtils
{
    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

    /** 网络图片读取超时（毫秒） */
    private static final int READ_TIMEOUT = 60000;

    public static byte[] getImage(String imagePath)
    {
        InputStream is = getFile(imagePath);
        try
        {
            return IoUtil.readBytes(is);
        }
        catch (Exception e)
        {
            log.error("图片加载异常 {}", e);
            return null;
        }
        finally
        {
            IoUtil.close(is);
        }
    }

    public static InputStream getFile(String imagePath)
    {
        try
        {
            byte[] result = readFile(imagePath);
            return new ByteArrayInputStream(result);
        }
        catch (Exception e)
        {
            log.error("获取图片异常 {}", e);
        }
        return null;
    }

    /**
     * 读取文件为字节数据
     *
     * <p>
     * 切到对象存储后，上传返回的是图床外链，会走 http 分支；只有存储类型为 local 时
     * 才是 /profile 相对路径，走本地磁盘分支。
     * </p>
     *
     * @param url 地址
     * @return 字节数据
     */
    public static byte[] readFile(String url)
    {
        try
        {
            if (StringUtils.startsWithIgnoreCase(url, "http"))
            {
                // 网络地址
                return HttpRequest.get(url).timeout(READ_TIMEOUT).execute().bodyBytes();
            }
            // 本机地址
            String localPath = YmlConfig.getProfile();
            String downloadPath = localPath + StringUtils.substringAfter(url, Constants.RESOURCE_PREFIX);
            return FileUtil.readBytes(downloadPath);
        }
        catch (Exception e)
        {
            log.error("获取文件路径异常 {}", e);
            return null;
        }
    }
}
