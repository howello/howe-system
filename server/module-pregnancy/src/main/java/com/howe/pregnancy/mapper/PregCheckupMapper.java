package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregCheckup;
import com.howe.pregnancy.domain.PregCheckupDetail;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 产检数据层
 *
 * @author howe
 */
public interface PregCheckupMapper {

    /**
     * 查询全部未删除产检（按建议窗口与创建顺序）
     *
     * @return 产检列表
     */
    List<PregCheckup> selectList();

    /** 锁定在用产检，保证默认待办核对与预约状态变更串行。 */
    List<PregCheckup> selectListForUpdate();

    /**
     * 按主键查询
     *
     * @param id 产检 ID
     * @return 产检，不存在返回 null
     */
    PregCheckup selectById(@Param("id") Long id);

    /**
     * 按初始化稳定键查询（含已软删与已取消，用于幂等初始化）
     *
     * @param seedKey 稳定键
     * @return 产检，不存在返回 null
     */
    PregCheckup selectBySeedKey(@Param("seedKey") String seedKey);

    /**
     * 按请求 ID 查询（含软删）
     *
     * @param requestId 请求 ID
     * @return 产检，不存在返回 null
     */
    PregCheckup selectByRequest(@Param("requestId") String requestId);

    /**
     * 新增产检
     *
     * @param checkup 产检
     * @return 影响行数
     */
    int insert(PregCheckup checkup);

    /**
     * 按版本更新产检
     *
     * @param checkup 产检
     * @return 影响行数
     */
    int update(PregCheckup checkup);

    /**
     * 仅递增版本（详情或费用变化时维护父版本）
     *
     * @param id      产检 ID
     * @param version 当前版本
     * @return 影响行数
     */
    int bumpVersion(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 逻辑删除
     *
     * @param id      产检 ID
     * @param version 版本
     * @return 影响行数
     */
    int logicalDelete(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 查询某天的产检（预约或完成）
     *
     * @param date 日期
     * @return 产检列表
     */
    List<PregCheckup> selectByDay(@Param("date") LocalDate date);

    /**
     * 按日统计产检数量（预约或完成）
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 每行含 recordDate 与 total
     */
    List<Map<String, Object>> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 查询某产检的全部详情
     *
     * @param checkupId 产检 ID
     * @return 详情列表
     */
    List<PregCheckupDetail> selectDetails(@Param("checkupId") Long checkupId);

    /**
     * 批量新增详情
     *
     * @param details 详情列表
     * @return 影响行数
     */
    int insertDetails(@Param("details") List<PregCheckupDetail> details);

    /**
     * 软删某产检的一类详情（原子替换用）
     *
     * @param checkupId 产检 ID
     * @param type      类型
     * @return 影响行数
     */
    int deleteDetailsByType(@Param("checkupId") Long checkupId, @Param("type") String type);

    /**
     * 按主键查询详情
     *
     * @param id 详情 ID
     * @return 详情，不存在返回 null
     */
    PregCheckupDetail selectDetailById(@Param("id") Long id);

    /**
     * 更新准备事项完成状态
     *
     * @param id        详情 ID
     * @param done      完成状态
     * @param doneBy    完成人
     * @return 影响行数
     */
    int updatePreparationDone(@Param("id") Long id, @Param("done") Boolean done,
                              @Param("doneBy") Long doneBy);
}
