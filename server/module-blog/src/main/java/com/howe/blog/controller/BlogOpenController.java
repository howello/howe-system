package com.howe.blog.controller;

import com.howe.blog.domain.dto.BlogArticlePublishBody;
import com.howe.blog.domain.vo.BlogPublishResult;
import com.howe.blog.service.IBlogArticleService;
import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.LimitType;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.sign.ConstantTimeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客开放接口
 *
 * <p>
 * 给站外工具（本地 agent、脚本、CI）投稿用：一次请求提交整篇文章，服务端拼好 frontmatter
 * 提交到 GitHub 仓库，再更新本地索引——与后台管理页走的是同一条写入链路。
 * </p>
 *
 * <p>
 * 接口是匿名的，靠请求头 {@code X-Blog-Token} 与参数配置里的令牌比对来放行。
 * <b>令牌没配就等于没开启，一律拒绝</b>，不给出裸奔的写入口；比对用定长算法，避免逐位试探。
 * </p>
 *
 * @author howe
 */
@Slf4j
@Tag(name = "博客开放接口", description = "供站外 agent 调用的文章发布接口")
@RestController
@RequestMapping("/blog/open")
public class BlogOpenController extends BaseController
{
    /** 令牌请求头 */
    private static final String TOKEN_HEADER = "X-Blog-Token";

    @Autowired
    private IBlogArticleService blogArticleService;

    /**
     * 发布整篇文章
     *
     * @param token 请求头里的开放接口令牌
     * @param body 文章内容
     * @return 发布结果
     */
    @Operation(summary = "发布文章",
            description = "一次性提交标题、分类、标签与正文。匿名接口，需在请求头 X-Blog-Token 带上令牌")
    @Anonymous
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/article")
    public AjaxResult publish(
            @Parameter(description = "开放接口令牌", required = true)
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody BlogArticlePublishBody body)
    {
        AjaxResult denied = checkToken(token);
        if (denied != null)
        {
            return denied;
        }
        BlogPublishResult result = blogArticleService.publishArticle(body);
        log.info("开放接口发布文章成功：slug={}，新建={}", result.slug(), result.created());
        return AjaxResult.success(result.created() ? "发布成功" : "覆盖成功", result);
    }

    /**
     * 校验开放接口令牌
     *
     * @return 校验不通过时返回错误结果，通过则返回 {@code null}
     */
    private AjaxResult checkToken(String token)
    {
        if (!ConfigUtils.getBoolean(ConfigConstants.BLOG_OPEN_ENABLED, false))
        {
            log.warn("收到开放接口请求但开关未打开，已拒绝");
            return AjaxResult.error("博客开放接口未启用");
        }
        String expected = ConfigUtils.getString(ConfigConstants.BLOG_OPEN_TOKEN);
        if (StringUtils.isEmpty(expected))
        {
            log.warn("收到开放接口请求但未配置令牌，已拒绝");
            return AjaxResult.error("博客开放接口未启用");
        }
        if (!ConstantTimeUtils.equals(expected, StringUtils.trim(token)))
        {
            log.warn("开放接口令牌校验失败");
            return AjaxResult.error("令牌校验失败");
        }
        return null;
    }
}
