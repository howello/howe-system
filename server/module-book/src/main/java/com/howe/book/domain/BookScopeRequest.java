package com.howe.book.domain;

import java.util.Map;

/** 客户端持有的这一层节点指纹。空父节点表示书架根。 */
public record BookScopeRequest(String parentId, Map<String, String> known, String parentVersion) {
}
