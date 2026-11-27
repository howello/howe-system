package com.howe.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.book.domain.BookNode;
import org.apache.ibatis.annotations.Mapper;

/** 读书节点的数据访问入口。 */
@Mapper
public interface BookNodeMapper extends BaseMapper<BookNode> {
}
