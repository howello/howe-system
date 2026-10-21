package com.howe.web.controller.home;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 站点目录与健康探测服务
 *
 * <p>
 * 根路径导航页（{@code static/index.html}）的条目与实时状态都来自这里。
 * 站点清单写死在代码里而不是参数表：它是「这台机器上跑了什么」的静态描述，
 * 改一次就要重新部署，放参数表反而多一层要同步的东西。
 * </p>
 *
 * <p>
 * <b>为什么探测在后端做而不是前端 JS 直接 fetch</b>：跨域请求会被浏览器拦下，
 * 前端拿到的永远是 CORS 错误而不是真实状态；而且部分服务（如 3xui 面板）
 * 只对特定来源放行，浏览器探测必然误报。后端发 HTTP 请求没有同源限制。
 * </p>
 *
 * <p>
 * <b>安全性</b>：被探测的地址全部来自本类里的常量，不接受任何外部输入，
 * 因此不存在 SSRF 面。响应只回传「状态码 / 耗时 / 可达性」，不回传响应体，
 * 避免把内网服务的页面内容带出去。
 * </p>
 *
 * @author howe
 */
@Slf4j
@Service
public class SiteDirectoryService
{
    /** 单个站点探测超时（毫秒） */
    private static final int PROBE_TIMEOUT_MS = 3000;

    /** 整批探测的总超时（毫秒）；超时的站点按「不可达」处理 */
    private static final long PROBE_BATCH_TIMEOUT_MS = 5000L;

    /** 探测并发度：站点数量有限，固定 8 个线程足够 */
    private static final int PROBE_THREADS = 8;

    /** 状态：正常 */
    public static final String STATUS_OK = "ok";

    /** 状态：响应异常（连上了但状态码不是 2xx/3xx） */
    public static final String STATUS_DEGRADED = "degraded";

    /** 状态：不可达 */
    public static final String STATUS_DOWN = "down";

    /**
     * 站点定义
     *
     * @param key     稳定标识，前端据此做筛选
     * @param name    展示名
     * @param url     访问地址（同时作为探测目标）
     * @param desc    一句话说明
     * @param group   分组：site / tool / service / admin
     * @param note    补充说明，如「不可达」这类已知状态
     */
    public record Site(String key, String name, String url, String desc, String group, String note)
    {
    }

    /**
     * 站点清单
     *
     * <p>
     * 顺序即展示顺序。分组含义：
     * </p>
     * <ul>
     *   <li>{@code site} 对外站点（博客）</li>
     *   <li>{@code tool} 自建工具（Git、看板、小游戏、邮件、点餐、评论、一言）</li>
     *   <li>{@code service} 中转与基础设施（AI 中转、图床、Docker、NPM、分享）</li>
     *   <li>{@code admin} 管理入口</li>
     * </ul>
     */
    private static final List<Site> SITES = List.of(
            new Site("blog", "博客", "https://www.wyantao.com/", "技术笔记与随笔", "site", ""),
            new Site("blog-apex", "博客（主域）", "https://wyantao.com/", "同上，裸域跳转", "site", ""),
            new Site("admin", "管理面板", "https://admin.wyantao.com/", "内容、AI、点餐与文件分享", "admin", ""),
            new Site("meal", "在线点餐", "https://meal.wyantao.com/", "家庭点餐 H5", "admin", ""),
            new Site("gitea", "自建 Git", "https://gitea.wyantao.com/", "代码仓库与镜像", "tool", ""),
            new Site("docker", "Dockhub", "https://docker.wyantao.com/", "镜像与容器入口", "tool", ""),
            new Site("game", "小游戏", "https://game.wyantao.com/", "在线小游戏", "tool", ""),
            new Site("mail", "邮件", "https://mail.wyantao.com/", "邮件服务", "tool", ""),
            new Site("waline", "评论", "https://waline.wyantao.com/", "博客评论系统", "tool", ""),
            new Site("hitokoto", "一言", "https://hitokoto.wyantao.com/", "随机句子 API", "tool", ""),
            new Site("ai", "AI 中转", "https://ai.wyantao.com/", "AI 接口中转站", "service", ""),
            new Site("newapi", "NewAPI 中转", "https://newapi.wyantao.com/", "AI 接口中转站", "service", ""),
            new Site("gpt", "GPT 中转", "https://gpt.wyantao.com/", "GPT 接口中转", "service", ""),
            new Site("cbc", "CBC2API 中转", "https://cbc.wyantao.com/", "CBC 接口中转", "service", ""),
            new Site("cpa", "CPA 中转", "https://cpa.wyantao.com/", "CPA 接口中转", "service", ""),
            new Site("npm", "NPM 转发", "https://npm.wyantao.com/", "Nginx 转发", "service", ""),
            new Site("share", "文件分享", "https://s.wyantao.com/", "WebDAV 文件分享短链", "service", ""),
            new Site("3xui", "3xui 面板", "https://3xui.wyantao.com/", "面板入口", "admin", "已知不可达"));

    /**
     * 探测单个站点
     *
     * @param site 站点
     * @return 探测结果
     */
    public ProbeResult probe(Site site)
    {
        long start = System.currentTimeMillis();
        try (HttpResponse response = HttpRequest.get(site.url())
                .timeout(PROBE_TIMEOUT_MS)
                .setFollowRedirects(true)
                .execute())
        {
            long cost = System.currentTimeMillis() - start;
            int status = response.getStatus();
            // 2xx / 3xx 视为正常；4xx 说明服务在但入口有鉴权，算「可达但受限」
            String state = status < 400 ? STATUS_OK : STATUS_DEGRADED;
            return new ProbeResult(site.key(), state, status, cost, "");
        }
        catch (Exception e)
        {
            long cost = System.currentTimeMillis() - start;
            log.debug("站点探测失败：{} {}", site.url(), e.getMessage());
            return new ProbeResult(site.key(), STATUS_DOWN, 0, cost, simplifyError(e));
        }
    }

    /**
     * 并发探测全部站点
     *
     * <p>
     * 串行探测最坏要 18 × 3s ≈ 54s，页面早就超时了。这里并发发出、整体
     * 5 秒封顶，超时未返回的站点按「不可达」处理，保证页面加载时间可控。
     * </p>
     *
     * @return 探测结果列表，顺序与 {@link #sites()} 一致
     */
    public List<ProbeResult> probeAll()
    {
        List<Site> sites = sites();
        ExecutorService pool = Executors.newFixedThreadPool(PROBE_THREADS, r -> {
            Thread t = new Thread(r, "site-probe");
            t.setDaemon(true);
            return t;
        });
        try
        {
            List<CompletableFuture<ProbeResult>> futures = new ArrayList<>(sites.size());
            for (Site site : sites)
            {
                futures.add(CompletableFuture.supplyAsync(() -> probe(site), pool));
            }
            List<ProbeResult> results = new ArrayList<>(sites.size());
            for (int i = 0; i < futures.size(); i++)
            {
                try
                {
                    results.add(futures.get(i).get(PROBE_BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                }
                catch (Exception e)
                {
                    results.add(new ProbeResult(sites.get(i).key(), STATUS_DOWN, 0, PROBE_BATCH_TIMEOUT_MS, "探测超时"));
                }
            }
            return results;
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    /**
     * 取站点清单
     *
     * @return 站点列表
     */
    public List<Site> sites()
    {
        return SITES;
    }

    /**
     * 把异常压缩成一句可读的原因
     *
     * @param e 异常
     * @return 简短描述
     */
    private String simplifyError(Exception e)
    {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank())
        {
            return e.getClass().getSimpleName();
        }
        if (msg.contains("Connection refused"))
        {
            return "连接被拒绝";
        }
        if (msg.contains("UnknownHost") || msg.contains("unresolved"))
        {
            return "域名解析失败";
        }
        if (msg.contains("timed out") || msg.contains("timeout") || msg.contains("Timeout"))
        {
            return "连接超时";
        }
        if (msg.contains("PKIX") || msg.contains("SSL") || msg.contains("certificate"))
        {
            return "证书校验失败";
        }
        return msg.length() > 60 ? msg.substring(0, 60) + "…" : msg;
    }

    /**
     * 单个站点的探测结果
     *
     * @param key 站点标识
     * @param status ok / degraded / down
     * @param httpStatus HTTP 状态码，未连上时为 0
     * @param costMs 耗时（毫秒）
     * @param reason 不可达原因，正常时为空串
     */
    public record ProbeResult(String key, String status, int httpStatus, long costMs, String reason)
    {
    }
}
