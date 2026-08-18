package org.firstfolio.learning.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningRoadmapMapperXmlTest {

    private static final String RESOURCE =
            "mappers/learning/LearningRoadmapMapper.xml";

    private Configuration configuration;

    @BeforeEach
    void setUp() throws IOException {
        configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }
    }

    @Test
    void parsesBulkChapterAndSubChapterQueries() {
        BoundSql chapters = boundSql("findChaptersByUserId");
        String chapterSql = normalize(chapters.getSql());
        assertTrue(chapterSql.contains(
                "FROM user_curriculum_items curriculum"
        ));
        assertTrue(chapterSql.contains("END AS progress_percent"));
        assertTrue(chapterSql.contains(
                "completed_quiz.quiz_type = 'SUB_CHAPTER'"
        ));
        assertTrue(chapterSql.contains(
                "completed_quiz.status = 'GRADED'"
        ));
        assertTrue(chapterSql.endsWith(
                "ORDER BY curriculum.display_order, curriculum.curriculum_item_id"
        ));

        BoundSql subChapters = boundSql("findSubChaptersByUserId");
        String subChapterSql = normalize(subChapters.getSql());
        assertTrue(subChapterSql.contains(
                "LEFT JOIN user_learning_progress progress"
        ));
        assertTrue(subChapterSql.contains(
                "published.status = 'PUBLISHED'"
        ));
        assertTrue(subChapterSql.contains("sub.is_active = TRUE"));
        assertTrue(subChapterSql.contains("AS quiz_completed"));
        assertTrue(subChapterSql.contains("AS active_quiz_attempt_id"));
        assertTrue(subChapterSql.contains("AS quiz_answered_count"));
        assertTrue(subChapterSql.contains("AS quiz_total_count"));
    }

    @Test
    void parsesBulkMainChapterQuizStateQuery() {
        BoundSql quizzes = boundSql("findMainChapterQuizzesByUserId");
        String sql = normalize(quizzes.getSql());

        assertTrue(sql.contains("question.usage_type = 'MAIN_CHAPTER'"));
        assertTrue(sql.contains("AS question_available"));
        assertTrue(sql.contains("AS all_sub_chapters_completed"));
        assertTrue(sql.contains("completed_quiz.quiz_type = 'SUB_CHAPTER'"));
        assertTrue(sql.contains("completed_quiz.status = 'GRADED'"));
        assertTrue(sql.contains("LEFT JOIN quiz_attempts attempt"));
        assertTrue(sql.contains("newer_attempt.attempt_no > attempt.attempt_no"));
    }

    private BoundSql boundSql(String statement) {
        String id = LearningRoadmapMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasMapper(LearningRoadmapMapper.class));
        assertTrue(configuration.hasStatement(id));
        return configuration.getMappedStatement(id)
                .getBoundSql(Map.of("userId", 11L));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
