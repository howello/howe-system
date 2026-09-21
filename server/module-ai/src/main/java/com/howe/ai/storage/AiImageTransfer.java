package com.howe.ai.storage;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiImage;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.file.FileUploadUtils;
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
 * <p>转存失败时抛 {@code TRANSFER_FAILED} 且<b>不返回临时地址</b>——
 * 宁可让用户重试，也不给一个会失效的地址。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class AiImageTransfer
{
    /** 生成图在对象存储中的业务目录 */
    public static final String BIZ_DIR = "ai";

    /** 单张图的下载上限（字节），防止异常响应撑爆内存 */
    private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;

    /** 下载超时（毫秒） */
    private static final int DOWNLOAD_TIMEOUT_MS = 30000;

    /**
     * 把厂商返回的图片转存为永久地址
     *
     * @param tempUrl  厂商临时地址，与 base64 二选一
     * @param base64   厂商返回的 base64 内容，与 tempUrl 二选一
     * @param mimeType 期望的 MIME 类型，可为空
     * @return 生成图（永久 url + 对象键 + 尺寸）
     * @throws AiException 下载或转存失败
     */
    public AiImage transfer(String tempUrl, String base64, String mimeType)
    {
        byte[] data = StrUtil.isNotBlank(base64) ? decodeBase64(base64) : download(tempUrl);
        if (data.length == 0)
        {
            throw new AiException(AiErrorCode.TRANSFER_FAILED, "图片已生成但内容为空，请重试");
        }
        String extension = resolveExtension(mimeType, tempUrl);
        String objectKey = DateUtils.datePath() + "/" + IdUtil.fastSimpleUUID() + "." + extension;
        String url;
        try
        {
            url = FileUploadUtils.uploadBytes(BIZ_DIR, objectKey, data, resolveContentType(extension, mimeType));
        }
        catch (Exception e)
        {
            log.error("AI 生成图转存失败", e);
            throw new AiException(AiErrorCode.TRANSFER_FAILED, "图片已生成但保存失败，请重试", e);
        }
        int[] size = readSize(data);
        return AiImage.builder()
                .url(url)
                .key(BIZ_DIR + "/" + objectKey)
                .width(size == null ? null : size[0])
                .height(size == null ? null : size[1])
                .build();
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
     * @param mimeType  期望的 MIME 类型
     * @return Content-Type
     */
    private String resolveContentType(String extension, String mimeType)
    {
        if (StrUtil.isNotBlank(mimeType) && mimeType.startsWith("image/"))
        {
            return mimeType;
        }
        return "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
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
