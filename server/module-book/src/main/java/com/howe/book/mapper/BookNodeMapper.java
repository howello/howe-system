package com.howe.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.book.domain.BookNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 读书节点的数据访问入口。 */
@Mapper
public interface BookNodeMapper extends BaseMapper<BookNode> {
    /** 锁定归属账号，保护树的并发修改。 */
    @Select("SELECT user_id FROM sys_user WHERE user_id = #{userId} AND del_flag = '0' FOR UPDATE")
    Long lockOwner(long userId);
}
