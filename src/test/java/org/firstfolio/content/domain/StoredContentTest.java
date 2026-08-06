package org.firstfolio.content.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredContentTest {

    @Test
    void copiesContentWhenCreatedAndRead() {
        byte[] source = new byte[]{1, 2, 3};
        StoredContent storedContent = new StoredContent(source, "application/json");

        source[0] = 9;
        byte[] firstRead = storedContent.content();
        firstRead[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, storedContent.content());
        assertEquals("application/json", storedContent.contentType());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(
                NullPointerException.class,
                () -> new StoredContent(null, "application/json")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoredContent(new byte[]{1}, " ")
        );
    }
}
