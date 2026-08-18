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
        assertTrue(configuration.hasStatement(id("findUserIdForUpdate")));
        assertTrue(configuration.hasStatement(id("findByIdForUpdate")));
        assertTrue(configuration.hasStatement(id("findLevelTestByUserId")));
        assertTrue(configuration.hasStatement(id(
                "findLevelTestByUserIdForUpdate"
        )));
        assertTrue(configuration.hasStatement(id(
                "findInProgressByUserIdAndSubChapterIdForUpdate"
        )));
        assertTrue(configuration.hasStatement(id(
                "findSubChapterQuizProgress"
        )));
        assertTrue(configuration.hasStatement(id(
                "findMaxAttemptNoByUserIdAndSubChapterId"
        )));
        assertTrue(configuration.hasStatement(id(
                "findInProgressByUserIdAndMainChapterIdForUpdate"
        )));
        assertTrue(configuration.hasStatement(id(
                "findMaxAttemptNoByUserIdAndMainChapterId"
        )));
        assertTrue(configuration.hasStatement(id("findAnswersByAttemptId")));
        assertTrue(configuration.hasStatement(id(
                "findAnswersByAttemptIdForUpdate"
        )));
        assertTrue(configuration.hasStatement(id(
                "findAnswerByAttemptIdAndQuestionIdForUpdate"
        )));
        assertTrue(configuration.hasStatement(id("countAnsweredByAttemptId")));
        assertTrue(configuration.hasStatement(id("countCorrectByAttemptId")));
        assertTrue(configuration.hasStatement(id("insertAttempt")));
        assertTrue(configuration.hasStatement(id("insertAnswer")));
        assertTrue(configuration.hasStatement(id("saveLevelTestAnswer")));
        assertTrue(configuration.hasStatement(id("gradeLevelTestAnswer")));
        assertTrue(configuration.hasStatement(id("gradeAnswerIfUnanswered")));
        assertTrue(configuration.hasStatement(id("completeAttemptIfInProgress")));

        BoundSql userLockSql = configuration.getMappedStatement(id(
                        "findUserIdForUpdate"
                ))
                .getBoundSql(Map.of("userId", 11L));
        assertTrue(normalize(userLockSql.getSql()).contains(
                "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE"
        ));

        BoundSql lockSql = configuration.getMappedStatement(id(
                        "findInProgressByUserIdAndSubChapterIdForUpdate"
                ))
                .getBoundSql(Map.of("userId", 11L, "subChapterId", 101L));
        assertTrue(normalize(lockSql.getSql()).contains(
                "AND status = 'IN_PROGRESS' ORDER BY attempt_no DESC LIMIT 1 FOR UPDATE"
        ));

        BoundSql progressSql = configuration.getMappedStatement(id(
                        "findSubChapterQuizProgress"
                ))
                .getBoundSql(Map.of("userId", 11L, "subChapterId", 101L));
        assertTrue(normalize(progressSql.getSql()).contains(
                "completed_attempt.status = 'GRADED'"
        ));
        assertTrue(normalize(progressSql.getSql()).contains(
                "active_attempt.status = 'IN_PROGRESS'"
        ) || normalize(progressSql.getSql()).contains(
                "AND status = 'IN_PROGRESS' ORDER BY attempt_no DESC LIMIT 1"
        ));

        BoundSql levelTestLockSql = configuration.getMappedStatement(id(
                        "findLevelTestByUserIdForUpdate"
                ))
                .getBoundSql(Map.of("userId", 11L));
        assertTrue(normalize(levelTestLockSql.getSql()).contains(
                "WHERE user_id = ? AND quiz_type = 'LEVEL_TEST' LIMIT 1 FOR UPDATE"
        ));

        BoundSql mainLockSql = configuration.getMappedStatement(id(
                        "findInProgressByUserIdAndMainChapterIdForUpdate"
                ))
                .getBoundSql(Map.of("userId", 11L, "mainChapterId", 10L));
        assertTrue(normalize(mainLockSql.getSql()).contains(
                "quiz_type = 'MAIN_CHAPTER' AND main_chapter_id = ?"
        ));

        BoundSql answersSql = configuration.getMappedStatement(
                        id("findAnswersByAttemptId")
                )
                .getBoundSql(Map.of("attemptId", 3001L));
        assertTrue(normalize(answersSql.getSql()).contains(
                "WHERE attempt_id = ? ORDER BY display_order ASC"
        ));

        BoundSql answersLockSql = configuration.getMappedStatement(
                        id("findAnswersByAttemptIdForUpdate")
                )
                .getBoundSql(Map.of("attemptId", 3001L));
        assertTrue(normalize(answersLockSql.getSql()).contains(
                "WHERE attempt_id = ? ORDER BY display_order ASC FOR UPDATE"
        ));

        BoundSql answerLockSql = configuration.getMappedStatement(id(
                        "findAnswerByAttemptIdAndQuestionIdForUpdate"
                ))
                .getBoundSql(Map.of(
                        "attemptId", 3001L,
                        "questionId", 1001L
                ));
        assertTrue(normalize(answerLockSql.getSql()).contains(
                "WHERE attempt_id = ? AND question_id = ? FOR UPDATE"
        ));

        BoundSql gradeSql = configuration.getMappedStatement(
                        id("gradeAnswerIfUnanswered")
                )
                .getBoundSql(new org.firstfolio.quiz.domain.QuizAnswer());
        assertTrue(normalize(gradeSql.getSql()).contains(
                "WHERE quiz_answer_id = ? AND user_answer_json IS NULL"
        ));

        BoundSql saveLevelTestAnswerSql = configuration.getMappedStatement(
                        id("saveLevelTestAnswer")
                )
                .getBoundSql(new org.firstfolio.quiz.domain.QuizAnswer());
        assertTrue(normalize(saveLevelTestAnswerSql.getSql()).contains(
                "SET user_answer_json = ?, is_correct = NULL, answered_at = ?"
        ));
        assertTrue(normalize(saveLevelTestAnswerSql.getSql()).contains(
                "WHERE quiz_answer_id = ? AND is_correct IS NULL"
        ));

        BoundSql gradeLevelTestAnswerSql = configuration.getMappedStatement(
                        id("gradeLevelTestAnswer")
                )
                .getBoundSql(new org.firstfolio.quiz.domain.QuizAnswer());
        assertTrue(normalize(gradeLevelTestAnswerSql.getSql()).contains(
                "SET is_correct = ? WHERE quiz_answer_id = ?"
        ));
        assertTrue(normalize(gradeLevelTestAnswerSql.getSql()).contains(
                "user_answer_json IS NOT NULL AND is_correct IS NULL"
        ));

        BoundSql completeSql = configuration.getMappedStatement(
                        id("completeAttemptIfInProgress")
                )
                .getBoundSql(new org.firstfolio.quiz.domain.QuizAttempt());
        assertTrue(normalize(completeSql.getSql()).contains(
                "reward_policy_id = ?, point_transaction_id = ?, submitted_at = ?"
        ));
        assertTrue(normalize(completeSql.getSql()).contains(
                "WHERE attempt_id = ? AND status = 'IN_PROGRESS'"
        ));
    }

    private String id(String statement) {
        return QuizAttemptMapper.class.getName() + "." + statement;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
