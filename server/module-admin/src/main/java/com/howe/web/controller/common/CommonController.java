package com.howe.web.controller.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.io.IoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.config.YmlConfig;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.LimitType;
import com.howe.common.exception.ServiceException;
import com.howe.common.storage.StorageService;
import com.howe.common.storage.StoredObject;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.file.FileUploadUtils;
import com.howe.common.utils.file.FileUtils;
import com.howe.common.utils.file.MimeTypeUtils;
import com.howe.framework.config.ServerConfig;

/**
 * 通用请求处理
 *
 * @author howe
 */
@Tag(name = "通用接口", description = "文件上传与下载")
@RestController
@RequestMapping("/common")
public class CommonController {
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private StorageService storageService;

    private static final String FILE_DELIMITER = ",";

    /**
     * 通用下载请求
     *
     * <p>
     * 上传早就全量走对象存储了，本地盘上并没有文件，所以这里从 {@link StorageService} 取流回传，
     * 而不是读 {@code YmlConfig.getDownloadPath()} 下的磁盘文件。存储类型是 local 还是 r2
     * 由参数配置决定，本方法两种都适用。
     * </p>
     *
     * @param fileName 文件名称
     * @param delete 是否下载后删除
     */
    @Operation(summary = "通用下载", description = "从当前存储（R2 或本地）取 download 目录下的文件")
    @GetMapping("/download")
    public void fileDownload(@Parameter(description = "文件名称") String fileName,
            @Parameter(description = "下载后是否删除") Boolean delete, HttpServletResponse response) {
        try {
            if (!FileUtils.checkAllowDownload(fileName)) {
                throw new ServiceException(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String objectKey = FileUploadUtils.buildObjectKey(YmlConfig.DOWNLOAD_DIR, fileName);
            if (!writeToResponse(response, objectKey, realFileName)) {
                return;
            }
            // 原实现用 if (delete) 直接拆箱，参数不传就 NPE
            if (Boolean.TRUE.equals(delete)) {
                storageService.remove(objectKey);
            }
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用上传请求（单个）
     */
    @Operation(summary = "通用上传（单个）", description = "上传后返回图床可访问地址")
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception {
        try {
            // 上传并返回可访问地址（R2 模式为图床完整 URL）
            String fileName = FileUploadUtils.uploadTo(YmlConfig.UPLOAD_DIR, file,
                    MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, false);
            String url = FileUploadUtils.toAbsoluteUrl(serverConfig.getUrl(), fileName);
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通用上传请求（多个）
     */
    @Operation(summary = "通用上传（多个）", description = "批量上传，返回逗号分隔的地址列表")
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files) throws Exception {
        try {
            List<String> urls = new ArrayList<String>();
            List<String> fileNames = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files) {
                // 上传并返回可访问地址
                String fileName = FileUploadUtils.uploadTo(YmlConfig.UPLOAD_DIR, file,
                        MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, false);
                String url = FileUploadUtils.toAbsoluteUrl(serverConfig.getUrl(), fileName);
                urls.add(url);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMITER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMITER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMITER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMITER));
            return ajax;
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 图片上传转发到图床（匿名）
     *
     * <p>
     * 供 blog-ui 等站外场景使用：无需登录 token，接口收到图片后直接转存到图床并返回外链，
     * 图片本身不落后端磁盘。按 IP 限流防滥用——匿名接口会往自己的 R2 桶里写文件。
     * </p>
     *
     * @param file 图片文件
     * @return 图床外链地址
     */
    @Anonymous
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @Operation(summary = "图片上传（匿名）", description = "供站外场景使用，仅放行图片扩展名并按 IP 限流")
    @PostMapping("/upload/image")
    public AjaxResult uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return AjaxResult.error("上传文件不能为空");
        }
        try {
            // 只放行图片扩展名，避免匿名接口被当成任意文件网盘
            String location = FileUploadUtils.uploadTo(YmlConfig.UPLOAD_DIR, file, MimeTypeUtils.IMAGE_EXTENSION, true);
            String url = FileUploadUtils.toAbsoluteUrl(serverConfig.getUrl(), location);
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", location);
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        } catch (Exception e) {
            log.error("图片上传失败", e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 资源通用下载
     *
     * <p>
     * 入参是上传时返回的地址：R2 模式下是图床完整 URL，local 模式下是 {@code /profile/...} 相对路径，
     * 两种形态都由存储层自己还原成对象键，调用方不用关心当前用的哪种存储。
     * </p>
     */
    @Operation(summary = "资源下载", description = "按上传返回的地址下载，兼容图床 URL 与 /profile 相对路径")
    @GetMapping("/download/resource")
    public void resourceDownload(@Parameter(description = "上传时返回的文件地址") String resource,
            HttpServletResponse response) {
        try {
            if (!FileUtils.checkAllowDownload(resource)) {
                throw new ServiceException(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            String downloadName = StringUtils.substringAfterLast(resource.replace('\\', '/'), "/");
            writeToResponse(response, resource, downloadName);
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 从存储取流并写进响应
     *
     * @param response 响应
     * @param location 存储地址或对象键
     * @param downloadName 浏览器另存为的文件名
     * @return 文件是否存在并已写出
     */
    private boolean writeToResponse(HttpServletResponse response, String location, String downloadName)
            throws IOException {
        StoredObject fetched = storageService.fetch(location);
        if (fetched == null) {
            log.warn("下载的文件不存在：{}", location);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }
        try (StoredObject object = fetched) {
            // 统一按二进制流下发，配合 Content-Disposition 保证浏览器一律走「下载」而不是内联预览
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            if (object.size() >= 0) {
                response.setContentLengthLong(object.size());
            }
            FileUtils.setAttachmentResponseHeader(response,
                    StringUtils.isNotEmpty(downloadName) ? downloadName : object.fileName());
            IoUtil.copy(object.stream(), response.getOutputStream());
            return true;
        }
    }
}
