package com.howe.blog.rss;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * RSS/Atom 抓取器
 *
 * <p>只负责把远端订阅源取回成<b>字节数组</b>，不做任何解码。
 * 返回字符串会毁掉 GBK 等非 UTF-8 的源——只有 ROME 的 XmlReader 能从 XML 声明
 * 识别出真实编码，提前 decode 就再也救不回来了。</p>
 *
 * <p>抓取目标是不受控的第三方站点，因此超时、体积上限、UA 都做成可配置项。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class RssFetcher {

    /** 参数未配置时的兜底 UA，部分站点会拒绝默认 UA */
    private static final String DEFAULT_UA = "Mozilla/5.0 (compatible; HoweBlogBot/1.0)";

    /** 单次读取缓冲区 */
    private static final int BUFFER_SIZE = 8192;

    /** 最多跟随的重定向跳数 */
    private static final int MAX_REDIRECTS = 5;

    /**
     * 抓取订阅源原始字节
     *
     * @param rssUrl 订阅地址
     * @return 响应体字节
     */
    public byte[] fetch(String rssUrl) {
        int timeout = ConfigUtils.getInt(ConfigConstants.BLOG_FEED_TIMEOUT, 30000);
        int maxSize = ConfigUtils.getInt(ConfigConstants.BLOG_FEED_MAX_SIZE, 5242880);
        String ua = StrUtil.blankToDefault(ConfigUtils.getString(ConfigConstants.BLOG_FEED_USER_AGENT), DEFAULT_UA);

        String target = rssUrl;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            // 每一跳都要重新校验目标：交给 hutool 自动跟随的话，源站只要 302 到
            // http://127.0.0.1:xxx 或云元数据地址，请求就从应用服务器内部发出去了，
            // 入库时的静态校验完全拦不住。所以这里关掉自动重定向、自己走。
            UrlGuard.assertFetchable(target);
            // executeAsync 不会先把整个响应体读进内存，配合下面的封顶读取才能真正挡住超大响应
            try (HttpResponse response = HttpRequest.get(target)
                    .header(Header.USER_AGENT, ua)
                    .timeout(timeout)
                    .setFollowRedirects(false)
                    .executeAsync()) {
                if (isRedirect(response.getStatus())) {
                    String location = response.header(Header.LOCATION);
                    if (StrUtil.isBlank(location)) {
                        throw new ServiceException("HTTP " + response.getStatus() + " 但缺少 Location");
                    }
                    // Location 可能是相对路径，按当前地址解析成绝对地址
                    target = URI.create(target).resolve(location.trim()).toString();
                    continue;
                }
                if (!response.isOk()) {
                    throw new ServiceException("HTTP " + response.getStatus());
                }
                byte[] body = readLimited(response.bodyStream(), maxSize);
                if (body.length == 0) {
                    throw new ServiceException("响应为空");
                }
                return body;
            } catch (IllegalArgumentException e) {
                throw new ServiceException("重定向地址不合法：" + e.getMessage());
            }
        }
        throw new ServiceException("重定向次数超过上限 " + MAX_REDIRECTS);
    }

    /**
     * 是否是重定向响应
     *
     * @param status HTTP 状态码
     * @return 结果
     */
    private boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    /**
     * 封顶读取：超过上限立即中断，不把整个响应读完再判断
     *
     * @param in      响应流
     * @param maxSize 字节上限
     * @return 响应体字节
     */
    private byte[] readLimited(InputStream in, int maxSize) {
        if (in == null) {
            throw new ServiceException("响应为空");
        }
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (out.size() + read > maxSize) {
                    throw new ServiceException("响应体超过上限 " + maxSize + " 字节");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ServiceException("读取响应失败：" + e.getMessage());
        }
    }
}
