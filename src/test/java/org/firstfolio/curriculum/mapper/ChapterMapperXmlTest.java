package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;

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
                SubChapterMapper.class.getName() + ".findPublicByMainChapterId"
        ));
        assertTrue(configuration.hasStatement(
                SubChapterMapper.class.getName() + ".updateCurrentContentVersion"
        ));
        String clearStatement = SubChapterMapper.class.getName()
                + ".clearCurrentContentVersion";
        assertTrue(configuration.hasStatement(clearStatement));
        BoundSql clearSql = configuration.getMappedStatement(clearStatement)
                .getBoundSql(Map.of(
                        "subChapterId", 103L,
                        "expectedContentVersionId", 301L,
                        "updatedAt", LocalDateTime.of(2026, 8, 14, 6, 0)
                ));
        String normalizedClearSql = clearSql.getSql()
                .replaceAll("\\s+", " ")
                .trim();
        assertTrue(normalizedClearSql.contains(
                "SET current_content_version_id = NULL"
        ));
        assertTrue(normalizedClearSql.endsWith(
                "WHERE sub_chapter_id = ? AND current_content_version_id = ?"
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
