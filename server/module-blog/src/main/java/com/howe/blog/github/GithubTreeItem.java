package com.howe.blog.github;

/**
 * GitHub 文件树中的一个条目
 *
 * @author howe
 */
public class GithubTreeItem
{
    /** 相对仓库根的路径 */
    private String path;

    /** blob sha */
    private String sha;

    /** 字节数 */
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
