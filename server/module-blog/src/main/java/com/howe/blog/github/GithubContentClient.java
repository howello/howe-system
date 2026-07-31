package com.howe.blog.github;

import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GitHub Contents API 客户端
 *
 * <p>
 * 仓库地址、令牌等都从「系统管理 &gt; 参数设置」读，改完即时生效。所有写操作都要带上
 * blob sha，GitHub 用它做乐观锁——sha 过期会返回 409，说明文件在别处被改过。
 * </p>
 *
 * @author howe
 */
@Component
public class GithubContentClient
{
    private static final Logger log = LoggerFactory.getLogger(GithubContentClient.class);

    /** GitHub Contents API 单文件返回内容的上限，超过需改用 blobs API */
    private static final long CONTENTS_API_SIZE_LIMIT = 1024 * 1024L;

    /** 默认请求超时（毫秒） */
    private static final int DEFAULT_TIMEOUT = 30000;

    /**
     * 配置是否完整可用
     *
     * @return true 表示 owner/repo/token 都已配置
     */
    public boolean isConfigured()
    {
        return StringUtils.isNotEmpty(ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_OWNER))
                && StringUtils.isNotEmpty(ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_REPO))
                && StringUtils.isNotEmpty(ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_TOKEN));
    }

    /**
     * 校验配置，缺失则抛出可读的业务异常
     */
    public void assertConfigured()
    {
        if (!isConfigured())
        {
            throw new ServiceException("GitHub 仓库未配置，请到「系统管理 > 参数设置」补齐 "
                    + ConfigConstants.BLOG_GITHUB_OWNER + " / " + ConfigConstants.BLOG_GITHUB_REPO + " / "
                    + ConfigConstants.BLOG_GITHUB_TOKEN);
        }
    }

    /**
     * 文章目录，相对仓库根，末尾不带斜杠
     *
     * @return 文章目录
     */
    public String getContentDir()
    {
        return StringUtils.stripEnd(ConfigUtils.getString(ConfigConstants.BLOG_CONTENT_DIR, "src/content/blog"), "/");
    }

    /**
     * 目标分支
     *
     * @return 分支名
     */
    public String getBranch()
    {
        return ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_BRANCH, "main");
    }

    /**
     * 列出内容目录下的所有 markdown 文件
     *
     * <p>
     * 走 Git Trees API 的 recursive 模式，一次请求拿到整棵树，避免按目录逐层遍历。
     * </p>
     *
     * @return 文件条目列表
     */
    public List<GithubTreeItem> listMarkdownFiles()
    {
        assertConfigured();
        String url = repoApi("/git/trees/" + encodeSegment(getBranch())) + "?recursive=1";
        JSONObject body = getJson(url, "读取仓库文件树");
        if (Boolean.TRUE.equals(body.getBoolean("truncated")))
        {
            log.warn("GitHub 文件树被截断，仓库文件数超过单次返回上限，同步结果可能不完整");
        }
        String dirPrefix = getContentDir() + "/";
        JSONArray tree = body.getJSONArray("tree");
        List<GithubTreeItem> items = new ArrayList<>();
        if (tree == null)
        {
            return items;
        }
        for (int i = 0; i < tree.size(); i++)
        {
            JSONObject node = tree.getJSONObject(i);
            if (!"blob".equals(node.getString("type")))
            {
                continue;
            }
            String path = node.getString("path");
            if (path == null || !path.startsWith(dirPrefix) || !isMarkdown(path))
            {
                continue;
            }
            Long size = node.getLong("size");
            items.add(new GithubTreeItem(path, node.getString("sha"), size == null ? 0L : size));
        }
        return items;
    }

    /**
     * 读取单个文件
     *
     * @param path 相对仓库根的路径
     * @return 文件内容；文件不存在时返回 null
     */
    public GithubFile getFile(String path)
    {
        assertConfigured();
        String url = repoApi("/contents/" + encodePath(path)) + "?ref=" + encodeSegment(getBranch());
        String responseBody;
        try (HttpResponse response = request(Method.GET, url).execute())
        {
            if (response.getStatus() == 404)
            {
                return null;
            }
            ensureSuccess(response, "读取文件 " + path);
            responseBody = response.body();
        }
        JSONObject body = JSONObject.parseObject(responseBody);
        Long size = body.getLong("size");
        long fileSize = size == null ? 0L : size;
        String sha = body.getString("sha");
        String content = decodeContent(body);
        if (content == null && fileSize > CONTENTS_API_SIZE_LIMIT)
        {
            // 超过 1MB 时 contents 接口不带 content 字段，改用 blobs 接口按 sha 取
            content = getBlob(sha);
        }
        return new GithubFile(body.getString("path"), sha, content, fileSize);
    }

    /**
     * 新建或更新文件
     *
     * @param path 相对仓库根的路径
     * @param content 文件原文
     * @param message 提交信息
     * @param sha 目标文件当前的 blob sha；新建时传 null
     * @return 提交结果
     */
    public GithubCommit putFile(String path, String content, String message, String sha)
    {
        assertConfigured();
        JSONObject payload = new JSONObject();
        payload.put("message", message);
        payload.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        payload.put("branch", getBranch());
        if (StringUtils.isNotEmpty(sha))
        {
            payload.put("sha", sha);
        }
        putCommitter(payload);

        JSONObject body = sendJson(Method.PUT, repoApi("/contents/" + encodePath(path)), payload, "写入文件 " + path);
        JSONObject commit = body.getJSONObject("commit");
        JSONObject fileNode = body.getJSONObject("content");
        return new GithubCommit(commit == null ? null : commit.getString("sha"),
                fileNode == null ? null : fileNode.getString("sha"));
    }

    /**
     * 删除文件
     *
     * @param path 相对仓库根的路径
     * @param message 提交信息
     * @param sha 目标文件当前的 blob sha，必填
     * @return 提交结果
     */
    public GithubCommit deleteFile(String path, String message, String sha)
    {
        assertConfigured();
        if (StringUtils.isEmpty(sha))
        {
            throw new ServiceException("删除文件需要文件的 sha，请先同步文章索引");
        }
        JSONObject payload = new JSONObject();
        payload.put("message", message);
        payload.put("branch", getBranch());
        payload.put("sha", sha);
        putCommitter(payload);

        JSONObject body = sendJson(Method.DELETE, repoApi("/contents/" + encodePath(path)), payload, "删除文件 " + path);
        JSONObject commit = body.getJSONObject("commit");
        return new GithubCommit(commit == null ? null : commit.getString("sha"), null);
    }

    /**
     * 判断是否是 markdown 文件
     *
     * @param path 文件路径
     * @return 是否是 markdown
     */
    public static boolean isMarkdown(String path)
    {
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".mdx");
    }

    /**
     * 按 sha 读取大文件内容
     */
    private String getBlob(String sha)
    {
        JSONObject body = getJson(repoApi("/git/blobs/" + encodeSegment(sha)), "读取文件内容");
        return decodeContent(body);
    }

    private JSONObject getJson(String url, String action)
    {
        try (HttpResponse response = request(Method.GET, url).execute())
        {
            ensureSuccess(response, action);
            return JSONObject.parseObject(response.body());
        }
    }

    private JSONObject sendJson(Method method, String url, JSONObject payload, String action)
    {
        try (HttpResponse response = request(method, url).body(payload.toJSONString()).execute())
        {
            ensureSuccess(response, action);
            return JSONObject.parseObject(response.body());
        }
    }

    /**
     * 构造带鉴权头的请求
     */
    private HttpRequest request(Method method, String url)
    {
        int timeout = ConfigUtils.getInt(ConfigConstants.BLOG_GITHUB_TIMEOUT, DEFAULT_TIMEOUT);
        return HttpRequest.of(url).method(method)
                .header("Authorization", "Bearer " + ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_TOKEN))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "howe-blog-admin")
                .timeout(timeout);
    }

    /**
     * 解码 base64 内容
     *
     * <p>
     * GitHub 返回的 base64 每 60 字符换行，必须用 MIME 解码器。
     * </p>
     */
    private static String decodeContent(JSONObject body)
    {
        String encoded = body.getString("content");
        if (StringUtils.isEmpty(encoded))
        {
            return null;
        }
        if (!"base64".equals(body.getString("encoding")))
        {
            return encoded;
        }
        return new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private void putCommitter(JSONObject payload)
    {
        String name = ConfigUtils.getString(ConfigConstants.BLOG_COMMITTER_NAME);
        String email = ConfigUtils.getString(ConfigConstants.BLOG_COMMITTER_EMAIL);
        if (StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(email))
        {
            JSONObject committer = new JSONObject();
            committer.put("name", name);
            committer.put("email", email);
            payload.put("committer", committer);
        }
    }

    private void ensureSuccess(HttpResponse response, String action)
    {
        int status = response.getStatus();
        if (status >= 200 && status < 300)
        {
            return;
        }
        String body = response.body();
        String detail = extractError(body);
        log.error("GitHub 接口调用失败，action={}，status={}，body={}", action, status, body);
        if (status == 401 || status == 403)
        {
            throw new ServiceException("GitHub 令牌无效或权限不足（" + action + "）：" + detail);
        }
        if (status == 409)
        {
            throw new ServiceException("文件已被其他提交修改（" + action + "），请重新同步后再试");
        }
        if (status == 422)
        {
            throw new ServiceException("GitHub 拒绝了本次提交（" + action + "）：" + detail);
        }
        throw new ServiceException("GitHub 接口返回 " + status + "（" + action + "）：" + detail);
    }

    private static String extractError(String body)
    {
        if (StringUtils.isEmpty(body))
        {
            return "无响应内容";
        }
        try
        {
            String message = JSONObject.parseObject(body).getString("message");
            return StringUtils.isNotEmpty(message) ? message : body;
        }
        catch (Exception e)
        {
            return body;
        }
    }

    private String repoApi(String suffix)
    {
        String apiBase = StringUtils.stripEnd(
                ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_API_BASE, "https://api.github.com"), "/");
        return apiBase + "/repos/" + encodeSegment(ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_OWNER)) + "/"
                + encodeSegment(ConfigUtils.getString(ConfigConstants.BLOG_GITHUB_REPO)) + suffix;
    }

    /**
     * 逐段编码路径，保留斜杠分隔（文章文件名可能含中文）
     */
    private static String encodePath(String path)
    {
        return Arrays.stream(path.split("/")).map(GithubContentClient::encodeSegment).collect(Collectors.joining("/"));
    }

    private static String encodeSegment(String segment)
    {
        return URLUtil.encodeAll(segment, StandardCharsets.UTF_8);
    }
}
