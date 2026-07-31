package com.howe.web.controller.system;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.howe.common.config.YmlConfig;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.system.service.ISysUserService;

/**
 * 首页
 *
 * @author howe
 */
@Tag(name = "首页", description = "服务欢迎页与锁屏解锁")
@RestController
public class SysIndexController
{
    /** 系统基础配置 */
    @Autowired
    private YmlConfig ymlConfig;

    @Autowired
    private ISysUserService userService;

    /**
     * 访问首页，提示语
     */
    @Operation(summary = "访问首页", description = "返回框架名称与版本的提示语，引导通过前端地址访问")
    @RequestMapping("/")
    public String index()
    {
        return StringUtils.format("欢迎使用{}后台管理框架，当前版本：v{}，请通过前端地址访问。", ymlConfig.getName(), ymlConfig.getVersion());
    }

    /**
     * 解锁屏幕
     */
    @Operation(summary = "解锁屏幕", description = "校验当前登录用户的密码以解除锁屏，需携带有效令牌")
    @PostMapping("/unlockscreen")
    public AjaxResult unlockScreen(@RequestBody Map<String, String> body)
    {
        String password = body.get("password");
        if (StringUtils.isEmpty(password))
        {
            return AjaxResult.error("密码不能为空");
        }
        String username = SecurityUtils.getUsername();
        SysUser user = userService.selectUserByUserName(username);
        if (user == null)
        {
            return AjaxResult.error("服务器超时，请重新登录");
        }
        if (!SecurityUtils.matchesPassword(password, user.getPassword()))
        {
            return AjaxResult.error("密码错误，请重新输入");
        }

        return AjaxResult.success("解锁成功");
    }
}
