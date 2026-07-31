package com.howe.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.URLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.constant.Constants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Cloudflare R2 对象存储实现（S3 兼容协议）
 *
 * <p>
 * 凭据来自「系统管理 &gt; 参数设置」而不是 yml，因此客户端不能在构造期一次性建好：
 * 这里按凭据指纹缓存 S3Client，参数改了下次调用会自动重建，不用重启服务。
 * </p>
 *
 * @author howe
 */
@Component
public class R2StorageService implements StorageService
{
    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

    /** R2 固定使用 auto 区域 */
    private static final String REGION = "auto";

    /** 未指定 MIME 类型时的缺省值 */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** 当前缓存客户端对应的凭据指纹 */
    private volatile String cachedFingerprint;

    private volatile S3Client cachedClient;

    @Override
    public String getType()
    {
        return "r2";
    }

    @Override
    public String store(String key, ContentSource source, long size, String contentType)
    {
        String objectKey = withPrefix(key);
        String bucket = ConfigUtils.getRequired(ConfigConstants.STORAGE_R2_BUCKET, "R2 存储桶");
        // SDK 重试时会重新取流，只有最后一次取的流由它关闭，被丢弃的那些得自己收尾
        List<InputStream> opened = Collections.synchronizedList(new ArrayList<>());
        try
        {
            PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(objectKey);
            if (StringUtils.isNotEmpty(contentType))
            {
                request.contentType(contentType);
            }
            String mimeType = StringUtils.isNotEmpty(contentType) ? contentType : DEFAULT_CONTENT_TYPE;
            ContentStreamProvider provider = () -> openTracked(source, opened);
            // 交出内容源而非单个流：重试要二次读取，流式上传因此不必把文件缓冲进内存
            RequestBody body = size >= 0 ? RequestBody.fromContentProvider(provider, size, mimeType)
                    : RequestBody.fromContentProvider(provider, mimeType);
            client().putObject(request.build(), body);
            return publicUrl() + "/" + objectKey;
        }
        catch (S3Exception e)
        {
            log.error("上传至 R2 失败，key={}", objectKey, e);
            throw new ServiceException("上传至图床失败：" + e.awsErrorDetails().errorMessage());
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("上传至 R2 失败，key={}", objectKey, e);
            throw new ServiceException("上传至图床失败：" + e.getMessage());
        }
        finally
        {
            opened.forEach(IoUtil::close);
        }
    }

    /**
     * 打开内容源并登记，便于上传结束后统一关闭
     *
     * <p>
     * {@link ContentStreamProvider} 不允许抛受检异常，这里把 IO 失败转成运行时异常，
     * 由 {@link #store} 的兜底分支包装成 {@link ServiceException}。
     * </p>
     */
    private InputStream openTracked(ContentSource source, List<InputStream> opened)
    {
        try
        {
            InputStream in = source.open();
            opened.add(in);
            return in;
        }
        catch (IOException e)
        {
            throw new ServiceException("读取上传内容失败：" + e.getMessage());
        }
    }

    @Override
    public StoredObject fetch(String location)
    {
        String objectKey = resolveKey(location);
        if (StringUtils.isEmpty(objectKey))
        {
            return null;
        }
        String bucket = ConfigUtils.getRequired(ConfigConstants.STORAGE_R2_BUCKET, "R2 存储桶");
        try
        {
            ResponseInputStream<GetObjectResponse> stream = client()
                    .getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            GetObjectResponse meta = stream.response();
            Long length = meta.contentLength();
            return new StoredObject(objectKey, fileNameOf(objectKey), meta.contentType(),
                    length == null ? -1L : length, stream);
        }
        catch (NoSuchKeyException e)
        {
            log.warn("R2 中不存在该文件，key={}", objectKey);
            return null;
        }
        catch (S3Exception e)
        {
            log.error("从 R2 读取文件失败，key={}", objectKey, e);
            throw new ServiceException("读取文件失败：" + e.awsErrorDetails().errorMessage());
        }
        catch (Exception e)
        {
            log.error("从 R2 读取文件失败，key={}", objectKey, e);
            throw new ServiceException("读取文件失败：" + e.getMessage());
        }
    }

    /**
     * 取对象键里的文件名部分
     */
    private static String fileNameOf(String objectKey)
    {
        int index = objectKey.lastIndexOf('/');
        return index < 0 ? objectKey : objectKey.substring(index + 1);
    }

    @Override
    public boolean remove(String location)
    {
        String objectKey = resolveKey(location);
        if (StringUtils.isEmpty(objectKey))
        {
            return false;
        }
        try
        {
            String bucket = ConfigUtils.getRequired(ConfigConstants.STORAGE_R2_BUCKET, "R2 存储桶");
            client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        }
        catch (Exception e)
        {
            // 删除失败不阻断主流程（通常是替换头像时清理旧文件），仅记录
            log.warn("从 R2 删除文件失败，key={}，原因：{}", objectKey, e.getMessage());
            return false;
        }
    }

    /**
     * 取 S3 客户端
     *
     * <p>
     * 凭据没变就复用，变了才重建——参数设置页改完 endpoint/密钥即时生效。
     * </p>
     */
    private S3Client client()
    {
        String endpoint = ConfigUtils.getRequired(ConfigConstants.STORAGE_R2_ENDPOINT, "R2 端点地址");
        String accessKey = ConfigUtils.getRequired(ConfigConstants.STORAGE_R2_ACCESS_KEY, "R2 Access Key ID");
        String secretKey = ConfigUtils.getRequired(ConfigConstants.STORAGE_R2_SECRET_KEY, "R2 Secret Access Key");
        String fingerprint = endpoint + "|" + accessKey + "|" + secretKey;
        S3Client current = cachedClient;
        if (current != null && fingerprint.equals(cachedFingerprint))
        {
            return current;
        }
        synchronized (this)
        {
            if (cachedClient != null && fingerprint.equals(cachedFingerprint))
            {
                return cachedClient;
            }
            IoUtil.close(cachedClient);
            cachedClient = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .region(Region.of(REGION))
                    // R2 不支持 virtual-hosted-style 寻址，必须走 path-style
                    .forcePathStyle(true)
                    .httpClient(UrlConnectionHttpClient.create())
                    .build();
            cachedFingerprint = fingerprint;
            log.info("R2 客户端已就绪，endpoint={}", endpoint);
            return cachedClient;
        }
    }

    /**
     * 公开访问域名，末尾不带斜杠
     */
    private String publicUrl()
    {
        return StringUtils.stripEnd(ConfigUtils.getRequired(ConfigConstants.STORAGE_R2_PUBLIC_URL, "R2 公开访问域名"), "/");
    }

    /**
     * 对象键前缀，已去掉首尾斜杠，可能为空串
     */
    private String keyPrefix()
    {
        String prefix = ConfigUtils.getString(ConfigConstants.STORAGE_R2_KEY_PREFIX);
        return StringUtils.isEmpty(prefix) ? "" : StringUtils.strip(prefix, "/");
    }

    /**
     * 把存储时返回的地址还原成对象键
     *
     * <p>
     * 兼容三种形态：本服务返回的完整 URL、其它域名的完整 URL、以及切换存储前遗留的
     * /profile 相对路径（这类文件在 R2 上并不存在，DeleteObject 会静默成功）。
     * </p>
     */
    private String resolveKey(String location)
    {
        if (StringUtils.isEmpty(location))
        {
            return null;
        }
        String value = location.trim();
        String publicUrl = publicUrl();
        if (value.startsWith(publicUrl + "/"))
        {
            return value.substring(publicUrl.length() + 1);
        }
        if (StringUtils.startsWithIgnoreCase(value, "http://") || StringUtils.startsWithIgnoreCase(value, "https://"))
        {
            String path = URLUtil.getPath(value);
            return StringUtils.stripStart(path, "/");
        }
        // 遗留的本地路径 /profile/avatar/xxx.png，剥掉前缀后按对象键处理
        if (value.startsWith(Constants.RESOURCE_PREFIX + "/"))
        {
            return withPrefix(value.substring(Constants.RESOURCE_PREFIX.length() + 1));
        }
        return withPrefix(StringUtils.stripStart(value, "/"));
    }

    /**
     * 拼接对象键前缀
     */
    private String withPrefix(String key)
    {
        String normalized = StringUtils.stripStart(key, "/");
        String prefix = keyPrefix();
        return prefix.isEmpty() ? normalized : prefix + "/" + normalized;
    }
}
