package org.firstfolio.learning.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.firstfolio.learning.domain.UserCurriculumItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainChapterLearningMapperXmlTest {

    private static final String RESOURCE =
            "mappers/learning/MainChapterLearningMapper.xml";

    @Test
    void parsesEligibilityAndCompletionStatements() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }

        assertTrue(configuration.hasMapper(MainChapterLearningMapper.class));
        assertTrue(configuration.hasStatement(id(
                "findActiveCurriculumItemForUpdate"
        )));
        assertTrue(configuration.hasStatement(id("countActiveSubChapters")));
        assertTrue(configuration.hasStatement(id(
                "countIncompleteActiveSubChapters"
        )));
        assertTrue(configuration.hasStatement(id(
                "completeCurriculumItemIfIncomplete"
        )));

        BoundSql lockSql = configuration.getMappedStatement(id(
                        "findActiveCurriculumItemForUpdate"
                ))
                .getBoundSql(Map.of("userId", 11L, "mainChapterId", 10L));
        assertTrue(normalize(lockSql.getSql()).contains(
                "AND status = 'ACTIVE' FOR UPDATE"
        ));

        BoundSql incompleteSql = configuration.getMappedStatement(id(
                        "countIncompleteActiveSubChapters"
                ))
                .getBoundSql(Map.of("userId", 11L, "mainChapterId", 10L));
        assertTrue(normalize(incompleteSql.getSql()).contains(
                "progress.status = 'COMPLETED'"
        ));

        BoundSql completeSql = configuration.getMappedStatement(id(
                        "completeCurriculumItemIfIncomplete"
                ))
                .getBoundSql(Map.of(
                        "curriculumItemId", 100L,
                        "completedAt", LocalDateTime.now()
                ));
        assertTrue(normalize(completeSql.getSql()).contains(
                "AND status = 'ACTIVE' AND completed_at IS NULL"
        ));
    }

    private String id(String statement) {
        return MainChapterLearningMapper.class.getName() + "." + statement;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
