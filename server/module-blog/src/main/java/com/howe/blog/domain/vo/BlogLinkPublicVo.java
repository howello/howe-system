package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公开友链视图对象
 *
 * <p>存在的理由：{@code BlogLink} 继承 {@code BaseEntity}，而后者只给 {@code searchValue}
 * 标了 {@code @JsonIgnore}，{@code createBy}/{@code updateBy}/{@code remark} 都会照常序列化。
 * 匿名接口直接下发实体，等于把管理员账号名和「仅后台可见」的备注公开出去——
 * 账号名可以直接拿去撞登录接口。</p>
 *
 * @param linkName 站点名称
 * @param linkUrl  站点地址
 * @param avatar   头像/图标地址
 * @param descr    站点描述
 * @author howe
 */
@Schema(description = "公开友链（仅展示字段）")
public record BlogLinkPublicVo(
        @Schema(description = "站点名称") String linkName,
        @Schema(description = "站点地址") String linkUrl,
        @Schema(description = "头像/图标地址") String avatar,
        @Schema(description = "站点描述") String descr) {
}
