package org.firstfolio.gifticon.crypto;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GifticonCryptoServiceTest {

    private static final String ENCRYPTION_KEY = Base64.getEncoder()
            .encodeToString(sequence(1));
    private static final String FINGERPRINT_KEY = Base64.getEncoder()
            .encodeToString(sequence(33));

    @Test
    void encryptsWithRandomNonceAndDecryptsOriginalCode() {
        GifticonCryptoService service = service();

        byte[] first = service.encrypt("1234-5678-ABCD");
        byte[] second = service.encrypt("1234-5678-ABCD");

        assertFalse(Arrays.equals(first, second));
        assertEquals("1234-5678-ABCD", service.decrypt(first));
        assertEquals("1234-5678-ABCD", service.decrypt(second));
    }

    @Test
    void fingerprintIgnoresCaseWhitespaceAndHyphens() {
        GifticonCryptoService service = service();

        assertEquals(
                Base64.getEncoder().encodeToString(service.fingerprint(" abcd-1234 ")),
                Base64.getEncoder().encodeToString(service.fingerprint("ABCD 1234"))
        );
        assertEquals("****1234", service.mask("ABCD-1234"));
    }

    @Test
    void rejectsMissingOrShortKeysOnlyWhenCryptoIsUsed() {
        GifticonCryptoService service = new GifticonCryptoService("", "", "v1", new SecureRandom());

        ApiException exception = assertThrows(ApiException.class, () -> service.encrypt("code"));

        assertEquals(ErrorCode.GIFTICON_CRYPTO_UNAVAILABLE, exception.getErrorCode());
    }

    private GifticonCryptoService service() {
        return new GifticonCryptoService(
                ENCRYPTION_KEY, FINGERPRINT_KEY, "v1", new SecureRandom()
        );
    }

    private static byte[] sequence(int start) {
        byte[] value = new byte[32];
        for (int index = 0; index < value.length; index++) value[index] = (byte) (start + index);
        return value;
    }
}
