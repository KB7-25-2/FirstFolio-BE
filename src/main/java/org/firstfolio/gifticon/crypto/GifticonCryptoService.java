package org.firstfolio.gifticon.crypto;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Component
public class GifticonCryptoService {

    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String encryptionKeyBase64;
    private final String fingerprintKeyBase64;
    private final String keyVersion;
    private final SecureRandom secureRandom;

    public GifticonCryptoService(
            @Value("${gifticon.crypto.encryption-key:}") String encryptionKeyBase64,
            @Value("${gifticon.crypto.fingerprint-key:}") String fingerprintKeyBase64,
            @Value("${gifticon.crypto.key-version:v1}") String keyVersion
    ) {
        this(encryptionKeyBase64, fingerprintKeyBase64, keyVersion, new SecureRandom());
    }

    GifticonCryptoService(
            String encryptionKeyBase64,
            String fingerprintKeyBase64,
            String keyVersion,
            SecureRandom secureRandom
    ) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
        this.fingerprintKeyBase64 = fingerprintKeyBase64;
        this.keyVersion = keyVersion;
        this.secureRandom = secureRandom;
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw cryptoUnavailable(exception);
        }
    }

    public String decrypt(byte[] stored) {
        if (stored == null || stored.length <= NONCE_LENGTH) {
            throw cryptoUnavailable(null);
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] encrypted = new byte[stored.length - NONCE_LENGTH];
            ByteBuffer.wrap(stored).get(nonce).get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw cryptoUnavailable(exception);
        }
    }

    public String decrypt(byte[] stored, String storedKeyVersion) {
        if (storedKeyVersion == null
                || !keyVersion().equals(storedKeyVersion.trim())) {
            throw cryptoUnavailable(null);
        }
        return decrypt(stored);
    }

    public byte[] fingerprint(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintKey(), "HmacSHA256"));
            return mac.doFinal(normalize(code).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw cryptoUnavailable(exception);
        }
    }

    public String mask(String code) {
        String normalized = normalize(code);
        if (normalized.length() <= 4) {
            return "*".repeat(normalized.length());
        }
        int stars = Math.min(12, Math.max(4, normalized.length() - 4));
        return "*".repeat(stars) + normalized.substring(normalized.length() - 4);
    }

    public String keyVersion() {
        if (keyVersion == null || keyVersion.isBlank() || keyVersion.length() > 50) {
            throw cryptoUnavailable(null);
        }
        return keyVersion.trim();
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private SecretKeySpec aesKey() {
        byte[] key = decode(encryptionKeyBase64);
        if (key.length != 32) {
            throw cryptoUnavailable(null);
        }
        return new SecretKeySpec(key, "AES");
    }

    private byte[] fingerprintKey() {
        byte[] key = decode(fingerprintKeyBase64);
        if (key.length < 32) {
            throw cryptoUnavailable(null);
        }
        return key;
    }

    private byte[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw cryptoUnavailable(null);
        }
        return Base64.getDecoder().decode(encoded.trim());
    }

    private ApiException cryptoUnavailable(Throwable cause) {
        return new ApiException(
                ErrorCode.GIFTICON_CRYPTO_UNAVAILABLE,
                ErrorCode.GIFTICON_CRYPTO_UNAVAILABLE.getDefaultMessage(),
                cause
        );
    }
}
