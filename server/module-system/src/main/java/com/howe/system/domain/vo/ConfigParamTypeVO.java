package com.howe.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 参数分类选项
 *
 * <p>用于参数管理页的分类下拉。分类是 {@code sys_config.param_type} 的自由文本，
 * 不存在独立的分类表，因此这里只承载「值 + 展示名 + 是否未分类」三个字段。</p>
 *
 * <p>{@code uncategorized=true} 的选项代表空 / NULL / 纯空白分类，
 * 前端选中它时应改为传 {@code uncategorized=true}，而不是传一个中文字符串冒充分类值。</p>
 *
 * @param value         分类值；未分类固定为空字符串
 * @param label         展示名称
 * @param uncategorized 是否为「未分类」选项
 * @author howe
 */
@Schema(description = "参数分类选项")
public record ConfigParamTypeVO(
        @Schema(description = "分类值；未分类为空字符串") String value,
        @Schema(description = "展示名称") String label,
        @Schema(description = "是否为未分类选项") boolean uncategorized) {

    /**
     * 构造一个普通分类选项
     *
     * @param value 分类值
     * @return 分类选项
     */
    public static ConfigParamTypeVO of(String value) {
        return new ConfigParamTypeVO(value, value, false);
    }

    /**
     * 构造「未分类」选项
     *
     * <p>不能叫 {@code uncategorized()}：与 record 的访问器同名，
     * Java 会判定为重复声明。</p>
     *
     * @return 未分类选项
     */
    public static ConfigParamTypeVO ofUncategorized() {
        return new ConfigParamTypeVO("", "未分类", true);
    }
}
