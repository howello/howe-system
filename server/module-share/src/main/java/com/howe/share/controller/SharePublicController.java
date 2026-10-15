package com.howe.share.controller;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.LimitType;
import com.howe.common.storage.StoredObject;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.dto.ShareVerifyRequest;
import com.howe.share.domain.vo.SharePublicDetail;
import com.howe.share.service.SharePublicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 公开分享 控制层
 *
 * <p>匿名可访问（{@code @Anonymous}），按来源 IP 限流。路径不带 {@code /api} 前缀，
 * 与公开分享页同源，票据 cookie 直接落当前域。</p>
 *
 * @author howe
 */
@Tag(name = "分享-公开端", description = "短链分享的匿名访问接口")
@Anonymous
@RestController
@RequestMapping("/share/public")
@RequiredArgsConstructor
public class SharePublicController extends BaseController {

    /** 终止态（返回 HTTP 410） */
    private static final Set<String> GONE_STATES = Set.of(
            ShareConstants.PUBLIC_STATE_INVALID,
            ShareConstants.PUBLIC_STATE_BURNED,
            ShareConstants.PUBLIC_STATE_EXPIRED,
            ShareConstants.PUBLIC_STATE_EXHAUSTED,
            ShareConstants.PUBLIC_STATE_NOTFOUND);

    private final SharePublicService sharePublicService;

    /**
     * 获取分享详情（不含文件内容）
     */
    @Operation(summary = "获取分享详情")
    @RateLimiter(time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/detail")
    public AjaxResult detail(@Parameter(description = "短码", required = true) @RequestParam("code") String code,
                             HttpServletRequest request, HttpServletResponse response) {
        SharePublicDetail detail = sharePublicService.detail(code, request);
        if (GONE_STATES.contains(detail.state())) {
            response.setStatus(HttpServletResponse.SC_GONE);
        }
        return success(detail);
    }

    /**
     * 校验密码并下发密码票据
     */
    @Operation(summary = "校验密码")
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @PostMapping("/verify")
    public AjaxResult verify(@Valid @RequestBody ShareVerifyRequest body,
                             HttpServletRequest request, HttpServletResponse response) {
        sharePublicService.verify(body.code(), body.password(), request, response);
        return success();
    }

    /**
     * 进入就绪态：计数 + 焚毁判定 + 下发就绪票据
     */
    @Operation(summary = "进入就绪态")
    @RateLimiter(time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/open")
    public AjaxResult open(@Parameter(description = "短码", required = true) @RequestParam("code") String code,
                           HttpServletRequest request, HttpServletResponse response) {
        return success(sharePublicService.open(code, request, response));
    }

    /**
     * 读取文件字节流（inline，供 img / iframe / video 直接引用）
     */
    @Operation(summary = "读取文件（inline）")
    @RateLimiter(time = 60, count = 180, limitType = LimitType.IP)
    @GetMapping("/file")
    public void file(@Parameter(description = "短码", required = true) @RequestParam("code") String code,
                     HttpServletRequest request, HttpServletResponse response) throws IOException {
        StoredObject object = sharePublicService.openFile(code, ShareConstants.ACTION_FILE, request);
        writeFile(response, object, object.fileName(), false);
    }

    /**
     * 下载文件（attachment）
     */
    @Operation(summary = "下载文件（attachment）")
    @RateLimiter(time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/download")
    public void download(@Parameter(description = "短码", required = true) @RequestParam("code") String code,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        StoredObject object = sharePublicService.openFile(code, ShareConstants.ACTION_DOWNLOAD, request);
        writeFile(response, object, object.fileName(), true);
    }

    /**
     * 把文件字节写入响应
     *
     * @param response   响应
     * @param object     文件读取结果
     * @param fileName   文件名
     * @param attachment true 下载、false 内联
     * @throws IOException IO 异常
     */
    private void writeFile(HttpServletResponse response, StoredObject object, String fileName,
                           boolean attachment) throws IOException {
        String contentType = StrUtil.blankToDefault(object.contentType(), "application/octet-stream");
        response.setContentType(contentType);
        String name = StrUtil.blankToDefault(fileName, "file");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                (attachment ? "attachment" : "inline") + "; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        response.setHeader("X-Robots-Tag", "noindex");
        if (object.size() > 0) {
            response.setContentLengthLong(object.size());
        }
        try (StoredObject stored = object) {
            IoUtil.copy(stored.stream(), response.getOutputStream());
        }
    }
}
