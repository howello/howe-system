package com.howe.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.howe.common.annotation.Excel;
import com.howe.common.annotation.Excel.ColumnType;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 参数配置表 sys_config
 *
 * @author howe
 */
@Schema(description = "参数配置")
public class SysConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 参数主键 */
    @Excel(name = "参数主键", cellType = ColumnType.NUMERIC)
    @Schema(description = "参数主键", example = "1")
    private Long configId;

    /** 参数名称 */
    @Excel(name = "参数名称")
    @Schema(description = "参数名称", example = "主框架页-默认皮肤样式名称")
    private String configName;

    /** 参数键名 */
    @Excel(name = "参数键名")
    @Schema(description = "参数键名", example = "sys.index.skinName")
    private String configKey;

    /** 参数键值 */
    @Excel(name = "参数键值")
    @Schema(description = "参数键值", example = "skin-blue")
    private String configValue;

    /** 系统内置（Y是 N否） */
    @Excel(name = "系统内置", readConverterExp = "Y=是,N=否")
    @Schema(description = "系统内置（Y是 N否）", example = "Y")
    private String configType;

    /**
     * 参数分类
     *
     * <p>
     * 自由文本，仅用于管理端按业务域分组展示（如「存储」「博客」「AI」「点餐」）。
     * 不建字典、不做枚举约束：分类随业务增长，写死反而要频繁改代码。
     * 注意与 {@link #configType} 区分——后者是「系统内置 Y/N」，是权限语义。
     * </p>
     */
    @Size(min = 0, max = 32, message = "参数分类不能超过32个字符")
    @Excel(name = "参数分类")
    @Schema(description = "参数分类（自由文本，仅用于管理端分组展示）", example = "存储")
    private String paramType;

    /**
     * 是否敏感（Y是 N否）
     *
     * <p>
     * 标记为 Y 的参数值加密入库，HTTP 接口一律只返回掩码；
     * 只有服务内部通过 {@code ConfigUtils} 才能拿到明文。
     * </p>
     */
    @Excel(name = "是否敏感", readConverterExp = "Y=是,N=否")
    @Schema(description = "是否敏感（Y是 N否）；Y 时值加密入库，HTTP 接口只返回掩码", example = "N")
    private String sensitive;

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    @NotBlank(message = "参数名称不能为空")
    @Size(min = 0, max = 100, message = "参数名称不能超过100个字符")
    public String getConfigName()
    {
        return configName;
    }

    public void setConfigName(String configName)
    {
        this.configName = configName;
    }

    @NotBlank(message = "参数键名长度不能为空")
    @Size(min = 0, max = 100, message = "参数键名长度不能超过100个字符")
    public String getConfigKey()
    {
        return configKey;
    }

    public void setConfigKey(String configKey)
    {
        this.configKey = configKey;
    }

    /**
     * 参数键值
     *
     * <p>
     * 不加 {@code @NotBlank}：敏感参数编辑时前端拿不到明文，编辑框留空代表「不修改」，
     * 后端按原值沿用。新增时的非空校验放在 {@code SysConfigController#add} 里。
     * </p>
     */
    @Size(min = 0, max = 2048, message = "参数键值长度不能超过2048个字符")
    public String getConfigValue()
    {
        return configValue;
    }

    public void setConfigValue(String configValue)
    {
        this.configValue = configValue;
    }

    public String getConfigType()
    {
        return configType;
    }

    public void setConfigType(String configType)
    {
        this.configType = configType;
    }

    public String getSensitive()
    {
        return sensitive;
    }

    public void setSensitive(String sensitive)
    {
        this.sensitive = sensitive;
    }

    public String getParamType()
    {
        return paramType;
    }

    public void setParamType(String paramType)
    {
        this.paramType = paramType;
    }

    /**
     * 判断是否为敏感参数
     *
     * <p>
     * 方法名不能叫 {@code isSensitive}：它会被 JavaBeans 当成 {@code sensitive} 属性的
     * 第二个 getter，与 {@link #getSensitive()} 一起触发 MyBatis 的
     * 「Illegal overloaded getter method with ambiguous type」错误。
     * </p>
     *
     * @return true 表示值加密入库、对外只给掩码
     */
    public boolean sensitiveEnabled()
    {
        return "Y".equals(sensitive);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("configId", getConfigId())
            .append("configName", getConfigName())
            .append("configKey", getConfigKey())
            .append("configValue", getConfigValue())
            .append("configType", getConfigType())
            .append("paramType", getParamType())
            .append("sensitive", getSensitive())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
