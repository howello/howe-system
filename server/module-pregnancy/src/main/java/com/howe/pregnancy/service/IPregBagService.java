package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.dto.BagItemSaveBody;
import com.howe.pregnancy.domain.vo.BagItemVO;
import com.howe.pregnancy.domain.vo.BagVO;

import java.util.List;
import java.util.Map;

/**
 * 待产包服务
 *
 * @author howe
 */
public interface IPregBagService {

    /**
     * 查询清单列表（含默认清单与物品）
     *
     * @return 清单列表
     */
    List<BagVO> listBags();

    /**
     * 查询物品列表
     *
     * @param bagId    清单 ID，可为空
     * @param category 分类，可为空
     * @param prepared 准备状态，可为空
     * @return 物品列表
     */
    List<BagItemVO> listItems(Long bagId, String category, Boolean prepared);

    /**
     * 查询物品详情
     *
     * @param id 物品 ID
     * @return 物品
     */
    BagItemVO getItem(Long id);

    /**
     * 新增物品
     *
     * @param body 请求
     * @return 新建物品
     */
    BagItemVO createItem(BagItemSaveBody body);

    /**
     * 编辑物品（含金额字段原子维护）
     *
     * @param id   物品 ID
     * @param body 请求
     * @return 更新后的物品
     */
    BagItemVO updateItem(Long id, com.howe.pregnancy.domain.dto.BagItemUpdateBody body);

    /**
     * 逻辑删除物品
     *
     * @param id      物品 ID
     * @param version 版本
     */
    void deleteItem(Long id, Integer version);

    /**
     * 查询清单汇总
     *
     * @param bagId    清单 ID，可为空
     * @param category 分类，可为空
     * @return 数量、已准备数量、实付小计与总计
     */
    Map<String, Object> summary(Long bagId, String category);

    /**
     * 幂等初始化默认清单与物品
     *
     * @return 初始化的物品数
     */
    int ensureSeeded();
}
