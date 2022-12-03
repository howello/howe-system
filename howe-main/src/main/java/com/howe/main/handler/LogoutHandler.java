package com.howe.main.handler;

import com.alibaba.fastjson.JSON;
import com.howe.common.constant.HttpStatus;
import com.howe.common.utils.request.AjaxResult;
import com.howe.common.utils.servlet.ServletUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/30 10:59 星期三
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Component
public class LogoutHandler implements LogoutSuccessHandler {
    /**
     * @param request
     * @param response
     * @param authentication
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException,
            ServletException {
        //LoginUser loginUser = tokenService.getLoginUser(request);
        //if (StringUtils.isNotNull(loginUser)) {
        //    String userName = loginUser.getUsername();
        //    // 删除用户缓存记录
        //    tokenService.delLoginUser(loginUser.getToken());
        //    // 记录用户退出日志
        //    AsyncManager.me().execute(AsyncFactory.recordLogininfor(userName, Constants.LOGOUT, "退出成功"));
        //}
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(HttpStatus.SUCCESS, "退出成功")));
    }
}
