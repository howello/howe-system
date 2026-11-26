package com.howe.book.service;

import com.howe.book.domain.BookNode;
import java.util.List;

/** 读书节点业务接口。 */
public interface BookNodeService {
    /** 查询符合条件的有效节点。 */
    List<BookNode> list(Long creator, String parent, boolean children, String type, String title);

    /**
     * 查询符合条件的有效节点。
     *
     * @param creator 可选的创建人筛选条件，书架不传此条件
     * @param withContent 是否连同正文一起返回；书架与知识树只需要摘要，
     *                    书本页的感悟摘要、备注正文才需要正文
     */
    List<BookNode> list(Long creator, String parent, boolean children, String type, String title, boolean withContent);

    /** 查询共享书架的有效节点详情。 */
    BookNode get(String id);

    /** 创建共享节点并记录创建人。 */
    BookNode create(long creator, BookNode input);

    /** 按版本编辑节点或移动子树。 */
    BookNode update(String id, BookNode input);

    /** 软删除节点及其后代。 */
    List<String> remove(String id);
}
