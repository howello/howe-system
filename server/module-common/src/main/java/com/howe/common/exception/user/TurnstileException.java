package com.howe.common.exception.user;

/**
 * 人机校验异常类
 *
 * <p>
 * Cloudflare Turnstile 校验未通过时抛出。与验证码异常分开，是为了让前端能区分
 * 「图形验证码填错」和「人机校验失败需要重新过一次挑战」两种恢复动作。
 * </p>
 *
 * @author howe
 */
public class TurnstileException extends UserException
{
    private static final long serialVersionUID = 1L;

    public TurnstileException()
    {
        super("user.turnstile.error", null);
    }

    public TurnstileException(String code)
    {
        super(code, null);
    }
}
