package com.budgettracker.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for Teller access tokens at rest.
 * Each encrypt() call uses a fresh random 12-byte IV.
 * Ciphertext format: base64(iv) + ":" + base64(ciphertext+tag).
 */
@Component
public class TokenEncryptionService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";

    private final SecretKey key;

    public TokenEncryptionService(@Value("${app.teller.token-encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "TELLER_TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes (256-bit AES key). "
                    + "Generate one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed.", e);
        }
    }

    public String decrypt(String stored) {
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid ciphertext format.");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed.", e);
        }
    }
}
