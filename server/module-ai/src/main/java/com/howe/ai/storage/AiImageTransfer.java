package com.howe.ai.storage;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiImage;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.storage.StorageService;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Locale;

/**
 * 生成图转存
 *
 * <p>厂商返回的是几小时后就失效的临时地址，直接写进业务数据会导致过几天全站 404，
 * 所以必须下载字节再转存到对象存储，只把永久地址交给业务方。</p>
 *
 * <p>转存分两级容错：先对「下载」和「上传」各自做有限次重试（复用已拿到的字节，绝不重新生成）；
 * 若已拿到字节但上传仍失败，则落一条补偿任务并把 <b>预写的永久地址</b> 作为成功返回——
 * 定时任务重传成功后，该地址自然生效，业务侧无需回填。</p>
 *
 * <p>只有连字节都没拿到（下载失败且无 base64）时才抛 {@code TRANSFER_FAILED}，因为无从补偿。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImageTransfer
{
    /** 生成图在对象存储中的业务目录 */
    public static final String BIZ_DIR = "ai";

    /** 单张图的下载上限（字节），防止异常响应撑爆内存 */
    private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;

    /** 下载超时（毫秒） */
    private static final int DOWNLOAD_TIMEOUT_MS = 30000;

    private final StorageService storageService;

    private final AiImageCompensationStore compensationStore;

    /**
     * 把厂商返回的图片转存为永久地址
     *
     * @param tempUrl  厂商临时地址，与 base64 二选一
     * @param base64   厂商返回的 base64 内容，与 tempUrl 二选一
     * @param mimeType 期望的 MIME 类型，可为空
     * @return 生成图（永久 url + 对象键 + 尺寸）
     * @throws AiException 下载失败（无从补偿）时抛出
     */
    public AiImage transfer(String tempUrl, String base64, String mimeType)
    {
        byte[] data = downloadWithRetry(tempUrl, base64);
        String extension = resolveExtension(mimeType, tempUrl);
        // 厂商原图动辄 2~3MB，转存前统一压到 500KB 上下
        AiImageCompressor.Compressed compressed = AiImageCompressor.compress(data, extension);
        byte[] uploadBytes = compressed.bytes();
        String uploadExtension = compressed.extension();
        String objectKey = BIZ_DIR + "/" + DateUtils.datePath() + "/" + IdUtil.fastSimpleUUID() + "." + uploadExtension;
        String contentType = resolveContentType(uploadExtension);
        int[] size = resolveSize(compressed, uploadBytes);

        try
        {
            String url = uploadWithRetry(objectKey, uploadBytes, contentType);
            return AiImage.builder()
                    .url(url)
                    .key(objectKey)
                    .width(size == null ? null : size[0])
                    .height(size == null ? null : size[1])
                    .build();
        }
        catch (Exception e)
        {
            return fallbackToCompensation(objectKey, uploadBytes, contentType, size, e);
        }
    }

    /**
     * 已拿到字节但上传失败：落补偿任务并返回预写的永久地址
     *
     * <p>补偿开关关闭时维持原有行为（直接抛错），避免留下一个永远补不上的占位地址。</p>
     *
     * @param objectKey   完整对象键
     * @param uploadBytes 已压缩的字节
     * @param contentType Content-Type
     * @param size        宽高，可为 null
     * @param cause       上传失败原因
     * @return 预写地址的生成图
     */
    private AiImage fallbackToCompensation(String objectKey, byte[] uploadBytes, String contentType, int[] size,
            Exception cause)
    {
        if (!ConfigUtils.getBoolean(ConfigConstants.AI_IMAGE_TRANSFER_COMPENSATE_ENABLED, true))
        {
            throw new AiException(AiErrorCode.TRANSFER_FAILED, "图片已生成但保存失败，请重试", cause);
        }
        String reason = StrUtil.blankToDefault(cause.getMessage(), cause.getClass().getSimpleName());
        int expireMinutes = Math.max(1, ConfigUtils.getInt(ConfigConstants.AI_IMAGE_TRANSFER_EXPIRE_MINUTES, 120));
        log.error("生成图转存失败，转入补偿重传：key={}", objectKey, cause);
        compensationStore.enqueue(objectKey, uploadBytes, contentType, expireMinutes, reason);
        return AiImage.builder()
                .url(storageService.urlOf(objectKey))
                .key(objectKey)
                .width(size == null ? null : size[0])
                .height(size == null ? null : size[1])
                .build();
    }

    /**
     * 下载厂商图片（base64 优先），失败按配置重试
     *
     * @param tempUrl 临时地址
     * @param base64  base64 内容
     * @return 图片字节
     * @throws AiException 重试后仍失败
     */
    private byte[] downloadWithRetry(String tempUrl, String base64)
    {
        if (StrUtil.isNotBlank(base64))
        {
            byte[] data = decodeBase64(base64);
            if (data.length == 0)
            {
                throw new AiException(AiErrorCode.TRANSFER_FAILED, "图片已生成但内容为空，请重试");
            }
            return data;
        }
        int retryMax = retryMax();
        long backoffMs = retryBackoffMs();
        AiException last = null;
        for (int attempt = 0; attempt <= retryMax; attempt++)
        {
            try
            {
                return download(tempUrl);
            }
            catch (AiException e)
            {
                last = e;
                if (attempt < retryMax)
                {
                    ThreadUtil.sleep(backoffMs * (attempt + 1));
                }
            }
        }
        throw last == null ? new AiException(AiErrorCode.TRANSFER_FAILED, "下载生成图失败") : last;
    }

    /**
     * 上传对象存储，失败按配置重试（复用同一份字节，不重新下载）
     *
     * @param objectKey   完整对象键
     * @param data        已压缩的字节
     * @param contentType Content-Type
     * @return 永久地址
     * @throws AiException 重试后仍失败
     */
    private String uploadWithRetry(String objectKey, byte[] data, String contentType)
    {
        int retryMax = retryMax();
        long backoffMs = retryBackoffMs();
        Exception last = null;
        for (int attempt = 0; attempt <= retryMax; attempt++)
        {
            try
            {
                return storageService.store(objectKey, () -> IoUtil.toStream(data), data.length, contentType);
            }
            catch (Exception e)
            {
                last = e;
                log.warn("上传生成图失败（第 {} 次）：key={}，原因：{}", attempt + 1, objectKey, e.getMessage());
                if (attempt < retryMax)
                {
                    ThreadUtil.sleep(backoffMs * (attempt + 1));
                }
            }
        }
        throw new AiException(AiErrorCode.TRANSFER_FAILED,
                "上传生成图失败：" + (last == null ? "未知错误" : last.getMessage()), last);
    }

    /**
     * 转存重试次数
     */
    private int retryMax()
    {
        return Math.max(0, ConfigUtils.getInt(ConfigConstants.AI_IMAGE_TRANSFER_RETRY_MAX, 3));
    }

    /**
     * 转存重试退避基数（毫秒）
     */
    private long retryBackoffMs()
    {
        return Math.max(0L, ConfigUtils.getInt(ConfigConstants.AI_IMAGE_TRANSFER_RETRY_BACKOFF_MS, 1000));
    }

    /**
     * 下载厂商临时图片
     *
     * @param tempUrl 临时地址
     * @return 图片字节
     * @throws AiException 地址为空、下载失败或超出大小上限
     */
    private byte[] download(String tempUrl)
    {
        if (StrUtil.isBlank(tempUrl))
        {
            throw new AiException(AiErrorCode.TRANSFER_FAILED, "厂商没有返回可用的图片地址");
        }
        try (HttpResponse response = HttpRequest.get(tempUrl).timeout(DOWNLOAD_TIMEOUT_MS).execute())
        {
            if (!response.isOk())
            {
                throw new AiException(AiErrorCode.TRANSFER_FAILED,
                        "下载生成图失败，厂商返回状态码 " + response.getStatus());
            }
            byte[] data = response.bodyBytes();
            if (data == null || data.length == 0)
            {
                throw new AiException(AiErrorCode.TRANSFER_FAILED, "下载生成图失败，返回内容为空");
            }
            if (data.length > MAX_DOWNLOAD_BYTES)
            {
                throw new AiException(AiErrorCode.TRANSFER_FAILED,
                        "生成图超过 " + (MAX_DOWNLOAD_BYTES / 1024 / 1024) + "MB，已放弃转存");
            }
            return data;
        }
        catch (AiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new AiException(AiErrorCode.TRANSFER_FAILED, "下载生成图失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解码 base64 图片内容
     *
     * @param base64 base64 字符串，可能带 data:image/...;base64, 前缀
     * @return 图片字节
     */
    private byte[] decodeBase64(String base64)
    {
        String content = base64;
        int comma = content.indexOf(',');
        if (content.startsWith("data:") && comma > 0)
        {
            content = content.substring(comma + 1);
        }
        try
        {
            return Base64.getDecoder().decode(content);
        }
        catch (IllegalArgumentException e)
        {
            throw new AiException(AiErrorCode.TRANSFER_FAILED, "厂商返回的图片内容不是合法的 base64", e);
        }
    }

    /**
     * 推断图片后缀
     *
     * @param mimeType 期望的 MIME 类型
     * @param tempUrl  临时地址，用于兜底推断
     * @return 后缀名
     */
    private String resolveExtension(String mimeType, String tempUrl)
    {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (mime.contains("jpeg") || mime.contains("jpg"))
        {
            return "jpg";
        }
        if (mime.contains("webp"))
        {
            return "webp";
        }
        if (mime.contains("png"))
        {
            return "png";
        }
        if (StrUtil.isNotBlank(tempUrl))
        {
            String path = tempUrl.split("\\?")[0].toLowerCase(Locale.ROOT);
            for (String candidate : new String[] { "png", "jpg", "jpeg", "webp" })
            {
                if (path.endsWith("." + candidate))
                {
                    return "jpeg".equals(candidate) ? "jpg" : candidate;
                }
            }
        }
        // 通义万相默认输出 PNG
        return "png";
    }

    /**
     * 解析上传时使用的 Content-Type
     *
     * @param extension 后缀名
     * @return Content-Type
     */
    private String resolveContentType(String extension)
    {
        return "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
    }

    /**
     * 取图片宽高：压缩结果已带尺寸则直接复用，否则重新读取
     *
     * @param compressed 压缩结果
     * @param data       最终上传的字节
     * @return 宽高数组
     */
    private int[] resolveSize(AiImageCompressor.Compressed compressed, byte[] data)
    {
        if (compressed.width() != null && compressed.height() != null)
        {
            return new int[] { compressed.width(), compressed.height() };
        }
        return readSize(data);
    }

    /**
     * 读取图片尺寸，失败时返回 null
     *
     * @param data 图片字节
     * @return 宽高数组
     */
    private int[] readSize(byte[] data)
    {
        try (ByteArrayInputStream input = new ByteArrayInputStream(data))
        {
            BufferedImage image = ImgUtil.read(input);
            return image == null ? null : new int[] { image.getWidth(), image.getHeight() };
        }
        catch (Exception e)
        {
            log.warn("读取生成图尺寸失败，调用记录中将不记录宽高：{}", e.getMessage());
            return null;
        }
    }
}
