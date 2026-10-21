package com.howe.ai.config;

import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.crypto.SecretCipher;
import org.springframework.stereotype.Component;

/**
 * AI 渠道密钥的加解密门面
 *
 * <p>
 * 加解密实现已下沉到 {@link SecretCipher}（全局主密钥 {@code HOWE_SECRET_KEY}），
 * 本类只保留 AI 侧的错误码语义：调用方按 {@link AiException} 处理，不需要感知
 * 底层抛的是通用业务异常还是 AI 专用异常。
 * </p>
 *
 * <p>
 * 对外签名与旧实现完全一致，因此 {@code AbstractAiProviderAdapter} 等调用点无需改动。
 * </p>
 *
 * @author howe
 */
@Component
public class AiSecretCipher
{
    /** 全局加解密实现 */
    private final SecretCipher delegate;

    /**
     * 构造门面
     *
     * @param delegate 全局加解密实现
     */
    public AiSecretCipher(SecretCipher delegate)
    {
        this.delegate = delegate;
    }

    /**
     * 加密渠道密钥明文
     *
     * @param plain 明文密钥，为空时原样返回
     * @return 带前缀的密文
     * @throws AiException 主密钥未配置或配置不合法
     */
    public String encrypt(String plain)
    {
        try
        {
            return delegate.encrypt(plain);
        }
        catch (ServiceException e)
        {
            throw new AiException(AiErrorCode.AI_DISABLED, e.getMessage(), e);
        }
    }

    /**
     * 解密渠道密钥密文
     *
     * @param stored 库中存储的密文
     * @return 明文密钥；入参为空时返回空
     * @throws AiException 主密钥未配置、密文格式不合法或解密失败
     */
    public String decrypt(String stored)
    {
        try
        {
            return delegate.decrypt(stored);
        }
        catch (ServiceException e)
        {
            throw new AiException(AiErrorCode.AUTH_FAILED, e.getMessage(), e);
        }
    }

    /**
     * 生成展示用掩码
     *
     * @param stored 库中存储的密文
     * @return 形如 {@code sk-****abcd} 的掩码；无法解密时返回固定占位
     */
    public String mask(String stored)
    {
        return delegate.mask(stored);
    }
}
