package org.firstfolio.curriculum.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.dto.request.MainChapterCreateRequest;
import org.firstfolio.curriculum.dto.request.MainChapterPatchRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterRequestDtoTest {

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    @Test
    void distinguishesMissingPatchFieldFromExplicitNull() throws Exception {
        MainChapterPatchRequest request = objectMapper.readValue(
                "{\"description\":null}",
                MainChapterPatchRequest.class
        );

        assertTrue(request.descriptionProvided());
        assertNull(request.description());
        assertFalse(request.titleProvided());
        assertFalse(request.activeProvided());
    }

    @Test
    void rejectsLegacyActiveCreateField() {
        assertThrows(
                Exception.class,
                () -> objectMapper.readValue(
                        """
                        {
                          "chapter_type": "ASSET",
                          "asset_type": "BOND",
                          "title": "채권",
                          "display_order": 2,
                          "is_required": false,
                          "active": true
                        }
                        """,
                        MainChapterCreateRequest.class
                )
        );
    }
}
