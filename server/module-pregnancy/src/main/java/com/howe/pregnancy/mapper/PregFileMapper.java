package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregFile;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 私有原图数据层
 *
 * @author howe
 */
public interface PregFileMapper {

    /**
     * 按主键查询（含任意状态，业务层按状态判断可见性）
     *
     * @param id 文件 ID
     * @return 文件，不存在返回 null
     */
    PregFile selectById(@Param("id") Long id);

    /**
     * 锁定文件行（关联、解绑与回收前使用）
     *
     * @param id 文件 ID
     * @return 文件，不存在返回 null
     */
    PregFile selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按上传人与请求 ID 查询
     *
     * @param uploaderUserId 上传人
     * @param requestId      请求 ID
     * @return 文件，不存在返回 null
     */
    PregFile selectByRequest(@Param("uploaderUserId") Long uploaderUserId,
                             @Param("requestId") String requestId);

    /**
     * 新增文件
     *
     * @param file 文件
     * @return 影响行数
     */
    int insert(PregFile file);

    /**
     * 绑定到业务资源
     *
     * @param id        文件 ID
     * @param ownerType 业务类型
     * @param ownerId   业务 ID
     * @param sortOrder 顺序
     * @return 影响行数
     */
    int bind(@Param("id") Long id, @Param("ownerType") String ownerType,
             @Param("ownerId") Long ownerId, @Param("sortOrder") Integer sortOrder);

    /**
     * 解除业务绑定并转入 ORPHAN
     *
     * @param ownerType 业务类型
     * @param ownerId   业务 ID
     * @param reclaimAt 可回收时刻
     * @return 影响行数
     */
    int detachByOwner(@Param("ownerType") String ownerType, @Param("ownerId") Long ownerId,
                      @Param("reclaimAt") LocalDateTime reclaimAt);

    /**
     * 查询某业务已绑定文件
     *
     * @param ownerType 业务类型
     * @param ownerId   业务 ID
     * @return 文件列表
     */
    List<PregFile> selectByOwner(@Param("ownerType") String ownerType, @Param("ownerId") Long ownerId);

    /**
     * 领取可回收文件（TEMP 过期或 ORPHAN 到期）并转为 DELETING
     *
     * @param tempCutoff TEMP 文件的创建时间上限（保留期之前的时刻）
     * @param limit      单次领取数量
     * @return 已转为 DELETING 的文件
     */
    List<PregFile> claimReclaimable(@Param("tempCutoff") LocalDateTime tempCutoff,
                                    @Param("limit") int limit);

    /**
     * 把文件标记为 DELETING
     *
     * @param id 文件 ID
     * @return 影响行数，0 表示已被其他执行者领取或已不可回收
     */
    int markDeleting(@Param("id") Long id);

    /**
     * 按状态查询文件
     *
     * @param state 状态
     * @param limit 数量上限
     * @return 文件列表
     */
    List<PregFile> selectByState(@Param("state") String state, @Param("limit") int limit);

    /**
     * 删除登记行
     *
     * @param id 文件 ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 查询全部仍被引用的存储键（清理未登记暂存文件时使用）
     *
     * @return 存储键列表
     */
    List<String> selectAllStorageKeys();
}
