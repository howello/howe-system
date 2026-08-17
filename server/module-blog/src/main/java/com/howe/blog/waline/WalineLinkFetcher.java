package com.howe.blog.waline;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.howe.blog.util.UrlGuard;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * waline 评论 HTTP 客户端
 *
 * <p>拉取友链申请留言（path=/links）。端点地址、超时、pageSize 从参数配置表读取。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class WalineLinkFetcher {

    /** path 固定为友链申请留言所在路径 */
    private static final String FIXED_PATH = "/links";

    /** sortBy 固定按时间降序 */
    private static final String FIXED_SORT = "insertedAt_desc";

    /** 默认超时 */
    private static final int DEFAULT_TIMEOUT = 30000;

    /** 默认 pageSize */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 拉取指定页的友链申请评论
     *
     * @param page 页码（从 1 开始）
     * @return JSON 响应体字符串
     */
    public String fetch(int page) {
        String endpoint = ConfigUtils.getRequired(ConfigConstants.BLOG_WALINE_URL, "waline 评论端点");
        int pageSize = ConfigUtils.getInt(ConfigConstants.BLOG_WALINE_PAGE_SIZE, DEFAULT_PAGE_SIZE);
        int timeout = ConfigUtils.getInt(ConfigConstants.BLOG_WALINE_TIMEOUT, DEFAULT_TIMEOUT);

        // 端点来自参数配置表（管理员填写），需 SSRF 防护
        UrlGuard.assertFetchable(endpoint);

        String url = endpoint + "?path=" + FIXED_PATH
                + "&pageSize=" + pageSize
                + "&page=" + page
                + "&lang=zh-CN"
                + "&sortBy=" + FIXED_SORT;

        try (HttpResponse response = HttpRequest.get(url)
                .header(Header.USER_AGENT, "HoweBlogBot/1.0")
                .timeout(timeout)
                .execute()) {
            if (!response.isOk()) {
                throw new ServiceException("waline 接口返回 HTTP " + response.getStatus());
            }
            String body = response.body();
            if (StrUtil.isBlank(body)) {
                throw new ServiceException("waline 响应为空");
            }
            return body;
        } catch (ServiceException e) {
            // 业务异常（HTTP 非 2xx、响应为空）直接放行，避免消息重复叠加
            throw e;
        } catch (Exception e) {
            // 网络/解码等底层异常：末参传异常本身，SLF4J 才会打印完整堆栈
            log.error("拉取 waline 评论失败，page={}，url={}", page, url, e);
            throw new ServiceException("拉取 waline 评论失败：" + e.getMessage());
        }
    }
}
