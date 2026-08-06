package org.firstfolio.content.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentWriteRequestTest {

    @Test
    void copiesContentWhenCreatedAndRead() {
        byte[] source = new byte[]{1, 2, 3};
        ContentWriteRequest request = new ContentWriteRequest(
                "learning/sub-chapters/101/content.json",
                source,
                "application/json"
        );

        source[0] = 9;
        byte[] firstRead = request.content();
        firstRead[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, request.content());
        assertEquals("learning/sub-chapters/101/content.json", request.objectKey());
        assertEquals("application/json", request.contentType());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContentWriteRequest(" ", new byte[]{1}, "application/json")
        );
        assertThrows(
                NullPointerException.class,
                () -> new ContentWriteRequest("content.json", null, "application/json")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContentWriteRequest("content.json", new byte[0], "application/json")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContentWriteRequest("content.json", new byte[]{1}, " ")
        );
    }
}
