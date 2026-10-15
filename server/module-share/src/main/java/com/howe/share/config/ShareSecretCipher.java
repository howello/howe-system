package com.howe.share.config;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * WebDAV 源密码的加解密
 *
 * <p>用 AES-GCM 对 {@code share_source.password_cipher} 加密后入库，主密钥从环境变量读取
 * （{@code SHARE_SECRET_KEY}，或由 {@code howe.share.secret-key} 显式覆盖），不落库、不入 Git。</p>
 *
 * <p>加密走 JDK 内置的 JCE（{@code AES/GCM/NoPadding}），不新增依赖；随机 IV 用 hutool。
 * 密文带 {@value #PREFIX} 前缀，便于识别与后续换算法；主密钥缺失时直接抛错，
 * <b>不会静默退化成明文存储</b>。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class ShareSecretCipher {

    /** 密文前缀，用于区分已加密值与历史明文 */
    private static final String PREFIX = "enc:v1:";

    /** 变换算法 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** GCM 认证标签长度（位） */
    private static final int GCM_TAG_LENGTH = 128;

    /** GCM 推荐随机 IV 长度（字节） */
    private static final int GCM_IV_LENGTH = 12;

    /** AES 支持的密钥长度（字节） */
    private static final int[] KEY_LENGTHS = {16, 24, 32};

    /** 主密钥，Base64 编码的 16/24/32 字节；为空表示未配置 */
    private final String masterKey;

    /**
     * 构造加解密工具
     *
     * @param masterKey 主密钥，来自 {@code howe.share.secret-key}（默认取环境变量 SHARE_SECRET_KEY）
     */
    public ShareSecretCipher(@Value("${howe.share.secret-key:}") String masterKey) {
        this.masterKey = StrUtil.isBlank(masterKey) ? System.getenv("SHARE_SECRET_KEY") : masterKey;
    }

    /**
     * 加密源密码明文
     *
     * @param plain 明文密码，为空时原样返回
     * @return 带前缀的密文
     * @throws ServiceException 主密钥未配置或配置不合法
     */
    public String encrypt(String plain) {
        if (StrUtil.isBlank(plain)) {
            return plain;
        }
        byte[] keyBytes = resolveKeyBytes();
        byte[] iv = RandomUtil.randomBytes(GCM_IV_LENGTH);
        byte[] cipher = crypt(Cipher.ENCRYPT_MODE, keyBytes, iv, plain.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + cipher.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipher, 0, combined, iv.length, cipher.length);
        return PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 解密源密码密文
     *
     * @param stored 库中存储的密文
     * @return 明文密码；入参为空时返回空
     * @throws ServiceException 主密钥未配置、密文格式不合法或解密失败
     */
    public String decrypt(String stored) {
        if (StrUtil.isBlank(stored)) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new ServiceException("源密码不是本模块加密的数据，请重新保存该源的密码");
        }
        byte[] keyBytes = resolveKeyBytes();
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new ServiceException("源密码格式不合法，请重新保存该源的密码");
        }
        if (combined.length <= GCM_IV_LENGTH) {
            throw new ServiceException("源密码格式不合法，请重新保存该源的密码");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        byte[] cipher = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, cipher, 0, cipher.length);
        try {
            return new String(crypt(Cipher.DECRYPT_MODE, keyBytes, iv, cipher), StandardCharsets.UTF_8);
        } catch (ServiceException e) {
            // 主密钥换过之后旧密文解不开，给出可操作的提示而不是抛底层异常
            log.warn("源密码解密失败：{}", e.getMessage());
            throw new ServiceException("源密码解密失败，可能是主密钥已变更，请重新保存该源的密码");
        }
    }

    /**
     * 执行 AES-GCM 加解密
     *
     * @param mode     {@link Cipher#ENCRYPT_MODE} 或 {@link Cipher#DECRYPT_MODE}
     * @param keyBytes 密钥字节
     * @param iv       随机 IV
     * @param input    输入数据
     * @return 输出数据
     * @throws ServiceException 加解密失败
     */
    private byte[] crypt(int mode, byte[] keyBytes, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new ServiceException("源密码加解密失败");
        }
    }

    /**
     * 解析并校验主密钥
     *
     * @return 密钥字节
     * @throws ServiceException 主密钥未配置或长度不合法
     */
    private byte[] resolveKeyBytes() {
        if (StrUtil.isBlank(masterKey)) {
            throw new ServiceException("未配置源密码的主密钥（环境变量 SHARE_SECRET_KEY），无法加解密源密码");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(masterKey.trim());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("源密码的主密钥不是合法的 Base64 字符串，请检查环境变量 SHARE_SECRET_KEY");
        }
        for (int length : KEY_LENGTHS) {
            if (keyBytes.length == length) {
                return keyBytes;
            }
        }
        throw new ServiceException("源密码的主密钥长度不合法，Base64 解码后必须是 16/24/32 字节");
    }
}
