package com.howe.book.domain;

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
}
