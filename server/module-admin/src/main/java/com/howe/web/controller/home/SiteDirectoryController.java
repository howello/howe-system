package com.howe.web.controller.home;

import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.LimitType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点导航 控制层
 *
 * <p>供根路径导航页（{@code static/index.html}）加载站点清单与健康状态。</p>
 *
 * <p>
 * <b>为什么匿名</b>：导航页本身是 {@code GET /}，访问者没有登录态。
 * 如果这个接口要求认证，页面就只能展示静态清单、拿不到健康状态。
 * 回传内容只有站点名、公开地址、状态码与耗时——这些信息与直接访问
 * 那些站点能看到的内容等价，不构成额外泄露。
 * </p>
 *
 * @author howe
 */
@Tag(name = "站点导航", description = "根路径导航页的站点清单与健康探测")
@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
public class SiteDirectoryController extends BaseController
{
    private final SiteDirectoryService siteDirectoryService;

    /**
     * 站点清单
     *
     * @return 站点列表（不含探测结果，秒回）
     */
    @Anonymous
    @RateLimiter(time = 60, count = 120, limitType = LimitType.IP)
    @Operation(summary = "站点清单", description = "返回全部站点的名称、地址与分组；不发起探测，响应很快")
    @GetMapping("/list")
    public AjaxResult list()
    {
        List<SiteDirectoryService.Site> sites = siteDirectoryService.sites();
        List<Map<String, Object>> rows = sites.stream().map(site -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", site.key());
            row.put("name", site.name());
            row.put("url", site.url());
            row.put("desc", site.desc());
            row.put("group", site.group());
            row.put("note", site.note());
            return row;
        }).toList();
        return success(rows);
    }

    /**
     * 健康探测
     *
     * <p>
     * 并发探测全部站点，整体 5 秒封顶。限流比 {@code /list} 更严：
     * 每次调用会产生 18 个外部请求，被刷就成了打自己服务的放大器。
     * </p>
     *
     * @return 各站点状态
     */
    @Anonymous
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @Operation(summary = "站点健康探测", description = "并发探测全部站点，整体 5 秒封顶；只返回状态码与耗时，不回传响应体")
    @GetMapping("/health")
    public AjaxResult health()
    {
        List<SiteDirectoryService.ProbeResult> results = siteDirectoryService.probeAll();
        List<Map<String, Object>> rows = results.stream().map(result -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", result.key());
            row.put("status", result.status());
            row.put("httpStatus", result.httpStatus());
            row.put("costMs", result.costMs());
            row.put("reason", result.reason());
            return row;
        }).toList();
        return success(rows);
    }
}
