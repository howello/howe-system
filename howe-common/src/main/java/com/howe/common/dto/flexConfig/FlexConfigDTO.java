package com.howe.common.dto.flexConfig;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/9 10:04 星期三
 * <p>@Version 1.0
 * <p>@Description
 */
@TableName(value = "t_flex_config")
@Data
@NoArgsConstructor
public class FlexConfigDTO {
    /**
     * 规则ID
     */
    @TableId
    private String ruleId;

    /**
     * 使能
     */
    private Boolean enable;

    /**
     * 创建时间
     */
    private Date crteTime;

    /**
     * 创建用户
     */
    private String crteName;

    /**
     * 更新时间
     */
    private Date updtTime;

    /**
     * 更新用户
     */
    private String updtName;

    /**
     * 规则所属业务类型ID
     */
    private String bizTypeId;

    /**
     * 规则所属业务类型描述
     */
    private String bizTypeDesc;

    /**
     * 配置内容
     */
    private String data;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        FlexConfigDTO other = (FlexConfigDTO) that;
        return (this.getRuleId() == null ? other.getRuleId() == null : this.getRuleId().equals(other.getRuleId()))
                && (this.getEnable().equals(other.getEnable()))
                && (this.getCrteTime() == null ? other.getCrteTime() == null : this.getCrteTime().equals(other.getCrteTime()))
                && (this.getCrteName() == null ? other.getCrteName() == null : this.getCrteName().equals(other.getCrteName()))
                && (this.getUpdtTime() == null ? other.getUpdtTime() == null : this.getUpdtTime().equals(other.getUpdtTime()))
                && (this.getUpdtName() == null ? other.getUpdtName() == null : this.getUpdtName().equals(other.getUpdtName()))
                && (this.getBizTypeId() == null ? other.getBizTypeId() == null : this.getBizTypeId().equals(other.getBizTypeId()))
                && (this.getData() == null ? other.getData() == null : this.getData().equals(other.getData()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getRuleId() == null) ? 0 : getRuleId().hashCode());
        result = prime * result + ((getCrteTime() == null) ? 0 : getCrteTime().hashCode());
        result = prime * result + ((getCrteName() == null) ? 0 : getCrteName().hashCode());
        result = prime * result + ((getUpdtTime() == null) ? 0 : getUpdtTime().hashCode());
        result = prime * result + ((getUpdtName() == null) ? 0 : getUpdtName().hashCode());
        result = prime * result + ((getBizTypeId() == null) ? 0 : getBizTypeId().hashCode());
        result = prime * result + ((getData() == null) ? 0 : getData().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", ruleId=").append(ruleId);
        sb.append(", enable=").append(enable);
        sb.append(", crteTime=").append(crteTime);
        sb.append(", crteName=").append(crteName);
        sb.append(", updtTime=").append(updtTime);
        sb.append(", updtName=").append(updtName);
        sb.append(", bizTypeId=").append(bizTypeId);
        sb.append(", data=").append(data);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}
