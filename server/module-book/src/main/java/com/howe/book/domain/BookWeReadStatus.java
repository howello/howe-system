package com.howe.book.domain;

import java.time.Instant;

/** 微信读书配置状态和最近一次任务进度。 */
public record BookWeReadStatus(boolean configured, Task task) {
    /** 手动和定时任务共用的进度快照。 */
    public record Task(String id, String state, String trigger, int total, int completed, int categories,
        int books, int notes, int failed, String message, Instant startedAt, Instant finishedAt) {}
}
