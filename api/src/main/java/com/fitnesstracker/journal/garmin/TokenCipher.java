package com.fitnesstracker.journal.garmin;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts Garmin tokens at rest with AES-256-GCM. The key comes from GARMIN_TOKEN_KEY
 * (32 random bytes, base64) and is never stored in the database. The account id is bound in as
 * associated data, so a token copied onto another account won't decrypt.
 */
@Component
public class TokenCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${app.garmin.token-key:}") String base64Key) {
        this.key = parse(base64Key);
    }

    static SecretKey parse(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("GARMIN_TOKEN_KEY must be 32 bytes, base64-encoded");
        }
        return new SecretKeySpec(raw, "AES");
    }

    /** False when no key is set; Garmin linking is then turned off. */
    public boolean available() {
        return key != null;
    }

    public String encrypt(String userId, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = cipher(Cipher.ENCRYPT_MODE, iv, userId);
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't encrypt the Garmin token", e);
        }
    }

    public String decrypt(String userId, String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown token format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOf(all, IV_BYTES);
            Cipher c = cipher(Cipher.DECRYPT_MODE, iv, userId);
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't decrypt the Garmin token", e);
        }
    }

    private Cipher cipher(int mode, byte[] iv, String userId) throws GeneralSecurityException {
        if (key == null) {
            throw new IllegalStateException("GARMIN_TOKEN_KEY is not set");
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        c.updateAAD(userId.getBytes(StandardCharsets.UTF_8));
        return c;
    }
}
