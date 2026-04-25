package com.runclub.api.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * AES-GCM encryption for sensitive at-rest values (currently Strava tokens).
 *
 * Format on disk: "v1:<base64(iv || ciphertext || tag)>"
 *
 * If no key is configured the cipher is a passthrough — convenient for local dev
 * but logged loudly so it isn't missed in production.
 */
@Component
public class TokenCipher {
    private static final Logger logger = Logger.getLogger(TokenCipher.class.getName());
    private static final String VERSION_PREFIX = "v1:";
    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    @Value("${runclub.token-encryption-key:}")
    private String configuredKey;

    private SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    void init() {
        if (configuredKey == null || configuredKey.isBlank()) {
            logger.warning("runclub.token-encryption-key not set — Strava tokens will NOT be encrypted at rest. " +
                "Set RUNCLUB_TOKEN_ENCRYPTION_KEY (32-byte base64) before going to production.");
            return;
        }
        byte[] key = Base64.getDecoder().decode(configuredKey);
        if (key.length != 32) {
            throw new IllegalStateException("runclub.token-encryption-key must decode to 32 bytes (got " + key.length + ")");
        }
        this.keySpec = new SecretKeySpec(key, AES);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (keySpec == null) return plaintext;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (keySpec == null) return stored;
        if (!stored.startsWith(VERSION_PREFIX)) {
            // Legacy plaintext value written before encryption was enabled.
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Token decryption failed", e);
        }
    }
}
