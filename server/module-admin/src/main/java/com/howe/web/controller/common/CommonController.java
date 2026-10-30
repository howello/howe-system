package com.howe.web.controller.common;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.yaml.YamlUtil;
import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.config.YmlConfig;
import com.howe.common.constant.Constants;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
     *
     * <p>
     * 入参来自请求，必须按「不可信输入」处理：先解码、再归一化，任何试图上跳目录或
     * 指向绝对路径的取值一律拒绝，避免切换本地存储后变成任意文件读取。
     * </p>
     */
    @Operation(summary = "资源下载", description = "按上传返回的地址下载，兼容图床 URL 与 /profile 相对路径")
    @GetMapping("/download/resource")
    public void resourceDownload(@Parameter(description = "上传时返回的文件地址") String resource,
            HttpServletResponse response) {
        try {
            String safeResource = normalizeResource(resource);
            if (!FileUtils.checkAllowDownload(safeResource)) {
                throw new ServiceException(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            String downloadName = StringUtils.substringAfterLast(safeResource.replace('\\', '/'), "/");
            writeToResponse(response, safeResource, downloadName);
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 订阅文件代理（匿名）
     *
     * <p>
     * 把 {@code /common/sub/**} 后面的部分原样拼到订阅站点基址上再发起 GET，等同于给订阅地址套一层
     * 自己的域名：{@code /common/sub/did/wyt?view=raw} 转发到
     * {@code https://sub.wyantao.com/did/wyt?view=raw}。请求头整体透传（Host、Content-Length 等
     * 逐跳头除外），方便下游按 UA / Authorization 区分订阅格式与鉴权。
     * </p>
     *
     * <p>
     * 响应分两种：内容是 YAML（Clash/Mihomo 配置）时做两处改写后按附件下载，
     * 文件名固定为 {@code 王艳涛.yaml}——{@code proxies} 里每个节点的 {@code port} 改成 443、
     * 已有的 {@code tls} 改成 true，其余内容（含注释、格式）逐字符保留；
     * 不是 YAML 时（例如订阅失效返回的 JSON 报错）原样回传，状态码与 Content-Type 一并带上。
     * </p>
     *
     * <p>
     * 匿名接口按 IP 限流，避免被当成免费代理反复打。
     * </p>
     *
     * @param request  当前请求，用于取子路径、查询串与待透传的请求头
     * @param response 响应
     */
    @Anonymous
    @RateLimiter(time = 60, count = 30, limitType = LimitType.IP)
    @Operation(summary = "订阅代理（匿名）", description = "转发到订阅站点；YAML 结果改写 port/tls 后按附件下发")
    @GetMapping("/sub/**")
    public void subProxy(HttpServletRequest request, HttpServletResponse response) {
        // 下游路径前缀、订阅站点基址、下载文件名、超时与各类需要跳过的头都只服务本方法，就近声明
        final String subPathPrefix = "/common/sub";
        final String subBaseUrl = "https://sub.wyantao.com";
        final String downloadFileName = "王艳涛";
        final String downloadAsciiFileName = "wyantao";
        final int timeoutMs = 15000;
        // 逐跳/客户端自管请求头，透传会导致签名或分块传输异常
        final Set<String> skipForwardHeaders = Set.of(
                "host", "content-length", "connection", "transfer-encoding", "accept-encoding", "upgrade");
        // 回传时跳过的下游响应头：会因改写而变必须重算的、已被解压的、容器自管的
        final Set<String> skipResponseHeaders = Set.of(
                "content-length", "content-type", "content-disposition", "content-encoding",
                "connection", "keep-alive", "transfer-encoding", "upgrade", "date", "server",
                "proxy-authenticate", "proxy-authorization", "te", "trailer");

        // 子路径取原始 URI /common/sub 之后的部分：多段路径与末尾斜杠都按原貌带给下游
        String uri = request.getRequestURI();
        String subPath = StrUtil.subAfter(uri, subPathPrefix, false);
        String targetUrl = subBaseUrl + (StrUtil.isEmpty(subPath) ? "/" : subPath);
        String query = request.getQueryString();
        if (StrUtil.isNotEmpty(query)) {
            targetUrl = targetUrl + "?" + query;
        }

        // 逐跳头由 HTTP 客户端自己生成；Accept-Encoding 不透传，避免下游回压缩体后无法当文本判断 YAML
        HttpRequest httpRequest = HttpUtil.createGet(targetUrl).timeout(timeoutMs);
        // hutool 预置了 Accept / User-Agent 默认值，而它的 header 表是大小写敏感的 HashMap：
        // 客户端按 HTTP/2 规范发小写 user-agent 时会变成另一个键，最终被默认值覆盖，UA 等于没透传。
        // 这里按已有键的原始大小写写回，保证 UA 等头真正生效。
        Map<String, List<String>> presetHeaders = httpRequest.headers();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (skipForwardHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String targetName = presetHeaders.keySet().stream()
                    .filter(existing -> existing != null && existing.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(name);
            Enumeration<String> values = request.getHeaders(name);
            boolean first = true;
            while (values.hasMoreElements()) {
                // 首个值覆盖 hutool 默认值，其余多值（如多个 Cookie）追加
                httpRequest.header(targetName, values.nextElement(), first);
                first = false;
            }
        }

        try (HttpResponse httpResponse = httpRequest.execute()) {
            byte[] body = httpResponse.bodyBytes();
            String rewritten = rewriteSubscriptionYaml(new String(body, StandardCharsets.UTF_8));

            // 先整体透传下游响应头：订阅源会在头里带用量与更新策略（Subscription-Userinfo /
            // Profile-Update-Interval 等），客户端靠它们显示流量、决定何时刷新，丢了这个功能就废。
            // 必须重算的（Content-Length / Content-Type / Content-Disposition）、已解压的
            // Content-Encoding、逐跳头与容器生成的 Date 由下面各自处理，这里跳过。
            httpResponse.headers().forEach((name, values) -> {
                if (StrUtil.isBlank(name) || values == null
                        || skipResponseHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                    return;
                }
                for (String value : values) {
                    // HTTP 头只允许 ISO-8859-1，带其它字符会被 Tomcat 整条丢弃，这类头直接不传
                    if (value != null && value.chars().allMatch(c -> c <= 0xFF)) {
                        response.addHeader(name, value);
                    }
                }
            });

            if (rewritten == null) {
                // 不是 YAML（多半是订阅失效后的 JSON 报错），原样回传，便于客户端看到真实原因
                response.setStatus(httpResponse.getStatus());
                String contentType = httpResponse.header(Header.CONTENT_TYPE);
                if (StrUtil.isNotEmpty(contentType)) {
                    response.setContentType(contentType);
                }
                response.setContentLength(body.length);
                response.getOutputStream().write(body);
            } else {
                byte[] out = rewritten.getBytes(StandardCharsets.UTF_8);
                // 内容被改写过，状态码与长度都以本方法为准；下游的 200 之外状态不再透传
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/yaml;charset=UTF-8");
                response.setContentLength(out.length);
                // 文件名含中文，不能直接拼进头：HTTP 头只允许 ISO-8859-1（0-255），
                // 明文的「王」会被 Tomcat 判为非法并把整条 Content-Disposition 丢掉。
                // filename= 放纯 ASCII 兜底名（老客户端只认这个），filename*= 放 UTF-8 百分号编码的完整中文名。
                response.setHeader("Content-Disposition",
                        "attachment; filename=\"" + downloadAsciiFileName + "\"; filename*=utf-8''"
                                + FileUtils.percentEncode(downloadFileName));
                response.getOutputStream().write(out);
            }
            response.flushBuffer();
        } catch (Exception e) {
            log.error("订阅代理失败：{}", targetUrl, e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                try {
                    // 错误体直接用项目统一的 AjaxResult + JSONUtil，不再手拼 JSON
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(JSONUtil.toJsonStr(
                            AjaxResult.error("订阅源请求失败：" + e.getMessage())));
                } catch (IOException ignored) {
                    // 响应已断开，无需再处理
                }
            }
        }
    }

    /**
     * 改写订阅 YAML 的 port 与 tls
     *
     * <p>
     * 先用 {@link YamlUtil} 确认内容是带非空 {@code proxies} 数组的 YAML，不是就返回 null，
     * 交给调用方原样回传。确认后逐行文本改写：每个节点的 {@code port} 统一改成 443，
     * 原本就有 {@code tls} 的改成 true（原本没有的不新增，避免给 socks5 之类协议塞非法字段）。
     * </p>
     *
     * <p>
     * 采用逐行改写而不是「解析再序列化」，是为了让节点名、注释、键顺序、缩进全部保持原样；
     * 只有与列表项同一层级的键才会被改，{@code ws-opts} 这类嵌套块里的同名键不受影响。
     * </p>
     *
     * @param text 订阅原始内容
     * @return 改写后的文本；内容不是含 proxies 数组的 YAML 时返回 null
     */
    private String rewriteSubscriptionYaml(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            Dict yaml = YamlUtil.load(new StringReader(text));
            if (!(yaml.get("proxies") instanceof List)) {
                return null;
            }
        } catch (Exception e) {
            // 不是合法 YAML（含订阅失效返回的 JSON / HTML 错误页）
            return null;
        }

        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = text.split("\r?\n", -1);
        // proxies: 所在列、列表项所在列、键所在列；Clash 输出里所有条目共用同一缩进
        int proxiesIndent = -1;
        int itemIndent = -1;
        int keyIndent = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String content = StrUtil.subBefore(line, " #", false).trim();
            if (content.isEmpty()) {
                continue;
            }
            // 缩进 = 去掉前导空白后的长度差，无需单独的计数方法
            int indent = line.length() - line.stripLeading().length();
            boolean listItem = content.startsWith("-");
            if (proxiesIndent < 0) {
                // 先定位 proxies 段落的起始行，其后的行才参与改写
                if ("proxies:".equals(content)) {
                    proxiesIndent = indent;
                }
                continue;
            }
            if (itemIndent < 0) {
                // 段落内第一条列表项决定条目与键的缩进基准；不是列表说明 proxies 为空。
                // 顶格写法（`- name:` 与 `proxies:` 同列）也合法，故只要求是列表项即可
                if (!listItem) {
                    return null;
                }
                itemIndent = indent;
                keyIndent = indent + 2;
                continue;
            }
            if (listItem && indent == itemIndent) {
                // 新条目，键列回到该条目下
                keyIndent = indent + 2;
                continue;
            }
            if (indent <= proxiesIndent) {
                // 非列表项回到了顶层（如 proxy-groups:），proxies 段落结束
                break;
            }
            if (indent != keyIndent) {
                // 嵌套块（如 ws-opts 内的 headers/path）不动
                continue;
            }
            String key = StrUtil.subBefore(content, ':', false).trim();
            String value = "port".equals(key) ? "443" : "tls".equals(key) ? "true" : null;
            if (value == null) {
                continue;
            }
            String comment = StrUtil.subAfter(line, " #", false);
            lines[i] = line.substring(0, keyIndent) + key + ": " + value
                    + (StrUtil.isEmpty(comment) ? "" : " #" + comment);
        }
        return itemIndent < 0 ? null : String.join(eol, lines);
    }

    /**
     * 归一化资源地址并拒绝越权路径
     *
     * <p>
     * 只放行两种形态：{@code http(s)://} 开头的完整地址（对象存储公网域名），
     * 以及 {@code /profile/} 前缀的相对路径或裸对象键。处理顺序是先 URL 解码
     * （防止 {@code %2e%2e} 绕过字面量校验），再折叠多余分隔符，最后统一拒绝
     * 含 {@code ..}、控制字符、盘符或非 http(s) 协议的取值。
     * </p>
     *
     * @param resource 请求传入的资源地址
     * @return 归一化后的安全地址
     */
    private String normalizeResource(String resource) {
        if (StringUtils.isEmpty(resource)) {
            throw new ServiceException("资源文件不能为空");
        }
        // 先解码，避免 %2e%2e / %2f 之类的编码绕过后续字面量检查
        String decoded = URLUtil.decode(resource.trim());
        if (StringUtils.contains(decoded, "..") || StringUtils.containsAny(decoded, '\u0000', '\r', '\n')) {
            throw new ServiceException("资源文件非法，不允许下载。");
        }
        // 统一分隔符后再判断，Windows 风格的反斜杠同样不能用于上跳
        String normalized = decoded.replace('\\', '/');
        if (StringUtils.startsWithIgnoreCase(normalized, "http://")
                || StringUtils.startsWithIgnoreCase(normalized, "https://")) {
            // 完整地址原样交给存储层匹配公网域名，协议已限定为 http(s)
            return normalized;
        }
        // 相对路径不允许出现协议或盘符写法（如 file:、C:）
        if (StringUtils.contains(normalized, ":")) {
            throw new ServiceException("资源文件非法，不允许下载。");
        }
        // 折叠重复斜杠，保留 /profile 前缀的语义（存储层据此还原对象键）
        normalized = normalized.replaceAll("/{2,}", "/");
        if (StringUtils.startsWith(normalized, Constants.RESOURCE_PREFIX + "/")) {
            return normalized;
        }
        return StringUtils.stripStart(normalized, "/");
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
