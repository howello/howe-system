package com.howe.pregnancy.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.domain.dto.CheckupAppointmentBody;
import com.howe.pregnancy.domain.dto.CheckupSaveBody;
import com.howe.pregnancy.domain.dto.CheckupStateBody;
import com.howe.pregnancy.domain.vo.CheckupVO;
import com.howe.pregnancy.service.IPregCheckupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产检 控制层
 *
 * <p>建议窗口与实际预约分开；状态动作都带父版本，完成日期不得晚于业务今天。</p>
 *
 * @author howe
 */
@Tag(name = "孕期-产检", description = "产检计划、预约、完成与准备事项")
@RestController
@RequestMapping("/pregnancy/checkups")
@RequiredArgsConstructor
public class PregCheckupController extends BaseController {

    private final IPregCheckupService checkupService;

    /**
     * 查询产检时间轴
     *
     * @return 产检列表
     */
    @Operation(summary = "查询产检", description = "按预约日期与建议窗口排序，首次读取幂等初始化默认清单")
    @GetMapping
    public AjaxResult list() {
        List<CheckupVO> list = checkupService.list();
        return success(list);
    }

    /**
     * 查询产检详情
     *
     * @param id 产检 ID
     * @return 详情
     */
    @Operation(summary = "查询产检详情", description = "包含项目、准备事项、关联报告与费用")
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable("id") Long id) {
        return success(checkupService.get(id));
    }

    /**
     * 手动新增产检计划
     *
     * @param body 请求
     * @return 新建产检
     */
    @Operation(summary = "新增产检计划", description = "手工计划可留空初始化键；同 requestId 同内容返回原记录")
    @Log(title = "孕期产检", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody CheckupSaveBody body) {
        return success(checkupService.create(body));
    }

    /**
     * 编辑产检
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    @Operation(summary = "编辑产检", description = "个性化重点、地点、建议日期；列表字段传值即原子替换")
    @Log(title = "孕期产检", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable("id") Long id,
                             @Valid @RequestBody com.howe.pregnancy.domain.dto.CheckupUpdateBody body) {
        return success(checkupService.update(id, body));
    }

    /**
     * 保存预约
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    @Operation(summary = "保存预约", description = "保存预约日期即进入 BOOKED；时刻可为空")
    @Log(title = "孕期产检", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/appointment")
    public AjaxResult book(@PathVariable("id") Long id,
                           @Valid @RequestBody CheckupAppointmentBody body) {
        return success(checkupService.book(id, body));
    }

    /**
     * 撤销预约
     *
     * @param id      产检 ID
     * @param version 父版本
     * @return 更新后的产检
     */
    @Operation(summary = "撤销预约", description = "回到 PLANNED，保留计划、报告与费用")
    @Log(title = "孕期产检", businessType = BusinessType.UPDATE)
    @DeleteMapping("/{id}/appointment")
    public AjaxResult cancelAppointment(@PathVariable("id") Long id,
                                        @RequestParam("version") Integer version) {
        return success(checkupService.cancelAppointment(id, version));
    }

    /**
     * 标记完成
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    @Operation(summary = "标记完成", description = "必须填写不晚于业务今天的实际完成日期")
    @Log(title = "孕期产检", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/complete")
    public AjaxResult complete(@PathVariable("id") Long id,
                               @Valid @RequestBody CheckupStateBody body) {
        return success(checkupService.complete(id, body));
    }

    /**
     * 撤销完成
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    @Operation(summary = "撤销完成", description = "按是否仍有预约恢复 BOOKED 或 PLANNED")
    @Log(title = "孕期产检", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/reopen")
    public AjaxResult reopen(@PathVariable("id") Long id,
                             @Valid @RequestBody CheckupStateBody body) {
        return success(checkupService.reopen(id, body));
    }

    /**
     * 取消整次计划
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    @Operation(summary = "取消计划", description = "进入 CANCELLED；不删除关联报告与费用")
    @Log(title = "孕期产检", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/cancel")
    public AjaxResult cancel(@PathVariable("id") Long id,
                             @Valid @RequestBody CheckupStateBody body) {
        return success(checkupService.cancel(id, body));
    }

    /**
     * 原子替换项目列表
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    @Operation(summary = "替换项目列表", description = "同一事务维护父版本")
    @PutMapping("/{id}/items")
    public AjaxResult replaceItems(@PathVariable("id") Long id,
                                   @Valid @RequestBody CheckupItemsBody body) {
        return success(checkupService.replaceItems(id, body.getVersion(), body.getItems()));
    }

    /**
     * 原子替换准备列表
     *
     * @param id   产检 ID
     * @param body 请求
     * @return 更新后的产检
     */
    @Operation(summary = "替换准备事项列表", description = "同一事务维护父版本")
    @PutMapping("/{id}/preparations")
    public AjaxResult replacePreparations(@PathVariable("id") Long id,
                                          @Valid @RequestBody CheckupItemsBody body) {
        return success(checkupService.replacePreparations(id, body.getVersion(), body.getItems()));
    }

    /**
     * 勾选准备事项
     *
     * @param id     产检 ID
     * @param prepId 准备事项 ID
     * @param body   请求
     * @return 更新后的产检
     */
    @Operation(summary = "勾选准备事项", description = "返回更新后的父版本")
    @RequestMapping(path = "/{id}/preparations/{prepId}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public AjaxResult togglePreparation(@PathVariable("id") Long id,
                                        @PathVariable("prepId") Long prepId,
                                        @Valid @RequestBody PreparationDoneBody body) {
        return success(checkupService.togglePreparation(id, prepId, body.getDone(), body.getVersion()));
    }

    /**
     * 详情列表请求体
     */
    @lombok.Data
    @io.swagger.v3.oas.annotations.media.Schema(description = "产检详情列表请求")
    public static class CheckupItemsBody {

        @jakarta.validation.constraints.NotNull(message = "版本号不能为空")
        private Integer version;

        @jakarta.validation.Valid
        private List<CheckupSaveBody.DetailItem> items;
    }

    /**
     * 准备事项完成请求
     */
    @lombok.Data
    @io.swagger.v3.oas.annotations.media.Schema(description = "准备事项完成请求")
    public static class PreparationDoneBody {

        @jakarta.validation.constraints.NotNull(message = "完成状态不能为空")
        private Boolean done;

        @jakarta.validation.constraints.NotNull(message = "版本号不能为空")
        private Integer version;
    }
}
