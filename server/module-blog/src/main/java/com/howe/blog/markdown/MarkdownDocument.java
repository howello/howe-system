package com.howe.blog.markdown;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 解析后的 markdown 文档：frontmatter + 正文
 *
 * @author howe
 */
@Schema(description = "解析后的 markdown 文档：frontmatter + 正文")
public class MarkdownDocument
{
    /** frontmatter */
    @Schema(description = "文章 frontmatter")
    private Frontmatter frontmatter;

    /** 正文（不含 frontmatter 与其后的空行） */
    @Schema(description = "正文（不含 frontmatter 与其后的空行）")
    private String body;

    public MarkdownDocument()
    {
    }

    public MarkdownDocument(Frontmatter frontmatter, String body)
    {
        this.frontmatter = frontmatter;
        this.body = body;
    }

    public Frontmatter getFrontmatter()
    {
        return frontmatter;
    }

    public void setFrontmatter(Frontmatter frontmatter)
    {
        this.frontmatter = frontmatter;
    }

    public String getBody()
    {
        return body;
    }

    public void setBody(String body)
    {
        this.body = body;
    }
}
