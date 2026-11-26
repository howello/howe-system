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

    /** 按分类关键词查图。 */
    @GetMapping("/images")
    @RateLimiter(time = 60, count = 12, limitType = LimitType.IP)
    @Operation(summary = "检索配图")
    public AjaxResult images(@RequestParam String q,
        @RequestParam(defaultValue = "auto") String source) {
        return AjaxResult.success(search.images(q, source));
    }
}
