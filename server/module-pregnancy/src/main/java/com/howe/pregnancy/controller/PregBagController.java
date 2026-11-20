package com.howe.pregnancy.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.pregnancy.domain.dto.BagItemSaveBody;
import com.howe.pregnancy.domain.vo.BagItemVO;
import com.howe.pregnancy.domain.vo.BagVO;
import com.howe.pregnancy.service.IPregBagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 待产包 控制层
 *
 * @author howe
 */
@Tag(name = "孕期-待产包", description = "清单、物品与实付金额")
@RestController
@RequestMapping("/pregnancy")
@RequiredArgsConstructor
public class PregBagController extends BaseController {

    private final IPregBagService bagService;

    /**
     * 查询清单与默认清单
     *
     * @return 清单列表
     */
    @Operation(summary = "查询清单", description = "首次读取幂等初始化默认清单与 26 件初始物品")
    @GetMapping("/bags")
    public AjaxResult listBags() {
        List<BagVO> list = bagService.listBags();
        return success(list);
    }

    /**
     * 分页查询物品
     *
     * @param bagId    清单 ID
     * @param category 分类
     * @param prepared 准备状态
     * @return 分页结果
     */
    @Operation(summary = "查询物品", description = "按清单、分类与准备状态过滤")
    @GetMapping("/bag-items")
    public TableDataInfo listItems(
            @Parameter(description = "清单ID") @RequestParam(value = "bagId", required = false) Long bagId,
            @Parameter(description = "分类") @RequestParam(value = "category", required = false) String category,
            @Parameter(description = "是否已准备") @RequestParam(value = "prepared", required = false) Boolean prepared) {
        startPage();
        List<BagItemVO> list = bagService.listItems(bagId, category, prepared);
        return getDataTable(list);
    }

    /**
     * 查询物品详情
     *
     * @param id 物品 ID
     * @return 物品详情
     */
    @Operation(summary = "查询物品详情", description = "包含当前费用与内容生成状态")
    @GetMapping("/bag-items/{id}")
    public AjaxResult getItem(@PathVariable("id") Long id) {
        return success(bagService.getItem(id));
    }

    /**
     * 新增物品
     *
     * @param body 请求
     * @return 新建物品
     */
    @Operation(summary = "新增物品", description = "创建物品与金额字段；初始化物品不自动触发 AI")
    @Log(title = "孕期待产包", businessType = BusinessType.INSERT)
    @PostMapping("/bag-items")
    public AjaxResult createItem(@Valid @RequestBody BagItemSaveBody body) {
        return success(bagService.createItem(body));
    }

    /**
     * 编辑物品
     *
     * @param id   物品 ID
     * @param body 请求
     * @return 更新后的物品
     */
    @Operation(summary = "编辑物品", description = "金额字段省略不改、null 清除、0.00 保留零元明细")
    @Log(title = "孕期待产包", businessType = BusinessType.UPDATE)
    @PutMapping("/bag-items/{id}")
    public AjaxResult updateItem(@PathVariable("id") Long id,
                                 @Valid @RequestBody com.howe.pregnancy.domain.dto.BagItemUpdateBody body) {
        return success(bagService.updateItem(id, body));
    }

    /**
     * 删除物品
     *
     * @param id      物品 ID
     * @param version 版本
     * @return 操作结果
     */
    @Operation(summary = "删除物品", description = "软删其费用并取消未完成的生成任务")
    @Log(title = "孕期待产包", businessType = BusinessType.DELETE)
    @DeleteMapping("/bag-items/{id}")
    public AjaxResult removeItem(@PathVariable("id") Long id,
                                 @RequestParam("version") Integer version) {
        bagService.deleteItem(id, version);
        return success();
    }

    /**
     * 查询清单汇总
     *
     * @param bagId    清单 ID
     * @param category 分类
     * @return 汇总
     */
    @Operation(summary = "查询清单汇总", description = "返回数量、已准备数量、实付小计与总计")
    @GetMapping("/bag-summary")
    public AjaxResult summary(
            @Parameter(description = "清单ID") @RequestParam(value = "bagId", required = false) Long bagId,
            @Parameter(description = "分类") @RequestParam(value = "category", required = false) String category) {
        Map<String, Object> summary = bagService.summary(bagId, category);
        return success(summary);
    }
}
