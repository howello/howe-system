package com.howe.book.domain;

import java.util.List;

/** 一个父节点下的增量及当前顺序。 */
public record BookScopeDelta(List<BookNode> upserts, List<String> removed, List<String> order, BookNode parent) {
}
