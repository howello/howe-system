package com.howe.share.config;

import com.howe.common.utils.crypto.SecretCipher;
import org.springframework.stereotype.Component;

/**
 * WebDAV 源密码的加解密门面
 *
 * <p>
 * 加解密实现已下沉到 {@link SecretCipher}（全局主密钥 {@code HOWE_SECRET_KEY}），
 * 本类只作为模块内的注入点，保证 {@code ShareSourceServiceImpl} 的依赖不变。
 * </p>
 *
 * <p>
 * <b>迁移提示</b>：存量 {@code share_source.password_cipher} 此前用独立主密钥加密。
 * 主密钥统一后，若新旧值不同，这些密文无法解密，需要在「分享管理 &gt; WebDAV 源」
 * 里重新保存一次密码，详见 {@code docs/防攻击/} 下的升级文档。
 * </p>
 *
 * @author howe
 */
@Component
public class ShareSecretCipher
{
    /** 全局加解密实现 */
    private final SecretCipher delegate;

    /**
     * 构造门面
     *
     * @param delegate 全局加解密实现
     */
    public ShareSecretCipher(SecretCipher delegate)
    {
        this.delegate = delegate;
    }

    /**
     * 加密源密码明文
     *
     * @param plain 明文密码，为空时原样返回
     * @return 带前缀的密文
     */
    public String encrypt(String plain)
    {
        return delegate.encrypt(plain);
    }

    /**
     * 解密源密码密文
     *
     * @param stored 库中存储的密文
     * @return 明文密码；入参为空时返回空
     */
    public String decrypt(String stored)
    {
        return delegate.decrypt(stored);
    }
}
