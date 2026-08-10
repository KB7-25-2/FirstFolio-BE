package org.firstfolio.quiz.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizAttemptMapperXmlTest {

    private static final String RESOURCE = "mappers/quiz/QuizAttemptMapper.xml";

    @Test
    void parsesAttemptAndAnswerStatements() throws IOException {
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

        assertTrue(configuration.hasMapper(QuizAttemptMapper.class));
        assertTrue(configuration.hasStatement(id(
                "findInProgressByUserIdAndSubChapterIdForUpdate"
        )));
        assertTrue(configuration.hasStatement(id(
                "findMaxAttemptNoByUserIdAndSubChapterId"
        )));
        assertTrue(configuration.hasStatement(id("findAnswersByAttemptId")));
        assertTrue(configuration.hasStatement(id("insertAttempt")));
        assertTrue(configuration.hasStatement(id("insertAnswer")));

        BoundSql lockSql = configuration.getMappedStatement(id(
                        "findInProgressByUserIdAndSubChapterIdForUpdate"
                ))
                .getBoundSql(Map.of("userId", 11L, "subChapterId", 101L));
        assertTrue(normalize(lockSql.getSql()).contains(
                "AND status = 'IN_PROGRESS' ORDER BY attempt_no DESC LIMIT 1 FOR UPDATE"
        ));

        BoundSql answersSql = configuration.getMappedStatement(
                        id("findAnswersByAttemptId")
                )
                .getBoundSql(Map.of("attemptId", 3001L));
        assertTrue(normalize(answersSql.getSql()).contains(
                "WHERE attempt_id = ? ORDER BY display_order ASC"
        ));
    }

    private String id(String statement) {
        return QuizAttemptMapper.class.getName() + "." + statement;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
