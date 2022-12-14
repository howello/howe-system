package com.howe.admin.service.login.impl;

import com.howe.admin.service.auth.RoleService;
import com.howe.admin.service.auth.UserService;
import com.howe.admin.service.login.LoginService;
import com.howe.common.dto.flexConfig.child.LoginConfigDTO;
import com.howe.common.dto.login.LoginDTO;
import com.howe.common.dto.login.LoginUserDTO;
import com.howe.common.dto.login.RegisterUserDTO;
import com.howe.common.dto.login.ValidCodeDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.exception.AdminExceptionEnum;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.enums.flexConfig.FlexConfigBizTypeEnum;
import com.howe.common.exception.child.AdminException;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.captcha.CaptchaUtils;
import com.howe.common.utils.config.FlexConfigUtils;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.token.SecurityUtils;
import com.howe.common.utils.token.TokenUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/7 17:08 ζζδΈ
 * <p>@Version 1.0
 * <p>@Description
 */
@Service
public class LoginServiceImpl implements LoginService {
    @Autowired
    private FlexConfigUtils flexConfigUtils;

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private CaptchaUtils captchaUtils;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private IdGeneratorUtil idGeneratorUtil;

    @Autowired
    private RoleService roleService;

    /**
     * η»ε½
     *
     * @param loginDTO
     * @return
     */
    @SneakyThrows
    @Override
    public String login(LoginDTO loginDTO) {
        LoginConfigDTO loginConfig = flexConfigUtils.getConfig(FlexConfigBizTypeEnum.LOGIN_CONFIG.getCode(), LoginConfigDTO.class);
        if (loginConfig.getCaptchaSwitch()) {
            captchaUtils.validateCaptcha(loginDTO.getUuid(), loginDTO.getCode());
        }
        // η¨ζ·ιͺθ―
        Authentication authentication = null;
        try {
            // θ―₯ζΉζ³δΌε»θ°η¨
            // {@link com.howe.admin.service.auth.impl.UserSecurityServiceImpl#loadUserByUsername}
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginDTO.getUsername(),
                    loginDTO.getPassword()));
        } catch (Exception e) {
            //TODO θ?°ε½η»ε½ε€±θ΄₯θ?°ε½
            if (e instanceof BadCredentialsException) {
                throw new CommonException(CommonExceptionEnum.WRONG_PASSWORD);
            } else {
                throw e;
            }
        }
        LoginUserDTO loginUser = (LoginUserDTO) authentication.getPrincipal();
        return tokenUtils.createToken(loginUser);
    }

    /**
     * ζ³¨ε
     *
     * @param registerUserDTO
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean register(RegisterUserDTO registerUserDTO) {
        String username = registerUserDTO.getUsername();
        LoginConfigDTO loginConfig = flexConfigUtils.getConfig(FlexConfigBizTypeEnum.LOGIN_CONFIG.getCode(), LoginConfigDTO.class);
        if (loginConfig.getCaptchaSwitch()) {
            captchaUtils.validateCaptcha(registerUserDTO.getUuid(), registerUserDTO.getCode());
        }
        if (!userService.checkUserNameUnique(username) || loginConfig.getBuiltInUser().contains(username)) {
            throw new AdminException(AdminExceptionEnum.USER_EXISTS);
        }

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(registerUserDTO, userDTO);
        if (StringUtils.isBlank(userDTO.getNickName())) {
            userDTO.setNickName(username);
        }
        userDTO.setPassword(SecurityUtils.encryptPassword(registerUserDTO.getPassword()));
        return userService.register(userDTO);
    }

    /**
     * θ·ειͺθ―η 
     *
     * @return
     */
    @Override
    public ValidCodeDTO getValidCode(ValidCodeDTO validCodeDTO) {
        String uuid = idGeneratorUtil.nextHex();
        String base64 = captchaUtils.generateCode(uuid, validCodeDTO.getHeight(), validCodeDTO.getWidth());
        LoginConfigDTO loginConfig = flexConfigUtils.getConfig(FlexConfigBizTypeEnum.LOGIN_CONFIG.getCode(), LoginConfigDTO.class);
        validCodeDTO.setCaptchaOnOff(loginConfig.getCaptchaSwitch());
        validCodeDTO.setCodeImg(base64);
        validCodeDTO.setUuid(uuid);
        return validCodeDTO;
    }
}
