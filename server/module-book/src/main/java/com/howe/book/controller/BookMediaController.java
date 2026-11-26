package com.howe.book.controller;

import cn.hutool.http.HttpRequest;
import com.howe.common.config.YmlConfig;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.file.FileUploadUtils;
import com.howe.common.utils.uuid.IdUtils;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 读书在线封面导入。 */
@RestController
@RequestMapping("/book/media")
public class BookMediaController {
    private static final Map<String, String> COVER_EXTENSIONS = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "image/gif", "gif");

    /** 将受支持检索来源的封面保存到共享媒体存储。 */
    @PostMapping("/import-cover")
    @Operation(summary = "采用在线封面")
    public AjaxResult importCover(@RequestBody Map<String, String> input) throws IOException {
        URI uri;
        try {
            uri = URI.create(input.getOrDefault("url", ""));
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("封面地址无效");
        }
        String referer = coverReferer(uri.getHost());
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1 || referer == null) {
            throw new ServiceException("封面来源无效");
        }
        try (var upstream = HttpRequest.get(uri.toASCIIString()).setFollowRedirects(false)
                .header("Referer", referer).header("User-Agent", "Mozilla/5.0").timeout(7000).executeAsync()) {
            String header = upstream.header("Content-Type");
            String mime = header == null ? "" : header.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            String extension = COVER_EXTENSIONS.get(mime);
            if (!upstream.isOk() || extension == null) {
                throw new ServiceException("封面暂不可用");
            }
            byte[] bytes = upstream.bodyStream().readNBytes(5 * 1024 * 1024 + 1);
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
                throw new ServiceException("封面文件为空或超过 5 MB");
            }
            String fileName = DateUtils.datePath() + "/" + IdUtils.fastSimpleUUID() + "." + extension;
            String location = FileUploadUtils.uploadBytes(YmlConfig.UPLOAD_DIR, fileName, bytes, mime);
            return AjaxResult.success(Map.of("url", location));
        }
    }

    /** 仅允许访问四个检索来源的封面 CDN。 */
    private String coverReferer(String host) {
        if (host == null) {
            return null;
        }
        if (host.matches("img[0-9]+\\.doubanio\\.com")) {
            return "https://book.douban.com/";
        }
        if ("pic.arkread.com".equals(host)) {
            return "https://read.douban.com/";
        }
        if ("cdn.weread.qq.com".equals(host)) {
            return "https://weread.qq.com/";
        }
        if (host.matches("(?:t|img|gimg)[0-9]+\\.baidu\\.com|img[0-9]+\\.imgtn\\.bdimg\\.com")) {
            return "https://image.baidu.com/";
        }
        // 配图检索当前主用 360 图片，需要允许其缩略图 CDN
        if (host.matches("p[0-9]+\\.ssl\\.qhimgs[0-9]*\\.com")) {
            return "https://image.so.com/";
        }
        return null;
    }
}
