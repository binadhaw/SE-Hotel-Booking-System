package com.Reservation.Hotel.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for personal data stored in the database (proposal NFR: "payments and personal
 * data are encrypted"). Every value gets a fresh random 96-bit IV; GCM also detects tampering.
 * Stored format: {@code enc:v1:<base64(iv + ciphertext + tag)>}. Values without the prefix are treated as
 * legacy plain text, so existing rows keep working until they are migrated.
 */
public final class FieldEncryptor {

    public static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKey key;

    private FieldEncryptor() {}

    /** Called once at start-up with a Base64-encoded 32-byte key. */
    public static void init(String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 32) throw new IllegalArgumentException("app.crypto.key must be 32 bytes (Base64), got " + raw.length);
        key = new SecretKeySpec(raw, "AES");
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plain) {
        if (plain == null || isEncrypted(plain)) return plain;
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a field", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) return stored;   // legacy plain text
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a field - is app.crypto.key the key the data was written with?", e);
        }
    }

    private static SecretKey requireKey() {
        if (key == null) throw new IllegalStateException("FieldEncryptor not initialised (app.crypto.key)");
        return key;
    }
}
