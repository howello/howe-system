package com.howe.framework.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.howe.common.constant.Constants;
import com.howe.common.constant.FamilyRoleConstants;
import com.howe.common.constant.UserConstants;
import com.howe.common.core.domain.entity.SysDept;
import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.core.domain.model.FamilyRegisterBody;
import com.howe.common.core.domain.model.RegisterBody;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.MessageUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.framework.captcha.CaptchaService;
import com.howe.framework.captcha.TurnstileService;
import com.howe.framework.manager.AsyncManager;
import com.howe.framework.manager.factory.AsyncFactory;
import com.howe.system.service.ISysDeptService;
import com.howe.system.service.ISysRoleService;
import com.howe.system.service.ISysUserService;

/**
 * 注册校验方法
 *
 * @author howe
 */
@Component
public class SysRegisterService
{
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private TurnstileService turnstileService;

    /**
     * 注册
     */
    public String register(RegisterBody registerBody)
    {
        String msg = "", username = registerBody.getUsername(), password = registerBody.getPassword();
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);

        // 人机校验，未开启时直接放行
        turnstileService.validate(registerBody.getTurnstileToken());

        // 验证码开关
        if (captchaService.isEnabled())
        {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }

        if (StringUtils.isEmpty(username))
        {
            msg = "用户名不能为空";
        }
        else if (StringUtils.isEmpty(password))
        {
            msg = "用户密码不能为空";
        }
        else if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            msg = "账户长度必须在2到20个字符之间";
        }
        else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            msg = "密码长度必须在5到20个字符之间";
        }
        else if (!userService.checkUserNameUnique(sysUser))
        {
            msg = "保存用户'" + username + "'失败，注册账号已存在";
        }
        else
        {
            sysUser.setNickName(username);
            sysUser.setPwdUpdateDate(DateUtils.getNowDate());
            sysUser.setPassword(SecurityUtils.encryptPassword(password));
            boolean regFlag = userService.registerUser(sysUser);
            if (!regFlag)
            {
                msg = "注册失败,请联系系统管理人员";
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success")));
            }
        }
        return msg;
    }

    /**
     * 家庭注册：通过家庭代码加入已有家庭
     *
     * <p>与既有匿名注册 {@code /register} 相互独立：不受
     * {@code sys.account.registerUser} 开关约束；校验通过后立即创建用户，
     * 绑定目标家庭（家庭代码即 {@code sys_dept.dept_id}），并赋予默认家庭角色
     * 「点餐员」（{@code meal_eater}）。</p>
     *
     * @param registerBody 家庭注册参数
     * @return 空字符串表示注册成功，否则为可读的错误原因
     */
    public String registerFamily(FamilyRegisterBody registerBody)
    {
        String msg = "";
        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        String nickName = registerBody.getNickName();
        String phonenumber = registerBody.getPhonenumber();

        // 人机校验，未开启时直接放行
        turnstileService.validate(registerBody.getTurnstileToken());

        // 验证码开关
        if (captchaService.isEnabled())
        {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }

        Long familyDeptId = parseFamilyCode(registerBody.getFamilyCode());
        SysDept family = familyDeptId == null ? null : deptService.selectDeptById(familyDeptId);

        if (familyDeptId == null)
        {
            msg = "家庭代码不能为空";
        }
        else if (family == null || !"0".equals(family.getDelFlag()) || !"0".equals(family.getStatus()))
        {
            msg = "家庭代码无效，请向家庭管理员确认";
        }
        else if (StringUtils.isEmpty(username))
        {
            msg = "用户名不能为空";
        }
        else if (StringUtils.isEmpty(password))
        {
            msg = "用户密码不能为空";
        }
        else if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            msg = "账户长度必须在2到20个字符之间";
        }
        else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            msg = "密码长度必须在5到20个字符之间";
        }
        else if (StringUtils.isEmpty(nickName))
        {
            msg = "昵称不能为空";
        }
        else if (nickName.length() > 30)
        {
            msg = "昵称长度不能超过30个字符";
        }
        else if (StringUtils.isNotEmpty(phonenumber) && phonenumber.length() > 11)
        {
            msg = "手机号码长度不能超过11个字符";
        }
        else if (!userService.checkUserNameUnique(buildProbe(username, phonenumber)))
        {
            msg = "保存用户'" + username + "'失败，注册账号已存在";
        }
        else if (StringUtils.isNotEmpty(phonenumber) && !userService.checkPhoneUnique(buildProbe(username, phonenumber)))
        {
            msg = "注册失败，手机号码已存在";
        }
        else
        {
            SysRole eaterRole = findEnabledFamilyRole(FamilyRoleConstants.ROLE_EATER);
            if (eaterRole == null)
            {
                msg = "注册失败，家庭默认角色未配置，请联系系统管理人员";
            }
            else
            {
                SysUser sysUser = new SysUser();
                sysUser.setUserName(username);
                sysUser.setNickName(nickName);
                sysUser.setDeptId(familyDeptId);
                sysUser.setPhonenumber(phonenumber);
                sysUser.setStatus("0");
                sysUser.setCreateBy(username);
                sysUser.setPwdUpdateDate(DateUtils.getNowDate());
                sysUser.setPassword(SecurityUtils.encryptPassword(password));
                sysUser.setRoleIds(new Long[] { eaterRole.getRoleId() });
                if (userService.insertUser(sysUser) <= 0)
                {
                    msg = "注册失败,请联系系统管理人员";
                }
                else
                {
                    AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success")));
                }
            }
        }
        return msg;
    }

    /**
     * 解析家庭代码：家庭代码即目标家庭的部门ID
     *
     * @param familyCode 用户输入的家庭代码
     * @return 部门ID；为空或非数字时返回 null
     */
    private Long parseFamilyCode(String familyCode)
    {
        if (StringUtils.isEmpty(familyCode))
        {
            return null;
        }
        try
        {
            return Long.valueOf(familyCode.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * 按角色标识查找启用中的家庭角色
     *
     * <p>用不做数据权限过滤的 {@code selectRoleByKey}：匿名注册没有登录态，
     * 走 {@code selectRoleAll} 会因 {@code @DataScope} 取不到当前用户而失败。</p>
     *
     * @param roleKey 角色权限字符串
     * @return 角色；不存在或已停用返回 null
     */
    private SysRole findEnabledFamilyRole(String roleKey)
    {
        SysRole role = roleService.selectRoleByKey(roleKey);
        return role != null && "0".equals(role.getStatus()) ? role : null;
    }

    /**
     * 构造用于唯一性校验的探针对象
     */
    private SysUser buildProbe(String username, String phonenumber)
    {
        SysUser probe = new SysUser();
        probe.setUserName(username);
        probe.setPhonenumber(phonenumber);
        return probe;
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
        captchaService.validate(uuid, code);
    }
}
