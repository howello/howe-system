package com.howe.automation.service;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 自动化任务凭据读取服务。
 *
 * <p>任务调用参数只允许传递别名，真实账号密码从系统参数表读取，避免进入
 * {@code sys_job.invoke_target} 和调度日志。</p>
 */
@Slf4j
@Service
public class AutomationCredentialService
{
    private static final String KEY_PREFIX = "automation.credential.";

    /**
     * 按别名读取账号密码。
     *
     * @param alias 凭据别名，只允许字母、数字、点、下划线和短横线
     * @return 凭据
     */
    public AutomationCredentials getRequired(String alias)
    {
        validateAlias(alias);
        String prefix = KEY_PREFIX + alias + ".";
        String username = ConfigUtils.getRequired(prefix + "username", "自动化任务账号");
        String password = ConfigUtils.getRequired(prefix + "password", "自动化任务密码");
        return new AutomationCredentials(alias, username, password);
    }

    /**
     * 尝试读取凭据。账号密码都为空时返回 null，允许已有 Redis 登录态继续执行。
     *
     * @param alias 凭据别名
     * @return 凭据或 null
     */
    public AutomationCredentials getOptional(String alias)
    {
        validateAlias(alias);
        String prefix = KEY_PREFIX + alias + ".";
        String username = ConfigUtils.getString(prefix + "username");
        String password = ConfigUtils.getString(prefix + "password");
        if (StringUtils.isEmpty(username) && StringUtils.isEmpty(password))
        {
            return null;
        }
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))
        {
            throw new ServiceException("自动化凭据配置不完整：" + alias);
        }
        return new AutomationCredentials(alias, username, password);
    }

    private void validateAlias(String alias)
    {
        if (StringUtils.isEmpty(alias) || !alias.matches("[A-Za-z0-9._-]+"))
        {
            throw new ServiceException("自动化凭据别名不合法");
        }
    }

    /**
     * 自动化登录凭据。密码禁止写入日志和异常信息。
     */
    public record AutomationCredentials(String alias, String username, String password)
    {
    }
}
