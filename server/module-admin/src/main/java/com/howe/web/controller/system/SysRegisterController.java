package com.howe.web.controller.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.howe.common.annotation.Anonymous;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.domain.model.FamilyRegisterBody;
import com.howe.common.core.domain.model.RegisterBody;
import com.howe.common.utils.StringUtils;
import com.howe.framework.web.service.SysRegisterService;
import com.howe.system.service.ISysConfigService;

/**
 * 注册验证
 * 
 * @author howe
 */
@Tag(name = "用户注册", description = "匿名接口，需在参数配置中开启注册功能后方可使用")
@RestController
public class SysRegisterController extends BaseController
{
    @Autowired
    private SysRegisterService registerService;

    @Autowired
    private ISysConfigService configService;

    @Operation(summary = "用户注册", description = "匿名接口。参数 sys.account.registerUser 未开启时直接返回错误")
    @PostMapping("/register")
    public AjaxResult register(@RequestBody RegisterBody user)
    {
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser"))))
        {
            return error("当前系统没有开启注册功能！");
        }
        String msg = registerService.register(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }

    /**
     * 家庭注册：通过家庭代码加入已有家庭
     *
     * <p>与既有匿名注册 {@code /register} 相互独立：不受
     * {@code sys.account.registerUser} 开关约束，始终开放；注册成功后绑定目标家庭，
     * 并赋予默认家庭角色「点餐员」。手机端点餐端使用该接口。</p>
     */
    @Operation(summary = "家庭注册", description = "匿名接口。需要填写家庭代码（目标家庭的部门ID），校验通过后加入对应家庭并获得默认点餐员角色；不受注册开关约束")
    @Anonymous
    @PostMapping("/register/family")
    public AjaxResult registerFamily(@RequestBody FamilyRegisterBody user)
    {
        String msg = registerService.registerFamily(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }
}
