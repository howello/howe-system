package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 友链分组视图对象
 *
 * <p>blog-ui 是静态站，读不到 sys_dict_data，所以分组名必须由后端翻译好再下发——
 * 前端只负责按这个结构渲染分区，不维护任何编码到名称的映射。</p>
 *
 * @param groupCode 分组编码（字典 blog_link_group 的值；「其他」组为空串）
 * @param groupName 已翻译的分组名称，可直接展示
 * @param links     该分组下的友链，按 order_num 升序
 * @author howe
 */
@Schema(description = "友链分组（含已翻译的分组名）")
public record BlogLinkGroupVo(
        @Schema(description = "分组编码，「其他」组为空串", example = "tech")
        String groupCode,

        @Schema(description = "分组名称，已由服务端翻译，可直接展示", example = "技术")
        String groupName,

        @Schema(description = "该分组下的友链")
        List<BlogLinkPublicVo> links) {
}
