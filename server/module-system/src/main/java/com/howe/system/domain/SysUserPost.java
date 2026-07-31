package com.howe.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户和岗位关联 sys_user_post
 *
 * @author howe
 */
@Schema(description = "用户和岗位关联")
public class SysUserPost
{
    /** 用户ID */
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    /** 岗位ID */
    @Schema(description = "岗位ID", example = "1")
    private Long postId;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getPostId()
    {
        return postId;
    }

    public void setPostId(Long postId)
    {
        this.postId = postId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("postId", getPostId())
            .toString();
    }
}
