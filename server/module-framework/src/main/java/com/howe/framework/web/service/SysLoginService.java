package com.howe.framework.web.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.howe.common.constant.Constants;
import com.howe.common.constant.FamilyRoleConstants;
import com.howe.common.constant.UserConstants;
import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.core.domain.model.LoginBody;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.exception.user.BlackListException;
import com.howe.common.exception.user.CaptchaException;
import com.howe.common.exception.user.CaptchaExpireException;
import com.howe.common.exception.user.TurnstileException;
import com.howe.common.exception.user.UserNotExistsException;
import com.howe.common.exception.user.UserPasswordNotMatchException;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.MessageUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.ip.IpUtils;
import com.howe.framework.captcha.CaptchaService;
import com.howe.framework.captcha.TurnstileService;
import com.howe.framework.manager.AsyncManager;
import com.howe.framework.manager.factory.AsyncFactory;
import com.howe.framework.security.context.AuthenticationContextHolder;
import com.howe.system.service.ISysConfigService;
import com.howe.system.service.ISysUserService;

/**
 * 登录校验方法
 *
 * @author howe
 */
@Component
public class SysLoginService
{
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private TurnstileService turnstileService;

    /**
     * 登录验证
     *
     * @param loginBody 登录参数
     * @return 结果
     */
    public String login(LoginBody loginBody)
    {
        // 人机校验：挡住脚本化的批量尝试，比图形验证码更靠前
        validateTurnstile(loginBody.getUsername(), loginBody.getTurnstileToken());
        // 验证码校验
        validateCaptcha(loginBody.getUsername(), loginBody.getCode(), loginBody.getUuid());
        return doLogin(loginBody.getUsername(), loginBody.getPassword(), loginBody.getClient());
    }

    /**
     * 登录验证
     *
     * @param username 用户名
     * @param password 密码
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public String login(String username, String password, String code, String uuid)
    {
        // 验证码校验
        validateCaptcha(username, code, uuid);
        return doLogin(username, password, Constants.CLIENT_ADMIN);
    }

    /**
     * 走完 Spring Security 的认证流程并发 token
     *
     * @param username 用户名
     * @param password 密码
     * @param client   登录客户端，决定本次会话的有效期
     */
    private String doLogin(String username, String password, String client)
    {
        // 登录前置校验
        loginPreCheck(username, password);
        // 用户验证
        Authentication authentication = null;
        try
        {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            AuthenticationContextHolder.setContext(authenticationToken);
            // 该方法会去调用UserDetailsServiceImpl.loadUserByUsername
            authentication = authenticationManager.authenticate(authenticationToken);
        }
        catch (Exception e)
        {
            if (e instanceof BadCredentialsException)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
                throw new UserPasswordNotMatchException();
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, e.getMessage()));
                throw new ServiceException(e.getMessage());
            }
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        // 管理端只允许管理员与家庭管理员登录，点餐员/厨师等普通角色一律拒绝
        assertAdminConsoleAllowed(loginUser, client);
        recordLoginInfo(loginUser.getUserId());
        // 按客户端决定本次会话的有效期：点餐端 30 天，管理端 30 分钟
        loginUser.setExpireMinutes(Constants.CLIENT_MEAL.equals(client)
                ? tokenService.getMealExpireTime()
                : tokenService.getExpireTime());
        // 生成token
        return tokenService.createToken(loginUser);
    }

    /**
     * 校验 Cloudflare Turnstile 人机校验
     *
     * @param username 用户名
     * @param turnstileToken 前端提交的令牌
     */
    public void validateTurnstile(String username, String turnstileToken)
    {
        try
        {
            turnstileService.validate(turnstileToken);
        }
        catch (TurnstileException e)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, e.getMessage()));
            throw e;
        }
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public void validateCaptcha(String username, String code, String uuid)
    {
        if (!captchaService.isEnabled())
        {
            return;
        }
        try
        {
            captchaService.validate(uuid, code);
        }
        catch (CaptchaExpireException e)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire")));
            throw e;
        }
        catch (CaptchaException e)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error")));
            throw e;
        }
    }

    /**
     * 登录前置校验
     * @param username 用户名
     * @param password 用户密码
     */
    public void loginPreCheck(String username, String password)
    {
        // 用户名或密码为空 错误
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("not.null")));
            throw new UserNotExistsException();
        }
        // 密码如果不在指定范围内 错误
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        // 用户名不在指定范围内 错误
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        // IP黑名单校验
        String blackStr = configService.selectConfigByKey("sys.login.blackIPList");
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("login.blocked")));
            throw new BlackListException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param userId 用户ID
     */
    public void recordLoginInfo(Long userId)
    {
        userService.updateLoginInfo(userId, IpUtils.getIpAddr(), DateUtils.getNowDate());
    }

    /**
     * 校验当前账号是否允许登录管理端
     *
     * <p>
     * 管理端只给两类人用：管理员（{@code userId == 1}）与家庭管理员（{@code meal_manager}）。
     * 点餐员、厨师以及任何普通角色只能登录点餐端。
     * </p>
     *
     * <p>
     * <b>为什么在登录时拦而不是在接口上拦</b>：接口权限只能挡住「操作」，
     * 挡不住「界面」——普通角色登录后照样能拉到菜单树、部门树、用户列表的骨架，
     * 这些本身就是信息泄露面。在发 token 之前拒绝，连会话都不会建立。
     * </p>
     *
     * <p>
     * 点餐端（{@code client=meal}）不受此限制：点餐员和厨师本来就要用它。
     * </p>
     *
     * @param loginUser 登录用户
     * @param client 登录客户端
     * @throws ServiceException 无管理端权限时抛出
     */
    private void assertAdminConsoleAllowed(LoginUser loginUser, String client)
    {
        if (Constants.CLIENT_MEAL.equals(client))
        {
            return;
        }
        SysUser user = loginUser.getUser();
        if (user != null && user.isAdmin())
        {
            return;
        }
        boolean allowed = user != null && user.getRoles() != null && user.getRoles().stream()
                .filter(role -> role != null && UserConstants.ROLE_NORMAL.equals(role.getStatus()))
                .anyMatch(role -> FamilyRoleConstants.canAccessAdminConsole(role.getRoleKey()));
        if (!allowed)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(user == null ? "" : user.getUserName(),
                    Constants.LOGIN_FAIL, "无管理端登录权限"));
            throw new ServiceException("该账号无权登录管理端，请使用点餐端");
        }
    }
}
