package com.howe.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howe.book.domain.BookNode;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 读书节点的数据访问入口。 */
@Mapper
public interface BookNodeMapper extends BaseMapper<BookNode> {
    /** 只读取历史导入笔记的来源，包含曾被移动或软删除的节点。 */
    @Select("""
        SELECT id, parent_id, deleted_at, JSON_EXTRACT(content_json, '$.weread') AS content_json
        FROM book_node
        WHERE node_type = 'note' AND JSON_EXTRACT(content_json, '$.weread.sourceId') IS NOT NULL
        """)
    List<BookNode> selectWeReadNoteOrigins();

    /** 读取节点上保存的别名，包括软删除节点。 */
    @Select("SELECT id AS nodeId, weread_source_keys AS sourceKeys FROM book_node WHERE weread_source_keys IS NOT NULL")
    List<WeReadBinding> selectWeReadBindings();

    /** 追加来源键，保留节点业务内容、时间和版本号。 */
    @Update("""
        UPDATE book_node
        SET weread_source_keys = JSON_MERGE_PATCH(COALESCE(weread_source_keys, JSON_OBJECT()), JSON_OBJECT(#{sourceKey}, 1)),
            updated_at = updated_at
        WHERE id = #{nodeId}
        """)
    int bindWeReadSource(@Param("nodeId") String nodeId, @Param("sourceKey") String sourceKey);

    /** 单个节点及其持久化来源键。 */
    record WeReadBinding(String nodeId, String sourceKeys) {}
}
