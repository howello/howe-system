package com.howe.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.book.domain.BookNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 读书节点的数据访问入口。 */
@Mapper
public interface BookNodeMapper extends BaseMapper<BookNode> {
    /** 锁定共享树，保护跨账号的并发修改。 */
    @Select("SELECT id FROM book_tree_lock WHERE id = 1 FOR UPDATE")
    Integer lockTree();
}
