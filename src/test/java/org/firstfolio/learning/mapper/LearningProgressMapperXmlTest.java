package org.firstfolio.learning.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningProgressMapperXmlTest {

    @Test
    void parsesMapperAndRegistersProgressStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mappers/learning/LearningProgressMapper.xml";

        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }

        assertTrue(configuration.hasMapper(LearningProgressMapper.class));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName()
                        + ".findByUserIdAndSubChapterId"
        ));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName() + ".insertIfAbsent"
        ));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName()
                        + ".findByUserIdAndSubChapterIdForUpdate"
        ));
        String accessStatement = LearningProgressMapper.class.getName()
                + ".countIncompletePreviousSubChapters";
        assertTrue(configuration.hasStatement(accessStatement));
        String accessSql = configuration.getMappedStatement(accessStatement)
                .getBoundSql(Map.of(
                        "userId", 11L,
                        "mainChapterId", 8L,
                        "displayOrder", 2,
                        "subChapterId", 102L
                ))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
        assertTrue(accessSql.contains("previous_sub.display_order <"));
        assertTrue(accessSql.contains(
                "completed_quiz.quiz_type = 'SUB_CHAPTER'"
        ));
        assertTrue(accessSql.contains("completed_quiz.status = 'GRADED'"));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName() + ".updateProgress"
        ));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName() + ".insertEvent"
        ));
    }
}
