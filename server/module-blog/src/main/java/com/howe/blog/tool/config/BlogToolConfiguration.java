package com.howe.blog.tool.config;

import com.howe.blog.service.IBlogArticleService;
import com.howe.blog.service.IBlogDraftService;
import com.howe.blog.service.IBlogTalkService;
import com.howe.blog.tool.provider.BlogArticleToolProvider;
import com.howe.blog.tool.provider.BlogReadToolProvider;
import com.howe.common.tool.spi.BusinessToolProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安装 MCP 时装配博客工具提供者。
 *
 * <p>未安装时不创建 Provider，也不查询任何 MCP 表；读取 {@code howe.mcp.installed}
 * 配置键不需要引用 MCP Java 类型。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class BlogToolConfiguration {

    /**
     * 注册博客文章 Provider，注入 Spring 代理以保留事务和切面。
     *
     * @param articles 博客文章服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpBlogArticleTools(IBlogArticleService articles) {
        return new BlogArticleToolProvider(articles);
    }

    /**
     * 注册 C2 新增的博客只读 Provider。
     *
     * <p>只发布文章详情、草稿与说说工具，不重复注册 {@code blog_article_list}。</p>
     *
     * @param articles 文章服务
     * @param drafts 草稿服务
     * @param talks 说说服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpBlogReadTools(IBlogArticleService articles, IBlogDraftService drafts,
            IBlogTalkService talks) {
        return new BlogReadToolProvider(articles, drafts, talks);
    }

    /**
     * 注册说说写工具；只依赖 common 操作网关，不引用 MCP 平台类型。
     *
     * <p>草稿相关写工具本期不开放，因此不注册草稿 Provider 或 Handler。</p>
     *
     * @param gateway 操作网关
     * @return 写工具提供者
     */
    @Bean
    public BusinessToolProvider mcpBlogTalkWriteTools(com.howe.common.tool.operation.ToolOperationGateway gateway) {
        return new com.howe.blog.tool.provider.BlogTalkWriteToolProvider(gateway);
    }

    /**
     * 注册说说创建操作处理器。
     *
     * @param talks 说说服务
     * @return 操作处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpBlogTalkCreateHandler(IBlogTalkService talks) {
        return new com.howe.blog.tool.operation.BlogTalkWriteHandler("blog_talk_create", talks);
    }

    /**
     * 注册说说更新操作处理器。
     *
     * @param talks 说说服务
     * @return 操作处理器
     */
    @Bean
    public com.howe.common.tool.operation.ToolOperationHandler mcpBlogTalkUpdateHandler(IBlogTalkService talks) {
        return new com.howe.blog.tool.operation.BlogTalkWriteHandler("blog_talk_update", talks);
    }
}
