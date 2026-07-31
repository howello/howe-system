package com.howe.blog.github;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GitHub 文件树中的一个条目
 *
 * @author howe
 */
@Schema(description = "GitHub 文件树中的一个条目")
public class GithubTreeItem
{
    /** 相对仓库根的路径 */
    @Schema(description = "相对仓库根的路径", example = "src/content/blog/interview-notes/01-jvm.md")
    private String path;

    /** blob sha */
    @Schema(description = "blob sha，与索引里的值比对以判断文件是否变化", example = "9daeafb9864cf43055ae93beb0afd6c7d144bfa4")
    private String sha;

    /** 字节数 */
    @Schema(description = "字节数", example = "4096")
    private long size;

    public GithubTreeItem()
    {
    }

    public GithubTreeItem(String path, String sha, long size)
    {
        this.path = path;
        this.sha = sha;
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

    public long getSize()
    {
        return size;
    }

    public void setSize(long size)
    {
        this.size = size;
    }
}
