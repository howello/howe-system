package com.howe.pregnancy.mapper;

import com.howe.pregnancy.domain.PregBag;
import com.howe.pregnancy.domain.PregBagItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 待产包数据层
 *
 * @author howe
 */
public interface PregBagMapper {

    /**
     * 查询全部清单
     *
     * @return 清单列表
     */
    List<PregBag> selectBags();

    /**
     * 按主键查询清单
     *
     * @param id 清单 ID
     * @return 清单，不存在返回 null
     */
    PregBag selectBagById(@Param("id") Long id);

    /**
     * 按稳定键查询清单（含软删，用于幂等初始化）
     *
     * @param seedKey 稳定键
     * @return 清单，不存在返回 null
     */
    PregBag selectBagBySeed(@Param("seedKey") String seedKey);

    /**
     * 新增清单
     *
     * @param bag 清单
     * @return 影响行数
     */
    int insertBag(PregBag bag);

    /**
     * 按条件查询物品
     *
     * @param bagId    清单 ID，可为空
     * @param category 分类，可为空
     * @param prepared 准备状态，可为空
     * @return 物品列表
     */
    List<PregBagItem> selectItems(@Param("bagId") Long bagId, @Param("category") String category,
                                  @Param("prepared") Boolean prepared);

    /**
     * 按主键查询物品
     *
     * @param id 物品 ID
     * @return 物品，不存在返回 null
     */
    PregBagItem selectItemById(@Param("id") Long id);

    /**
     * 锁定当前物品，串行检查和创建 AI 介绍任务。
     *
     * @param id 物品主键
     * @return 当前物品
     */
    PregBagItem selectItemByIdForUpdate(@Param("id") Long id);

    /**
     * 按清单与稳定键查询物品（含软删）
     *
     * @param bagId   清单 ID
     * @param seedKey 稳定键
     * @return 物品，不存在返回 null
     */
    PregBagItem selectItemBySeed(@Param("bagId") Long bagId, @Param("seedKey") String seedKey);

    /**
     * 按请求 ID 查询物品（含软删）
     *
     * @param requestId 请求 ID
     * @return 物品，不存在返回 null
     */
    PregBagItem selectItemByRequest(@Param("requestId") String requestId);

    /**
     * 新增物品
     *
     * @param item 物品
     * @return 影响行数
     */
    int insertItem(PregBagItem item);

    /**
     * 按版本更新物品
     *
     * @param item 物品
     * @return 影响行数
     */
    int updateItem(PregBagItem item);

    /**
     * 更新 AI 展示内容（仅当任务仍是当前任务时生效）
     *
     * @param id          物品 ID
     * @param taskId      任务 ID
     * @param aiText      AI 文本，可为空表示不改
     * @param aiImageKey  AI 图片键，可为空表示不改
     * @return 影响行数
     */
    int updateAiContent(@Param("id") Long id, @Param("taskId") Long taskId,
                        @Param("aiText") String aiText, @Param("aiImageKey") String aiImageKey);

    /**
     * 设置当前内容任务
     *
     * @param id     物品 ID
     * @param taskId 任务 ID
     * @return 影响行数
     */
    int updateCurrentTask(@Param("id") Long id, @Param("taskId") Long taskId);

    /**
     * 逻辑删除物品
     *
     * @param id      物品 ID
     * @param version 版本
     * @param updateBy 操作人
     * @return 影响行数
     */
    int logicalDeleteItem(@Param("id") Long id, @Param("version") Integer version,
                          @Param("updateBy") String updateBy);

    /**
     * 统计清单下的物品数量与准备数量
     *
     * @param bagId    清单 ID，可为空
     * @param category 分类，可为空
     * @return 含 total 与 preparedCount 的单行
     */
    Map<String, Object> summary(@Param("bagId") Long bagId, @Param("category") String category);
}
