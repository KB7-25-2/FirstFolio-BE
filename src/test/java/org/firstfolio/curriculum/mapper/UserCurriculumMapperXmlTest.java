package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCurriculumMapperXmlTest {

    private static final String RESOURCE =
            "mappers/curriculum/UserCurriculumMapper.xml";

    @Test
    void parsesActiveCurriculumQuery() throws IOException {
        Configuration configuration = configuration();

        String statementId = UserCurriculumMapper.class.getName()
                + ".findActiveByUserId";
        assertTrue(configuration.hasMapper(UserCurriculumMapper.class));
        assertTrue(configuration.hasStatement(statementId));

        BoundSql sql = configuration.getMappedStatement(statementId)
                .getBoundSql(Map.of("userId", 11L));
        String normalized = normalize(sql.getSql());
        assertTrue(normalized.contains(
                "WHERE user_id = ? AND status = 'ACTIVE'"
        ));
        assertTrue(normalized.endsWith(
                "ORDER BY display_order, curriculum_item_id"
        ));
    }

    @Test
    void parsesUserLockAndBatchInsert() throws IOException {
        Configuration configuration = configuration();
        String namespace = UserCurriculumMapper.class.getName();

        BoundSql lockSql = configuration.getMappedStatement(
                namespace + ".findUserIdForUpdate"
        ).getBoundSql(Map.of("userId", 11L));
        assertTrue(normalize(lockSql.getSql()).contains(
                "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE"
        ));

        CurriculumDraftItem foundation = new CurriculumDraftItem(
                1L,
                "포트폴리오 기초",
                CurriculumSourceType.FOUNDATION,
                1,
                false
        );
        BoundSql insertSql = configuration.getMappedStatement(
                namespace + ".insertAll"
        ).getBoundSql(Map.of(
                "userId", 11L,
                "items", List.of(foundation),
                "confirmedAt", LocalDateTime.of(2026, 8, 12, 1, 2)
        ));
        String normalized = normalize(insertSql.getSql());
        assertTrue(normalized.startsWith(
                "INSERT INTO user_curriculum_items"
        ));
        assertTrue(normalized.contains(
                "VALUES ( ?, ?, ?, ?, 'ACTIVE', ?, NULL )"
        ));
    }

    @Test
    void parsesCurriculumOverviewWithProgressCalculation() throws IOException {
        Configuration configuration = configuration();
        BoundSql sql = configuration.getMappedStatement(
                UserCurriculumMapper.class.getName()
                        + ".findOverviewByUserId"
        ).getBoundSql(Map.of("userId", 11L));

        String normalized = normalize(sql.getSql());
        assertTrue(normalized.contains(
                "FROM user_curriculum_items curriculum"
        ));
        assertTrue(normalized.contains(
                "progress.status = 'COMPLETED'"
        ));
        assertTrue(normalized.contains(
                "END AS progress_percent"
        ));
        assertTrue(normalized.endsWith(
                "ORDER BY curriculum.display_order, curriculum.curriculum_item_id"
        ));
    }

    @Test
    void parsesSoftRemovalAndUpsertForCurriculumUpdate() throws IOException {
        Configuration configuration = configuration();
        String namespace = UserCurriculumMapper.class.getName();

        BoundSql removeSql = configuration.getMappedStatement(
                namespace + ".markActiveAsRemoved"
        ).getBoundSql(Map.of("userId", 11L));
        assertTrue(normalize(removeSql.getSql()).contains(
                "SET status = 'REMOVED' WHERE user_id = ? AND status = 'ACTIVE'"
        ));

        CurriculumDraftItem foundation = new CurriculumDraftItem(
                1L,
                "포트폴리오 기초",
                CurriculumSourceType.FOUNDATION,
                1,
                false
        );
        BoundSql upsertSql = configuration.getMappedStatement(
                namespace + ".upsertAll"
        ).getBoundSql(Map.of(
                "userId", 11L,
                "items", List.of(foundation),
                "confirmedAt", LocalDateTime.of(2026, 8, 14, 1, 2)
        ));
        String normalized = normalize(upsertSql.getSql());
        assertTrue(normalized.startsWith(
                "INSERT INTO user_curriculum_items"
        ));
        assertTrue(normalized.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(normalized.contains("status = 'ACTIVE'"));
        assertFalse(normalized.contains("completed_at = VALUES"));
    }

    private Configuration configuration() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
