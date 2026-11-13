package com.howe.blog.tool.dto;

/**
 * 博客写工具的请求字段常量与共享校验规则。
 *
 * <p>只声明字段名与上限，不携带实体；工具输入结构、校验器与 Handler 共用同一份常量，
 * 避免 schema 与执行校验漂移。</p>
 */
public final class BlogWriteFields {

    /** 草稿标题上限。 */
    public static final int TITLE_MAX = 100;

    /** 文章标识上限。 */
    public static final int SLUG_MAX = 100;

    /** 草稿正文上限。 */
    public static final int DRAFT_CONTENT_MAX = 40000;

    /** 说说正文上限。 */
    public static final int TALK_CONTENT_MAX = 20000;

    /** 分类/标签最大数量。 */
    public static final int TAG_MAX_ITEMS = 20;

    /** 单个分类/标签最大长度。 */
    public static final int TAG_MAX_LENGTH = 50;

    /** 幂等键规则说明。 */
    public static final String IDEMPOTENCY_PATTERN = "^[A-Za-z0-9_-]{16,64}$";

    /** 常量类不允许实例化。 */
    private BlogWriteFields() {
    }
}
