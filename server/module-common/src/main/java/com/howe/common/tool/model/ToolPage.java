package com.howe.common.tool.model;

/**
 * 工具分页信息。
 *
 * @param number 页码，从 1 开始
 * @param size 每页条数
 * @param returned 本页实际条数
 * @param total 符合条件的总数
 */
public record ToolPage(int number, int size, int returned, long total) {
}
