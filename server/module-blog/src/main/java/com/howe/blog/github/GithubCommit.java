package com.howe.blog.github;

/**
 * GitHub 写操作的返回结果
 *
 * @author howe
 */
public class GithubCommit
{
    /** 提交 sha */
    private String commitSha;

    /** 写入后文件的新 blob sha，删除操作为 null */
    private String contentSha;

    public GithubCommit()
    {
    }

    public GithubCommit(String commitSha, String contentSha)
    {
        this.commitSha = commitSha;
        this.contentSha = contentSha;
    }

    public String getCommitSha()
    {
        return commitSha;
    }

    public void setCommitSha(String commitSha)
    {
        this.commitSha = commitSha;
    }

    public String getContentSha()
    {
        return contentSha;
    }

    public void setContentSha(String contentSha)
    {
        this.contentSha = contentSha;
    }
}
