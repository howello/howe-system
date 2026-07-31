package com.howe.generator.domain;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.lang3.ArrayUtils;
import com.howe.common.constant.GenConstants;
import com.howe.common.core.domain.BaseEntity;
import com.howe.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 业务表 gen_table
 *
 * @author howe
 */
@Schema(description = "代码生成业务表")
public class GenTable extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 编号 */
    @Schema(description = "编号", example = "1")
    private Long tableId;

    /** 表名称 */
    @NotBlank(message = "表名称不能为空")
    @Schema(description = "表名称", example = "sys_user")
    private String tableName;

    /** 表描述 */
    @NotBlank(message = "表描述不能为空")
    @Schema(description = "表描述", example = "用户信息表")
    private String tableComment;

    /** 关联父表的表名 */
    @Schema(description = "关联父表的表名", example = "sys_user")
    private String subTableName;

    /** 本表关联父表的外键名 */
    @Schema(description = "本表关联父表的外键名", example = "user_id")
    private String subTableFkName;

    /** 实体类名称(首字母大写) */
    @NotBlank(message = "实体类名称不能为空")
    @Schema(description = "实体类名称（首字母大写）", example = "SysUser")
    private String className;

    /** 使用的模板（crud单表操作 tree树表操作 sub主子表操作） */
    @Schema(description = "使用的模板（crud单表操作 tree树表操作 sub主子表操作）", example = "crud")
    private String tplCategory;

    /** 前端类型（element-ui模版 element-plus模版 element-plus-typescript模版） */
    @Schema(description = "前端类型（element-ui 模版 element-plus 模版 element-plus-typescript 模版）", example = "element-plus")
    private String tplWebType;

    /** 生成包路径 */
    @NotBlank(message = "生成包路径不能为空")
    @Schema(description = "生成包路径", example = "com.howe.system")
    private String packageName;

    /** 生成模块名 */
    @NotBlank(message = "生成模块名不能为空")
    @Schema(description = "生成模块名", example = "system")
    private String moduleName;

    /** 生成业务名 */
    @NotBlank(message = "生成业务名不能为空")
    @Schema(description = "生成业务名", example = "user")
    private String businessName;

    /** 生成功能名 */
    @NotBlank(message = "生成功能名不能为空")
    @Schema(description = "生成功能名", example = "用户信息")
    private String functionName;

    /** 生成作者 */
    @NotBlank(message = "作者不能为空")
    @Schema(description = "生成作者", example = "howe")
    private String functionAuthor;

    /** 表单布局（单列 双列 三列） */
    @Schema(description = "表单布局（1单列 2双列 3三列）", example = "1")
    private Integer formColNum;

    /** 生成代码方式（0zip压缩包 1自定义路径） */
    @Schema(description = "生成代码方式（0zip压缩包 1自定义路径）", example = "0")
    private String genType;

    /** 生成路径（不填默认项目路径） */
    @Schema(description = "生成路径（不填默认项目路径）", example = "/")
    private String genPath;

    /** 主键信息 */
    @Schema(description = "主键列信息")
    private GenTableColumn pkColumn;

    /** 子表信息 */
    @Schema(description = "子表信息")
    private GenTable subTable;

    /** 表列信息 */
    @Valid
    @Schema(description = "表列信息")
    private List<GenTableColumn> columns;

    /** 其它生成选项 */
    @Schema(description = "其它生成选项")
    private String options;

    /** 树编码字段 */
    @Schema(description = "树编码字段", example = "dept_id")
    private String treeCode;

    /** 树父编码字段 */
    @Schema(description = "树父编码字段", example = "parent_id")
    private String treeParentCode;

    /** 树名称字段 */
    @Schema(description = "树名称字段", example = "dept_name")
    private String treeName;

    /** 上级菜单ID字段 */
    @Schema(description = "上级菜单ID", example = "0")
    private Long parentMenuId;

    /** 上级菜单名称字段 */
    @Schema(description = "上级菜单名称", example = "系统管理")
    private String parentMenuName;

    /** 是否生成详情页 */
    @Schema(description = "是否生成详情页", example = "false")
    private boolean isView;

    public Long getTableId()
    {
        return tableId;
    }

    public void setTableId(Long tableId)
    {
        this.tableId = tableId;
    }

    public String getTableName()
    {
        return tableName;
    }

    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }

    public String getTableComment()
    {
        return tableComment;
    }

    public void setTableComment(String tableComment)
    {
        this.tableComment = tableComment;
    }

    public String getSubTableName()
    {
        return subTableName;
    }

    public void setSubTableName(String subTableName)
    {
        this.subTableName = subTableName;
    }

    public String getSubTableFkName()
    {
        return subTableFkName;
    }

    public void setSubTableFkName(String subTableFkName)
    {
        this.subTableFkName = subTableFkName;
    }

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public String getTplCategory()
    {
        return tplCategory;
    }

    public void setTplCategory(String tplCategory)
    {
        this.tplCategory = tplCategory;
    }

    public String getTplWebType()
    {
        return tplWebType;
    }

    public void setTplWebType(String tplWebType)
    {
        this.tplWebType = tplWebType;
    }

    public String getPackageName()
    {
        return packageName;
    }

    public void setPackageName(String packageName)
    {
        this.packageName = packageName;
    }

    public String getModuleName()
    {
        return moduleName;
    }

    public void setModuleName(String moduleName)
    {
        this.moduleName = moduleName;
    }

    public String getBusinessName()
    {
        return businessName;
    }

    public void setBusinessName(String businessName)
    {
        this.businessName = businessName;
    }

    public String getFunctionName()
    {
        return functionName;
    }

    public void setFunctionName(String functionName)
    {
        this.functionName = functionName;
    }

    public String getFunctionAuthor()
    {
        return functionAuthor;
    }

    public void setFunctionAuthor(String functionAuthor)
    {
        this.functionAuthor = functionAuthor;
    }

    public Integer getFormColNum()
    {
        return formColNum;
    }

    public void setFormColNum(Integer formColNum)
    {
        this.formColNum = formColNum;
    }

    public String getGenType()
    {
        return genType;
    }

    public void setGenType(String genType)
    {
        this.genType = genType;
    }

    public String getGenPath()
    {
        return genPath;
    }

    public void setGenPath(String genPath)
    {
        this.genPath = genPath;
    }

    public GenTableColumn getPkColumn()
    {
        return pkColumn;
    }

    public void setPkColumn(GenTableColumn pkColumn)
    {
        this.pkColumn = pkColumn;
    }

    public GenTable getSubTable()
    {
        return subTable;
    }

    public void setSubTable(GenTable subTable)
    {
        this.subTable = subTable;
    }

    public List<GenTableColumn> getColumns()
    {
        return columns;
    }

    public void setColumns(List<GenTableColumn> columns)
    {
        this.columns = columns;
    }

    public String getOptions()
    {
        return options;
    }

    public void setOptions(String options)
    {
        this.options = options;
    }

    public String getTreeCode()
    {
        return treeCode;
    }

    public void setTreeCode(String treeCode)
    {
        this.treeCode = treeCode;
    }

    public String getTreeParentCode()
    {
        return treeParentCode;
    }

    public void setTreeParentCode(String treeParentCode)
    {
        this.treeParentCode = treeParentCode;
    }

    public String getTreeName()
    {
        return treeName;
    }

    public void setTreeName(String treeName)
    {
        this.treeName = treeName;
    }

    public Long getParentMenuId()
    {
        return parentMenuId;
    }

    public void setParentMenuId(Long parentMenuId)
    {
        this.parentMenuId = parentMenuId;
    }

    public String getParentMenuName()
    {
        return parentMenuName;
    }

    public void setParentMenuName(String parentMenuName)
    {
        this.parentMenuName = parentMenuName;
    }

    public boolean isView()
    {
        return isView;
    }

    public void setView(boolean isView)
    {
        this.isView = isView;
    }

    public boolean isSub()
    {
        return isSub(this.tplCategory);
    }

    public static boolean isSub(String tplCategory)
    {
        return tplCategory != null && StringUtils.equals(GenConstants.TPL_SUB, tplCategory);
    }

    public boolean isTree()
    {
        return isTree(this.tplCategory);
    }

    public static boolean isTree(String tplCategory)
    {
        return tplCategory != null && StringUtils.equals(GenConstants.TPL_TREE, tplCategory);
    }

    public boolean isCrud()
    {
        return isCrud(this.tplCategory);
    }

    public static boolean isCrud(String tplCategory)
    {
        return tplCategory != null && StringUtils.equals(GenConstants.TPL_CRUD, tplCategory);
    }

    public boolean isSuperColumn(String javaField)
    {
        return isSuperColumn(this.tplCategory, javaField);
    }

    public static boolean isSuperColumn(String tplCategory, String javaField)
    {
        if (isTree(tplCategory))
        {
            return StringUtils.equalsAnyIgnoreCase(javaField,
                    ArrayUtils.addAll(GenConstants.TREE_ENTITY, GenConstants.BASE_ENTITY));
        }
        return StringUtils.equalsAnyIgnoreCase(javaField, GenConstants.BASE_ENTITY);
    }
}
