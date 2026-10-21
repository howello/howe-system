package com.howe.web.controller.system;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.system.service.ISysUserService;

/**
 * 首页
 *
 * <p>
 * 根路径 {@code GET /} 已不再由本类处理：改为返回 {@code static/index.html} 站点导航页。
 * 该页面是纯静态资源，由 Spring Boot 默认静态资源处理直接提供，不需要 Controller；
 * 原来的「欢迎使用 xxx 后台管理框架」文本已移除，避免对外暴露框架名与版本号。
 * </p>
 *
 * @author howe
 */
@Tag(name = "首页", description = "锁屏解锁")
@RestController
public class SysIndexController
{
    @Autowired
    private ISysUserService userService;

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
