package com.howe.common.utils.token;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.howe.common.config.HoweConfig;
import com.howe.common.constant.StrConstant;
import com.howe.common.dto.login.LoginUserDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.utils.ip.AddressUtils;
import com.howe.common.utils.ip.IpUtils;
import com.howe.common.utils.redis.RedisUtils;
import com.howe.common.utils.servlet.ServletUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/28 17:23 星期一
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Component
public class TokenUtils {

    @Autowired
    private HoweConfig howeConfig;

    @Autowired
    private RedisUtils redisUtils;


    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public LoginUserDTO getLoginUser(HttpServletRequest request) {
        // 获取请求携带的令牌
        String token = getToken(request);
        if (StringUtils.isNotEmpty(token)) {
            Claims claims = parseToken(token);
            // 解析对应的权限以及用户信息
            String uuid = (String) claims.get(StrConstant.LOGIN_USER_KEY);
            LoginUserDTO user = redisUtils.getObject(RedisKeyPrefixEnum.TOKEN_INFO, uuid, LoginUserDTO.class);
            return user;
        }
        return null;
    }


    /**
     * 验证令牌有效期，相差不足20分钟，自动刷新缓存
     *
     * @param loginUser
     * @return 令牌
     */
    public void verifyToken(LoginUserDTO loginUser) {
        Date expireTime = loginUser.getExpireTime();
        long between = DateUtil.between(DateTime.now(), expireTime, DateUnit.MINUTE, true);
        if (between <= howeConfig.getToken().getRefreshTokenTime()) {
            refreshToken(loginUser);
        }
    }

    public String createToken(LoginUserDTO loginUser) {
        String token = IdUtil.fastSimpleUUID();
        loginUser.setToken(token);
        this.setUserAgent(loginUser);
        this.refreshToken(loginUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put(StrConstant.LOGIN_USER_KEY, token);
        return this.createToken(claims);
    }

    private String createToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, howeConfig.getToken().getSecret())
                .compact();
    }

    private void refreshToken(LoginUserDTO loginUser) {
        Date now = DateTime.now();
        loginUser.setLoginTime(now);
        Integer expireTime = howeConfig.getToken().getExpireTime();
        DateTime expire = DateUtil.offsetMinute(now, expireTime);
        loginUser.setExpireTime(expire);
        redisUtils.set(RedisKeyPrefixEnum.TOKEN_INFO, loginUser.getToken(), loginUser, expireTime, TimeUnit.MINUTES);
    }

    /**
     * 设置用户角色
     *
     * @param loginUserDTO
     */
    private void setUserRoles(LoginUserDTO loginUserDTO) {
        UserDTO user = loginUserDTO.getUser();
    }

    /**
     * 设置用户代理消息
     *
     * @param loginUser
     */
    private void setUserAgent(LoginUserDTO loginUser) {
        UserAgent userAgent = UserAgentUtil.parse(ServletUtils.getHeader("User-Agent"));
        String ip = IpUtils.getIpAddr(ServletUtils.getRequest());
        loginUser.setIpaddr(ip);
        loginUser.setLoginLocation(AddressUtils.getAddressByIp(ip));
        loginUser.setBrowser(userAgent.getBrowser().getName());
        loginUser.setOs(userAgent.getOs().getName());
    }

    /**
     * 从令牌中获取数据声明
     *
     * @param token 令牌
     * @return 数据声明
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(howeConfig.getToken().getSecret())
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 获取请求token
     *
     * @param request
     * @return token
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(howeConfig.getToken().getHeader());
        return token;
    }
}
