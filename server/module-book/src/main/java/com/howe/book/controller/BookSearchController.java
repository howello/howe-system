package com.howe.book.controller;

import com.howe.book.service.BookSearchService;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.LimitType;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 书目与分类配图检索。 */
@RestController
@RequestMapping("/book/search")
@RequiredArgsConstructor
public class BookSearchController {
    private final BookSearchService search;

    /** 按书名、作者或 ISBN 查询。 */
    @GetMapping("/catalog")
    @RateLimiter(time = 60, count = 12, limitType = LimitType.IP)
    @Operation(summary = "检索书目")
    public AjaxResult catalog(@RequestParam String q, @RequestParam(defaultValue = "douban-book") String source) {
        return AjaxResult.success(search.catalog(q, source));
    }

    /** 读取选中的微信读书书目及封面信息。 */
    @GetMapping("/weread-book")
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @Operation(summary = "查询微信读书书目详情")
    public AjaxResult weReadBook(@RequestParam String bookId, @RequestParam String searchToken) {
        return AjaxResult.success(search.weReadBook(bookId, searchToken));
    }

    /** 按分类关键词查图。 */
    @GetMapping("/images")
    @RateLimiter(time = 60, count = 12, limitType = LimitType.IP)
    @Operation(summary = "检索配图")
    public AjaxResult images(@RequestParam String q,
        @RequestParam(defaultValue = "auto") String source) {
        return AjaxResult.success(search.images(q, source));
    }
}
