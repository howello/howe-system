package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregTodo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 待办数据层
 *
 * @author howe
 */
public interface PregTodoMapper {

    /**
     * 按条件查询待办
     *
     * @param done 完成状态，可为空
     * @param from 起始日期，可为空
     * @param to   结束日期，可为空
     * @return 待办列表
     */
    List<PregTodo> selectList(@Param("done") Boolean done, @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    /**
     * 按主键查询
     *
     * @param id 待办 ID
     * @return 待办，不存在返回 null
     */
    PregTodo selectById(@Param("id") Long id);

    /**
     * 按请求 ID 查询（含软删）
     *
     * @param requestId 请求 ID
     * @return 待办，不存在返回 null
     */
    PregTodo selectByRequest(@Param("requestId") String requestId);

    /**
     * 新增待办
     *
     * @param todo 待办
     * @return 影响行数
     */
    int insert(PregTodo todo);

    /**
     * 按版本更新
     *
     * @param todo 待办
     * @return 影响行数
     */
    int update(PregTodo todo);

    /**
     * 逻辑删除
     *
     * @param id       待办 ID
     * @param version  版本
     * @param updateBy 操作人
     * @return 影响行数
     */
    int logicalDelete(@Param("id") Long id, @Param("version") Integer version,
                      @Param("updateBy") String updateBy);

    /**
     * 查询某天的待办
     *
     * @param dueDate 日期
     * @return 待办列表
     */
    List<PregTodo> selectByDay(@Param("dueDate") LocalDate dueDate);

    /**
     * 按日统计待办数量
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 每行含 dueDate 与 total
     */
    List<Map<String, Object>> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
