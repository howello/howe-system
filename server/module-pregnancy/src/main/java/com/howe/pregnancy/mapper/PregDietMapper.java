package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregDietRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 饮食记录数据层
 *
 * @author howe
 */
public interface PregDietMapper {

    /**
     * 按条件查询饮食记录
     *
     * @param mealType 餐次，可为空
     * @param from     起始业务日，可为空
     * @param to       结束业务日，可为空
     * @return 饮食记录列表
     */
    List<PregDietRecord> selectList(@Param("mealType") String mealType,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 按主键查询
     *
     * @param id 记录 ID
     * @return 记录，不存在返回 null
     */
    PregDietRecord selectById(@Param("id") Long id);

    /**
     * 按请求 ID 查询（含软删）
     *
     * @param actorUserId 操作人
     * @param requestId   请求 ID
     * @return 记录，不存在返回 null
     */
    PregDietRecord selectByRequest(@Param("actorUserId") Long actorUserId,
                                   @Param("requestId") String requestId);

    /**
     * 新增记录
     *
     * @param record 记录
     * @return 影响行数
     */
    int insert(PregDietRecord record);

    /**
     * 按版本更新
     *
     * @param record 记录
     * @return 影响行数
     */
    int update(PregDietRecord record);

    /**
     * 逻辑删除
     *
     * @param id      记录 ID
     * @param version 版本
     * @return 影响行数
     */
    int logicalDelete(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 查询某天的饮食记录
     *
     * @param date 日期
     * @return 记录列表
     */
    List<PregDietRecord> selectByDay(@Param("date") LocalDate date);

    /**
     * 按日统计饮食记录数量
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 每行含 recordDate 与 total
     */
    List<Map<String, Object>> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
