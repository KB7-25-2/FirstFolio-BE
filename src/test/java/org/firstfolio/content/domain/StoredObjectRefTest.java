package org.firstfolio.content.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredObjectRefTest {

    @Test
    void keepsObjectKeyAndVersionId() {
        StoredObjectRef reference = new StoredObjectRef(
                "learning/sub-chapters/101/content.json",
                "version-1"
        );

        assertEquals("learning/sub-chapters/101/content.json", reference.objectKey());
        assertEquals("version-1", reference.versionId());
    }

    @Test
    void rejectsBlankObjectKeyOrVersionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoredObjectRef(" ", "version-1")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoredObjectRef("content.json", " ")
        );
    }
}
