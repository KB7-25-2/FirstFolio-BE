package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterMapperXmlTest {

    @Test
    void parsesChapterMapperXmlAndRegistersStatements() throws IOException {
        Configuration configuration = new Configuration();

        parse(configuration, "mappers/curriculum/MainChapterMapper.xml");
        parse(configuration, "mappers/curriculum/SubChapterMapper.xml");
        parse(configuration, "mappers/curriculum/AdminAuditLogMapper.xml");

        assertTrue(configuration.hasMapper(MainChapterMapper.class));
        assertTrue(configuration.hasMapper(SubChapterMapper.class));
        assertTrue(configuration.hasMapper(AdminAuditLogMapper.class));
        assertTrue(configuration.hasStatement(
                MainChapterMapper.class.getName() + ".insert"
        ));
        assertTrue(configuration.hasStatement(
                SubChapterMapper.class.getName() + ".updateMetadata"
        ));
        assertTrue(configuration.hasStatement(
                SubChapterMapper.class.getName() + ".findByIdForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                SubChapterMapper.class.getName() + ".updateCurrentContentVersion"
        ));
        assertTrue(configuration.hasStatement(
                AdminAuditLogMapper.class.getName() + ".insert"
        ));
    }

    private void parse(Configuration configuration, String resource)
            throws IOException {
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder builder = new XMLMapperBuilder(
                    input,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            );
            builder.parse();
        }
    }
}
