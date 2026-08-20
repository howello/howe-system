package com.howe.blog.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.StrUtil;
import com.howe.blog.domain.BlogLink;
import com.howe.blog.mapper.BlogLinkMapper;
import com.howe.blog.service.WalineLinkSyncService;
import com.howe.blog.waline.WalineLinkFetcher;
import com.howe.blog.waline.WalineLinkParser;
import com.howe.common.task.TaskLogContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * waline 友链同步服务实现
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalineLinkSyncServiceImpl implements WalineLinkSyncService {

    /** 类型：友链 */
    private static final String TYPE_LINK = "1";

    /** 默认窗口分钟数 */
    private static final int DEFAULT_WINDOW_MINUTES = 30;

    /** 匿名写入时的操作者标记 */
    private static final String OPERATOR = "waline-sync";

    /** 分页上限：防止 waline 数据异常导致无限翻页 */
    private static final int MAX_PAGES = 100;

    private final WalineLinkFetcher walineLinkFetcher;
    private final BlogLinkMapper blogLinkMapper;

    @Override
    public WalineLinkSyncResult sync(Integer windowMinutes) {
        int window = (windowMinutes == null || windowMinutes <= 0) ? DEFAULT_WINDOW_MINUTES : windowMinutes;
        long startMillis = System.currentTimeMillis() - window * 60_000L;

        int newCount = 0;
        int skipCount = 0;
        int page = 1;
        boolean shouldStop = false;

        try (TaskLogContext.TaskStep step = TaskLogContext.startStep("翻页拉取评论列表")) {
            while (page <= MAX_PAGES && !shouldStop) {
                List<WalineLinkParser.WalineLinkItem> items;
                try {
                    String json = walineLinkFetcher.fetch(page);
                    // 解析也放在同一个 try 内：waline 返回 errno≠0 或反代返回 HTML 错误页时，
                    // 页级失败应与拉取失败一样 break 并正常收尾，不能掀翻整个 sync 丢掉汇总与返回值
                    items = WalineLinkParser.parseResponse(json);
                    step.info("拉取成功，条数：{}", items.size());
                } catch (Exception e) {
                    step.fail("拉取或解析第" + page + "页失败", e);
                    break;
                }

                if (items.isEmpty()) {
                    step.info("第{}页无数据，停止翻页", page);
                    break;
                }

                for (WalineLinkParser.WalineLinkItem item : items) {
                    // sortBy=insertedAt_desc 降序，遇到窗口外的停止翻页
                    if (item.time() < startMillis) {
                        step.info("遇到窗口外评论（time={}），停止翻页", DateTime.of(item.time()));
                        shouldStop = true;
                        skipCount++;
                        continue;
                    }

                    // 只处理已批准的评论
                    if (!"approved".equals(item.status())) {
                        step.info("跳过非 approved 评论：status={}", item.status());
                        skipCount++;
                        continue;
                    }

                    // 解析 orig 四字段
                    Map<String, String> fields = WalineLinkParser.parseOrig(item.orig());
                    String name = fields.get("name");
                    String link = fields.get("link");
                    if (StrUtil.isBlank(name) || StrUtil.isBlank(link)) {
                        step.info("评论缺少 name 或 link，跳过：{}", item.orig());
                        skipCount++;
                        continue;
                    }

                    // 按 link_url 去重：已存在则跳过（保留人工编辑）
                    BlogLink existing = blogLinkMapper.selectBlogLinkByUrl(link, TYPE_LINK);
                    if (existing != null) {
                        step.info("友链已存在（link={}），跳过", link);
                        skipCount++;
                        continue;
                    }

                    // 插入新友链
                    BlogLink blogLink = new BlogLink();
                    blogLink.setLinkType(TYPE_LINK);
                    blogLink.setLinkName(name);
                    blogLink.setLinkUrl(link);
                    blogLink.setAvatar(fields.get("avatar"));
                    blogLink.setDescr(fields.get("desc"));
                    blogLink.setStatus("0");
                    blogLink.setOrderNum(0);
                    blogLink.setCreateBy(OPERATOR);

                    try {
                        blogLinkMapper.insertBlogLink(blogLink);
                        newCount++;
                        step.info("新增友链：{} ({})", name, link);
                    } catch (Exception e) {
                        step.info("写入友链失败：{}", e.getMessage());
                        skipCount++;
                    }
                }

                page++;
            }
            if (page > MAX_PAGES) {
                step.info("翻页达到上限{}，强制停止", MAX_PAGES);
            }

            step.success("waline 友链同步完成：新增{}条，跳过{}条，翻{}页", newCount, skipCount, page - 1);
        }
        return new WalineLinkSyncResult(newCount, skipCount, page - 1);
    }
}
