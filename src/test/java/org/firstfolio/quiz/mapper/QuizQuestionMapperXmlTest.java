package org.firstfolio.quiz.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizQuestionMapperXmlTest {

    private static final String RESOURCE = "mappers/quiz/QuizQuestionMapper.xml";

    @Test
    void parsesMapperAndExpandsQuestionIdParameters() throws IOException {
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

        String statementId = QuizQuestionMapper.class.getName() + ".findReferencesByIds";
        assertTrue(configuration.hasMapper(QuizQuestionMapper.class));
        assertTrue(configuration.hasStatement(
                QuizQuestionMapper.class.getName() + ".findById"
        ));
        assertTrue(configuration.hasStatement(
                QuizQuestionMapper.class.getName() + ".findLatestByQuestionKeyForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                QuizQuestionMapper.class.getName() + ".countByQuestionKey"
        ));
        assertTrue(configuration.hasStatement(
                QuizQuestionMapper.class.getName() + ".insert"
        ));
        assertTrue(configuration.hasStatement(
                QuizQuestionMapper.class.getName() + ".findAllByIds"
        ));
        assertTrue(configuration.hasStatement(
                QuizQuestionMapper.class.getName()
                        + ".findLatestPublishedByMainChapterId"
        ));
        assertTrue(configuration.hasStatement(
                QuizQuestionMapper.class.getName()
                        + ".findLatestPublishedLevelTestQuestions"
        ));
        assertTrue(configuration.hasStatement(statementId));

        BoundSql boundSql = configuration.getMappedStatement(statementId)
                .getBoundSql(Map.of("questionIds", List.of(1021L, 1022L, 1023L)));
        assertEquals(3, boundSql.getParameterMappings().size());
        assertTrue(normalize(boundSql.getSql()).contains(
                "FROM quiz_questions WHERE question_id IN ( ? , ? , ? )"
        ));

        BoundSql lockSql = configuration.getMappedStatement(
                        QuizQuestionMapper.class.getName()
                                + ".findLatestByQuestionKeyForUpdate"
                )
                .getBoundSql(Map.of("questionKey", "deposit-basic-001"));
        assertTrue(normalize(lockSql.getSql()).contains(
                "WHERE question_key = ? ORDER BY version_no DESC LIMIT 1 FOR UPDATE"
        ));

        BoundSql mainQuestionSql = configuration.getMappedStatement(
                        QuizQuestionMapper.class.getName()
                                + ".findLatestPublishedByMainChapterId"
                )
                .getBoundSql(Map.of("mainChapterId", 10L));
        String normalizedMainQuestionSql = normalize(mainQuestionSql.getSql());
        assertTrue(normalizedMainQuestionSql.contains(
                "question.usage_type = 'MAIN_CHAPTER'"
        ));
        assertTrue(normalizedMainQuestionSql.contains(
                "newer.version_no > question.version_no"
        ));
        assertTrue(normalizedMainQuestionSql.endsWith(
                "ORDER BY question.display_order, question.question_id"
        ));

        BoundSql levelTestSql = configuration.getMappedStatement(
                        QuizQuestionMapper.class.getName()
                                + ".findLatestPublishedLevelTestQuestions"
                )
                .getBoundSql(Map.of());
        String normalizedLevelTestSql = normalize(levelTestSql.getSql());
        assertTrue(normalizedLevelTestSql.contains(
                "question.usage_type = 'LEVEL_TEST'"
        ));
        assertTrue(normalizedLevelTestSql.contains(
                "chapter.chapter_type = 'ASSET' AND chapter.is_active = TRUE"
        ));
        assertTrue(normalizedLevelTestSql.contains(
                "newer.status IN ('PUBLISHED', 'RETIRED')"
        ));
        assertTrue(normalizedLevelTestSql.endsWith(
                "ORDER BY chapter.display_order, question.display_order, question.question_id"
        ));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
