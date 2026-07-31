package com.howe.blog.github;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GitHub 写操作的返回结果
 *
 * @author howe
 */
@Schema(description = "GitHub 写操作的返回结果")
public class GithubCommit
{
    /** 提交 sha */
    @Schema(description = "提交 sha", example = "7fb52107e2f0b6d1c1a9d3f1e5b8c0a2d4e6f8a0")
    private String commitSha;

    /** 写入后文件的新 blob sha，删除操作为 null */
    @Schema(description = "写入后文件的新 blob sha，删除操作为 null", example = "9daeafb9864cf43055ae93beb0afd6c7d144bfa4")
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
