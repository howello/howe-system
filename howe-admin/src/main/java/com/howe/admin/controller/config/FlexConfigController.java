package com.howe.admin.controller.config;

import com.howe.common.utils.config.FlexConfigUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/22 17:32 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@RestController
@RequestMapping("/flexConfig")
public class FlexConfigController {

    @Resource
    private FlexConfigUtils flexConfigUtils;


}
