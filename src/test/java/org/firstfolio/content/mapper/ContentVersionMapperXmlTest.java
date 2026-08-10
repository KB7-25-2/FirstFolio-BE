package org.firstfolio.content.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentVersionMapperXmlTest {

    private static final String RESOURCE = "mappers/content/ContentVersionMapper.xml";

    @Test
    void parsesMapperAndRegistersContentVersionStatements() throws IOException {
        Configuration configuration = new Configuration();

        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            XMLMapperBuilder builder = new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            );
            builder.parse();
        }

        assertTrue(configuration.hasMapper(ContentVersionMapper.class));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".findCurrentPublishedLesson"
        ));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".countBySubChapterIdAndVersionNo"
        ));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".insert"
        ));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".findAllBySubChapterId"
        ));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".findById"
        ));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".findByIdForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".publishDraft"
        ));
        assertTrue(configuration.hasStatement(
                ContentVersionMapper.class.getName() + ".retirePublished"
        ));
    }
}
