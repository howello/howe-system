package com.howe.admin.controller.dic;

import com.github.pagehelper.PageInfo;
import com.howe.common.dto.dic.DicDataDTO;
import com.howe.common.dto.dic.DictTypeDTO;
import com.howe.common.enums.StatusEnum;
import com.howe.common.utils.dic.DictionaryUtils;
import com.howe.common.utils.request.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/5 18:10 星期一
 * <p>@Version 1.0
 * <p>@Description 字典控制类
 */
@RestController
@RequestMapping("/dic")
@Api(tags = "字典")
@RequiredArgsConstructor
public class DicController {

    private final DictionaryUtils dictionaryUtils;

    @GetMapping("/getAllDicList")
    @ApiOperation(value = "获取所有字典", httpMethod = "GET")
    public AjaxResult<List<DicDataDTO>> getAllDicList() {
        DicDataDTO dicDataDTO = new DicDataDTO();
        List<DicDataDTO> dicList = dictionaryUtils.getDicList(dicDataDTO);
        return AjaxResult.success(dicList);
    }

    @GetMapping("/getDicPage")
    @ApiOperation(value = "分页获取字典", httpMethod = "GET")
    public AjaxResult<PageInfo<DicDataDTO>> getDicPage(@NotNull DicDataDTO dicData) {
        dicData.setPageNum(dicData.getPageNum());
        dicData.setPageSize(dicData.getPageSize());
        PageInfo<DicDataDTO> dicPage = dictionaryUtils.getDicPage(dicData);
        return AjaxResult.success(dicPage);
    }

    @GetMapping("/getDicDataListByType")
    @ApiOperation(value = "根据字典类型获取字典数据列表", httpMethod = "GET")
    public AjaxResult<List<DicDataDTO>> getDicDataListByType(String dicType) {
        List<DicDataDTO> dicList = dictionaryUtils.getDicDataListByType(dicType);
        return AjaxResult.success(dicList);
    }

    @GetMapping("/getDicDataByDictCode")
    @ApiOperation(value = "根据code获取字典数据", httpMethod = "GET")
    public AjaxResult<DicDataDTO> getDicDataByDictCode(Long dicCode) {
        return AjaxResult.success(dictionaryUtils.getDicListById(dicCode));
    }

    @PostMapping("/saveDicData")
    @ApiOperation(value = "保存字典数据", httpMethod = "POST")
    public AjaxResult<Integer> saveDicData(@RequestBody @Valid DicDataDTO dicData) {
        int i = dictionaryUtils.saveDicData(dicData);
        return AjaxResult.success(i);
    }

    @PostMapping("/updateDicDate")
    @ApiOperation(value = "更新字典数据", httpMethod = "POST")
    public AjaxResult<Integer> updateDicDate(@RequestBody @Valid DicDataDTO dicData) {
        int i = dictionaryUtils.updateDicDate(dicData);
        return AjaxResult.success(i);
    }

    @DeleteMapping("/delDicData")
    @ApiOperation(value = "删除字典数据", httpMethod = "DELETE")
    public AjaxResult<Integer> delDicData(Long[] dictCodes) {
        int i = dictionaryUtils.delDicData(dictCodes);
        return AjaxResult.success(i);
    }

    @GetMapping("/getAllDicTypeList")
    @ApiOperation(value = "获取所有字典类型列表", httpMethod = "GET")
    public AjaxResult<List<DictTypeDTO>> getAllDicTypeList() {
        DictTypeDTO dictTypeDTO = new DictTypeDTO();
        dictTypeDTO.setStatus(StatusEnum.NORMAL.getCodeStr());
        List<DictTypeDTO> dicTypeList = dictionaryUtils.getDicTypeList(dictTypeDTO);
        return AjaxResult.success(dicTypeList);
    }

    @GetMapping("/getDicTypePage")
    @ApiOperation(value = "分页获取字典类型列表", httpMethod = "GET")
    public AjaxResult<PageInfo<DictTypeDTO>> getDicTypePage(@Valid @NotNull DictTypeDTO dictTypeDTO) {
        PageInfo<DictTypeDTO> page = dictionaryUtils.getDicTypePage(dictTypeDTO);
        return AjaxResult.success(page);
    }

    @GetMapping("/getDicTypeById")
    @ApiOperation(value = "根据id获取字典类型", httpMethod = "GET")
    public AjaxResult<DictTypeDTO> getDicTypeById(Long dicTypeId) {
        DictTypeDTO dictTypeDTO = dictionaryUtils.getDicTypeById(dicTypeId);
        return AjaxResult.success(dictTypeDTO);
    }

    @PostMapping("/updateDicType")
    @ApiOperation(value = "更新字典类型", httpMethod = "POST")
    public AjaxResult<Integer> updateDicType(@RequestBody @Valid DictTypeDTO dictType) {
        int i = dictionaryUtils.updateType(dictType);
        return AjaxResult.success(i);
    }

    @PostMapping("/saveDicType")
    @ApiOperation(value = "保存字典类型", httpMethod = "POST")
    public AjaxResult<Integer> saveDicType(@RequestBody @Valid DictTypeDTO dictType) {
        int i = dictionaryUtils.saveDicType(dictType);
        return AjaxResult.success(i);
    }

    @DeleteMapping("/delDicType")
    @ApiOperation(value = "删除字典类型", httpMethod = "DELETE")
    public AjaxResult<Integer> delDicType(Long[] dicTypeIds) {
        int i = dictionaryUtils.delDicType(dicTypeIds);
        return AjaxResult.success(i);
    }

    @GetMapping("/refreshCache")
    @ApiOperation(value = "刷新缓存", httpMethod = "GET")
    public AjaxResult<Boolean> refreshCache() {
        Boolean b = dictionaryUtils.refreshCache();
        return AjaxResult.success(b);
    }
}
