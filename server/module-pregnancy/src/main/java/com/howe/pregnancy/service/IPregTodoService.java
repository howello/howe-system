package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.dto.TodoCreateBody;
import com.howe.pregnancy.domain.dto.TodoUpdateBody;
import com.howe.pregnancy.domain.vo.TodoVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 待办服务
 *
 * @author howe
 */
public interface IPregTodoService {

    /**
     * 查询待办
     *
     * @param done 完成状态，可为空
     * @param from 起始日期，可为空
     * @param to   结束日期，可为空
     * @return 待办列表
     */
    List<TodoVO> list(Boolean done, LocalDate from, LocalDate to);

    /**
     * 新增待办
     *
     * @param body 请求
     * @return 新建待办
     */
    TodoVO create(TodoCreateBody body);

    /**
     * 编辑待办
     *
     * @param id   待办 ID
     * @param body 请求
     * @return 更新后的待办
     */
    TodoVO update(Long id, TodoUpdateBody body);

    /**
     * 逻辑删除待办
     *
     * @param id      待办 ID
     * @param version 版本
     */
    void delete(Long id, Integer version);

    /**
     * 查询某天的待办
     *
     * @param date 日期
     * @return 待办列表
     */
    List<TodoVO> listByDay(LocalDate date);
}
