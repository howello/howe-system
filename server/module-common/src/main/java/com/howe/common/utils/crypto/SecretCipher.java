package com.howe.common.utils.crypto;

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
 * 敏感字段的全局加解密
 *
 * <p>
 * 项目里有多处需要加密入库的敏感值（AI 渠道密钥、WebDAV 源密码、后续的参数表敏感项），
 * 它们此前各写了一份几乎相同的 AES-GCM 实现。本类把这些逻辑收敛到一处：
 * 算法、密文格式、主密钥解析只有一份，避免各模块的实现悄悄分叉。
 * </p>
 *
 * <p>
 * 密文格式：{@code enc:v1:} + Base64(IV ‖ 密文 ‖ 认证标签)，IV 为 12 字节随机值。
 * 前缀用于区分已加密值与历史明文，也便于将来换算法时识别旧数据。
 * 主密钥从 {@code howe.secret-key}（默认取环境变量 {@code HOWE_SECRET_KEY}）读取，
 * 必须是 Base64 编码的 16/24/32 字节；未配置时直接抛错，
 * <b>不会静默退化成明文存储</b>。
 * </p>
 *
 * @author howe
 */
@Slf4j
@Component
public class SecretCipher
{
    /** 密文前缀，用于区分已加密值与历史明文 */
    public static final String PREFIX = "enc:v1:";

    /** 变换算法 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** GCM 认证标签长度（位） */
    private static final int GCM_TAG_LENGTH = 128;

    /** GCM 推荐随机 IV 长度（字节） */
    private static final int GCM_IV_LENGTH = 12;

    /** AES 支持的密钥长度（字节） */
    private static final int[] KEY_LENGTHS = { 16, 24, 32 };

    /** 主密钥，Base64 编码的 16/24/32 字节；为空表示未配置 */
    private final String masterKey;

    /**
     * 构造加解密工具
     *
     * @param masterKey 主密钥，来自 {@code howe.secret-key}（由环境变量 {@code HOWE_SECRET_KEY} 注入）
     */
    public SecretCipher(@Value("${howe.secret-key:}") String masterKey)
    {
        this.masterKey = StrUtil.isBlank(masterKey) ? "" : masterKey.trim();
    }

    /**
     * 加密敏感值
     *
     * @param plain 明文，为空时原样返回
     * @return 带 {@value #PREFIX} 前缀的密文
     * @throws ServiceException 主密钥未配置或配置不合法
     */
    public String encrypt(String plain)
    {
        if (StrUtil.isBlank(plain))
        {
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
     * 解密敏感值
     *
     * @param stored 库中存储的密文
     * @return 明文；入参为空时返回空
     * @throws ServiceException 主密钥未配置、密文格式不合法或解密失败
     */
    public String decrypt(String stored)
    {
        if (StrUtil.isBlank(stored))
        {
            return stored;
        }
        if (!stored.startsWith(PREFIX))
        {
            throw new ServiceException("该值不是本系统加密的数据，请重新保存");
        }
        byte[] keyBytes = resolveKeyBytes();
        byte[] combined;
        try
        {
            combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("密文格式不合法，请重新保存");
        }
        if (combined.length <= GCM_IV_LENGTH)
        {
            throw new ServiceException("密文格式不合法，请重新保存");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        byte[] cipher = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, cipher, 0, cipher.length);
        try
        {
            return new String(crypt(Cipher.DECRYPT_MODE, keyBytes, iv, cipher), StandardCharsets.UTF_8);
        }
        catch (ServiceException e)
        {
            // 主密钥换过之后旧密文解不开，给出可操作的提示而不是抛底层异常
            log.warn("敏感值解密失败：{}", e.getMessage());
            throw new ServiceException("解密失败，可能是主密钥已变更，请重新保存");
        }
    }

    /**
     * 判断值是否为本类产生的密文
     *
     * <p>
     * 用于兼容历史明文：调用方据此决定是直接使用还是先解密，
     * 避免把明文当密文解而报错。
     * </p>
     *
     * @param value 待判断的值
     * @return true 表示已是密文
     */
    public boolean isEncrypted(String value)
    {
        return StrUtil.isNotBlank(value) && value.startsWith(PREFIX);
    }

    /**
     * 生成展示用掩码
     *
     * @param plain 明文（不是密文）
     * @return 形如 {@code sk-****abcd} 的掩码；长度不足时返回固定占位
     */
    public String maskPlain(String plain)
    {
        if (StrUtil.isBlank(plain))
        {
            return "";
        }
        if (plain.length() <= 8)
        {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    /**
     * 解密后生成掩码
     *
     * @param stored 库中存储的密文
     * @return 形如 {@code sk-****abcd} 的掩码；解密失败时返回固定占位
     */
    public String mask(String stored)
    {
        if (StrUtil.isBlank(stored))
        {
            return "";
        }
        try
        {
            return maskPlain(decrypt(stored));
        }
        catch (ServiceException e)
        {
            log.warn("生成掩码时解密失败：{}", e.getMessage());
            return "****";
        }
    }

    /**
     * 执行 AES-GCM 加解密
     *
     * @param mode {@link Cipher#ENCRYPT_MODE} 或 {@link Cipher#DECRYPT_MODE}
     * @param keyBytes 密钥字节
     * @param iv 随机 IV
     * @param input 输入数据
     * @return 输出数据
     * @throws ServiceException 加解密失败
     */
    private byte[] crypt(int mode, byte[] keyBytes, byte[] iv, byte[] input)
    {
        try
        {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(input);
        }
        catch (GeneralSecurityException e)
        {
            throw new ServiceException("敏感值加解密失败");
        }
    }

    /**
     * 解析并校验主密钥
     *
     * @return 密钥字节
     * @throws ServiceException 主密钥未配置或长度不合法
     */
    private byte[] resolveKeyBytes()
    {
        if (StrUtil.isBlank(masterKey))
        {
            throw new ServiceException("未配置敏感值主密钥（环境变量 HOWE_SECRET_KEY），无法加解密");
        }
        byte[] keyBytes;
        try
        {
            keyBytes = Base64.getDecoder().decode(masterKey);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("敏感值主密钥不是合法的 Base64 字符串，请检查环境变量 HOWE_SECRET_KEY");
        }
        for (int length : KEY_LENGTHS)
        {
            if (keyBytes.length == length)
            {
                return keyBytes;
            }
        }
        throw new ServiceException("敏感值主密钥长度不合法，Base64 解码后必须是 16/24/32 字节");
    }
}
