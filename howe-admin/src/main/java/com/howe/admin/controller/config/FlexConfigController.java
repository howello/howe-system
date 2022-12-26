package com.howe.admin.controller.config;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageInfo;
import com.howe.common.dto.flexConfig.ConfigDTO;
import com.howe.common.enums.flexConfig.FlexConfigBizTypeEnum;
import com.howe.common.utils.config.FlexConfigUtils;
import com.howe.common.utils.convert.ConvertUtils;
import com.howe.common.utils.request.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/22 17:32 星期二
 * <p>@Version 1.0
 * <p>@Description 灵活配置
 */
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@Api(tags = "灵活配置")
public class FlexConfigController {

    private final FlexConfigUtils flexConfigUtils;

    @GetMapping("/getBizTypeMap")
    @ApiOperation(value = "获取业务类型的map数据", httpMethod = "GET")
    public AjaxResult<List<JSONObject>> getBizTypeMap() {
        List<JSONObject> jsonObjectList = ConvertUtils.enumToList(FlexConfigBizTypeEnum.class);
        return AjaxResult.success(jsonObjectList);
    }

    @GetMapping("/getConfigList")
    @ApiOperation(value = "获取配置列表", httpMethod = "GET")
    public AjaxResult<List<ConfigDTO>> getConfigList(ConfigDTO configDTO) {
        List<ConfigDTO> configPage = flexConfigUtils.getConfigList(configDTO);
        return AjaxResult.success(configPage);
    }

    @GetMapping("/getConfigPage")
    @ApiOperation(value = "分页获取配置", httpMethod = "GET")
    public AjaxResult<PageInfo<ConfigDTO>> getConfigPage(ConfigDTO configDTO) {
        PageInfo<ConfigDTO> configPage = flexConfigUtils.getConfigPage(configDTO);
        return AjaxResult.success(configPage);
    }

    @GetMapping("/getConfigByRuleId")
    @ApiOperation(value = "根据主键获取配置", httpMethod = "GET")
    public AjaxResult<ConfigDTO> getConfigByRuleId(String ruleId) {
        ConfigDTO configByRuleId = flexConfigUtils.getConfigByRuleId(ruleId);
        return AjaxResult.success(configByRuleId);
    }

    @PutMapping("/updateConfig")
    @ApiOperation(value = "更新配置", httpMethod = "PUT")
    public AjaxResult<Boolean> updateConfig(@RequestBody ConfigDTO configDTO) {
        flexConfigUtils.updateConfig(configDTO);
        return AjaxResult.success();
    }

    @PutMapping("/saveConfig")
    @ApiOperation(value = "保存配置", httpMethod = "PUT")
    public AjaxResult<Boolean> saveConfig(@RequestBody ConfigDTO configDTO) {
        flexConfigUtils.saveConfig(configDTO);
        return AjaxResult.success();
    }

    @DeleteMapping("/delConfig")
    @ApiOperation(value = "删除配置", httpMethod = "DELETE")
    public AjaxResult<Boolean> delConfig(@RequestBody String[] ruleIds) {
        flexConfigUtils.disableConfig(ruleIds);
        return AjaxResult.success();
    }

    @GetMapping("/refreshCache")
    @ApiOperation(value = "清除缓存", httpMethod = "GET")
    public AjaxResult<Boolean> refreshCache() {
        flexConfigUtils.clearRedisConfigCache();
        return AjaxResult.success();
    }
}
