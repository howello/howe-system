package com.howe.book.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 分类、书本、感悟或备注。 */
@Data
@TableName("book_node")
public class BookNode {
    private String id;
    /** 创建节点的用户 ID。 */
    private Long userId;
    private String parentId;
    private String nodeType;
    private String title;
    private Integer sortOrder;
    private String coverKind;
    private String coverRef;
    private String contentJson;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    /** 直接子节点数，供按层展开和书架摘要使用。 */
    @TableField(exist = false)
    private Integer childCount;
    /** 当前层中该节点的内容指纹。 */
    @TableField(exist = false)
    private String cacheVersion;
}
