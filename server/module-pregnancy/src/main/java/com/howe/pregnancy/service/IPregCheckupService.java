package com.howe.pregnancy.service;

import com.howe.pregnancy.domain.dto.CheckupAppointmentBody;
import com.howe.pregnancy.domain.dto.CheckupSaveBody;
import com.howe.pregnancy.domain.dto.CheckupStateBody;
import com.howe.pregnancy.domain.vo.CheckupVO;

import java.util.List;

/**
 * 产检服务
 *
 * @author howe
 */
public interface IPregCheckupService {

    /**
     * 查询产检时间轴
     *
     * @return 产检列表
     */
    List<CheckupVO> list();

    /**
     * 查询产检详情
     *
     * @param id 产检 ID
     * @return 详情
     */
    CheckupVO get(Long id);

    /**
     * 手动新增产检计划
     *
     * @param body 请求
     * @return 新建产检
     */
    CheckupVO create(CheckupSaveBody body);

    /**
     * 编辑产检重点、地点与建议日期
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    CheckupVO update(Long id, com.howe.pregnancy.domain.dto.CheckupUpdateBody body);

    /**
     * 原子替换项目列表
     *
     * @param id      产检 ID
     * @param version 父版本
     * @param items   项目
     * @return 更新后的产检
     */
    CheckupVO replaceItems(Long id, Integer version, List<CheckupSaveBody.DetailItem> items);

    /**
     * 原子替换准备事项列表
     *
     * @param id            产检 ID
     * @param version       父版本
     * @param preparations  准备事项
     * @return 更新后的产检
     */
    CheckupVO replacePreparations(Long id, Integer version,
                                  List<CheckupSaveBody.DetailItem> preparations);

    /**
     * 勾选或取消准备事项
     *
     * @param id     产检 ID
     * @param prepId 准备事项 ID
     * @param done   完成状态
     * @param version 父版本
     * @return 更新后的产检
     */
    CheckupVO togglePreparation(Long id, Long prepId, Boolean done, Integer version);

    /**
     * 保存预约（进入 BOOKED）
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    CheckupVO book(Long id, CheckupAppointmentBody body);

    /**
     * 撤销预约（回到 PLANNED）
     *
     * @param id      产检 ID
     * @param version 父版本
     * @return 更新后的产检
     */
    CheckupVO cancelAppointment(Long id, Integer version);

    /**
     * 标记完成
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    CheckupVO complete(Long id, CheckupStateBody body);

    /**
     * 撤销完成
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    CheckupVO reopen(Long id, CheckupStateBody body);

    /**
     * 取消整次计划
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    CheckupVO cancel(Long id, CheckupStateBody body);

    /**
     * 幂等初始化默认产检清单
     *
     * @return 初始化的条数
     */
    int ensureSeeded();

    /** 核对现有产检计划的默认待办。 */
    void reconcileCheckupTodos();
}
