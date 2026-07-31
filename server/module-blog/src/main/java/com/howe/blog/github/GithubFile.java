package com.howe.blog.github;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GitHub 仓库中的一个文件
 *
 * @author howe
 */
@Schema(description = "GitHub 仓库中的一个文件")
public class GithubFile
{
    /** 相对仓库根的路径 */
    @Schema(description = "相对仓库根的路径", example = "src/content/blog/interview-notes/01-jvm.md")
    private String path;

    /** blob sha，更新和删除时必须回传，GitHub 用它做乐观锁 */
    @Schema(description = "blob sha，更新和删除时必须回传，GitHub 用它做乐观锁", example = "9daeafb9864cf43055ae93beb0afd6c7d144bfa4")
    private String sha;

    /** 已解码的文件原文 */
    @Schema(description = "已解码的文件原文")
    private String content;

    /** 字节数 */
    @Schema(description = "字节数", example = "4096")
    private long size;

    public GithubFile()
    {
    }

    public GithubFile(String path, String sha, String content, long size)
    {
        this.path = path;
        this.sha = sha;
        this.content = content;
        this.size = size;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getSha()
    {
        return sha;
    }

    public void setSha(String sha)
    {
        this.sha = sha;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public long getSize()
    {
        return size;
    }

    public void setSize(long size)
    {
        this.size = size;
    }
}
