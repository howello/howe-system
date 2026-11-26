package com.howe.book.service;

import com.howe.book.domain.BookNode;
import java.util.List;

/** 读书节点业务接口。 */
public interface BookNodeService {
    /** 查询符合条件的有效节点。 */
    List<BookNode> list(Long owner, String parent, boolean children, String type, String title);

    /**
     * 查询符合条件的有效节点。
     *
     * @param withContent 是否连同正文一起返回；书架与知识树只需要摘要，
     *                    书本页的感悟摘要、备注正文才需要正文
     */
    List<BookNode> list(Long owner, String parent, boolean children, String type, String title, boolean withContent);

    /** 查询有权访问的节点详情。 */
    BookNode get(Long owner, String id);

    /** 为指定账号创建节点。 */
    BookNode create(long owner, BookNode input);

    /** 按版本编辑节点或移动子树。 */
    BookNode update(Long owner, String id, BookNode input);

    /** 软删除节点及其后代。 */
    List<String> remove(Long owner, String id);
}
