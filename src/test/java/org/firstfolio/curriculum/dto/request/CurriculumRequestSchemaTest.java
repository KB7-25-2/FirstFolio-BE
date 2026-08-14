package org.firstfolio.curriculum.dto.request;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumRequestSchemaTest {

    @Test
    void exposesSnakeCasePropertyNamesInSwaggerSchemas() {
        assertSnakeCaseProperty(CurriculumDraftEditRequest.class);
        assertSnakeCaseProperty(CurriculumConfirmRequest.class);
        assertSnakeCaseProperty(CurriculumUpdateRequest.class);
    }

    private void assertSnakeCaseProperty(Class<?> requestType) {
        Map<String, Schema> schemas = ModelConverters.getInstance()
                .read(requestType);
        Schema<?> requestSchema = schemas.get(requestType.getSimpleName());

        assertNotNull(requestSchema);
        assertTrue(requestSchema.getProperties().containsKey("main_chapter_ids"));
        assertFalse(requestSchema.getProperties().containsKey("mainChapterIds"));
    }
}
