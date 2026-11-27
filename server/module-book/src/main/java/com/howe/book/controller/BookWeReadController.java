package com.howe.book.controller;

import com.howe.book.service.BookWeReadSyncService;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 微信读书同步入口。 */
@RestController
@RequestMapping("/book/weread/sync")
@RequiredArgsConstructor
public class BookWeReadController {
    private final BookWeReadSyncService sync;

    /** 查看同步配置及最近一次任务进度。 */
    @GetMapping
    @Operation(summary = "查看微信读书同步进度")
    public AjaxResult status() {
        return AjaxResult.success(sync.status());
    }

    /** 立即提交同步任务，处理在服务端后台完成。 */
    @PostMapping
    @Operation(summary = "提交微信读书同步任务")
    public AjaxResult start() {
        return AjaxResult.success(sync.start(SecurityUtils.getUserId()));
    }
}
