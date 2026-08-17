package com.howe.ai.application;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

/** AI 密钥专用 AES-GCM 加密，主密钥缺失时拒绝运行。 */
public final class SecretCipher {
    private static final int IV_LENGTH = 12;
    private final Supplier<String> masterKey;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(Supplier<String> masterKey) {
        this.masterKey = masterKey;
        key();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("密钥不能为空");
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] data = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + data.length).put(iv).put(data).array());
        } catch (Exception e) {
            throw new IllegalStateException("密钥加密失败", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, IV_LENGTH));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("密钥解密失败", e);
        }
    }

    public String mask(String encrypted) {
        return encrypted == null || encrypted.isBlank() ? "" : "******" + encrypted.substring(Math.max(0, encrypted.length() - 4));
    }

    private SecretKeySpec key() {
        String value = masterKey == null ? null : masterKey.get();
        if (value == null || value.isBlank()) throw new IllegalStateException("AI_MASTER_KEY 未配置");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) throw new IllegalStateException("AI_MASTER_KEY 长度必须为 16/24/32 字节");
        return new SecretKeySpec(bytes, "AES");
    }
}
