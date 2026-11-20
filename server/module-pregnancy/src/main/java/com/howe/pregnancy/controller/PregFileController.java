package com.howe.pregnancy.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.pregnancy.domain.vo.FileVO;
import com.howe.pregnancy.service.IPregFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 私有原图 控制层
 *
 * <p>文件流也走 Bearer 鉴权，不把 token 放在 URL 中；响应禁止公共缓存，
 * 不返回磁盘路径或公开地址。</p>
 *
 * @author howe
 */
@Tag(name = "孕期-私有原图", description = "上传、元数据、鉴权内容与删除")
@RestController
@RequestMapping("/pregnancy/files")
@RequiredArgsConstructor
public class PregFileController extends BaseController {

    private final IPregFileService fileService;

    /**
     * 上传原图
     *
     * @param file      原文件
     * @param requestId 请求 ID
     * @return 文件元数据
     */
    @Operation(summary = "上传原图", description = "返回 TEMP 元数据；单文件上限与格式由配置与目录校验")
    @PostMapping
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "requestId", required = false) String requestId) {
        return success(fileService.upload(file, requestId));
    }

    /**
     * 查询文件元数据
     *
     * @param id 文件 ID
     * @return 元数据
     */
    @Operation(summary = "查询文件元数据", description = "TEMP 仅上传者或孕期管理员可读")
    @GetMapping("/{id}")
    public AjaxResult metadata(@PathVariable("id") Long id) {
        return success(fileService.metadata(id));
    }

    /**
     * 读取文件内容
     *
     * @param id 文件 ID
     * @return 二进制流
     */
    @Operation(summary = "读取原图内容", description = "返回原文件流，private/no-store，禁止公共缓存")
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable("id") Long id) {
        IPregFileService.FileContent content = fileService.content(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + safeName(content.fileName()) + "\"")
                .body(new InputStreamResource(content.inputStream()));
    }

    /**
     * 删除未绑定文件
     *
     * @param id 文件 ID
     * @return 操作结果
     */
    @Operation(summary = "删除未绑定文件", description = "已绑定业务记录的文件返回 RESOURCE_IN_USE")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        fileService.delete(id);
        return success();
    }

    /**
     * 净化文件名
     *
     * @param name 原始文件名
     * @return 安全文件名
     */
    private String safeName(String name) {
        return name == null ? "image" : name.replaceAll("[\\r\\n\"\\\\]", "_");
    }
}
