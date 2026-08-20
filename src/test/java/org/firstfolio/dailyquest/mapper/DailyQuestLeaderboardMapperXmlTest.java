package org.firstfolio.dailyquest.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyQuestLeaderboardMapperXmlTest {

    private static final String RESOURCE =
            "mappers/dailyquest/DailyQuestLeaderboardMapper.xml";

    @Test
    void ranksCompletedActiveUsersAndAppliesCompositeCursor() throws IOException {
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

        assertTrue(configuration.hasMapper(
                DailyQuestLeaderboardMapper.class
        ));
        assertTrue(configuration.hasStatement(
                DailyQuestLeaderboardMapper.class.getName()
                        + ".findTodayPage"
        ));
        assertTrue(configuration.hasStatement(
                DailyQuestLeaderboardMapper.class.getName()
                        + ".findTodayEntry"
        ));

        BoundSql pageSql = configuration.getMappedStatement(
                        DailyQuestLeaderboardMapper.class.getName()
                                + ".findTodayPage"
                )
                .getBoundSql(Map.of(
                        "questDate", LocalDate.of(2026, 8, 20),
                        "cursorScore", 4,
                        "cursorCompletedAt",
                        java.time.LocalDateTime.of(2026, 8, 20, 5, 0),
                        "cursorUserId", 12L,
                        "limit", 21
                ));
        String normalizedPageSql = normalize(pageSql.getSql());

        assertTrue(normalizedPageSql.contains(
                "RANK() OVER ( ORDER BY quest.score DESC ) AS rank_no"
        ));
        assertTrue(normalizedPageSql.contains(
                "quest.quest_date = ? AND quest.status = 'COMPLETED' "
                        + "AND quest.completed_at IS NOT NULL"
        ));
        assertTrue(normalizedPageSql.contains(
                "account.role_code = 'USER' AND account.status = 'ACTIVE'"
        ));
        assertTrue(normalizedPageSql.contains(
                "WHERE score < ? OR ( score = ? AND completed_at > ? ) "
                        + "OR ( score = ? AND completed_at = ? "
                        + "AND user_id > ? )"
        ));
        assertTrue(normalizedPageSql.endsWith(
                "ORDER BY score DESC, completed_at ASC, user_id ASC LIMIT ?"
        ));

        BoundSql myRankSql = configuration.getMappedStatement(
                        DailyQuestLeaderboardMapper.class.getName()
                                + ".findTodayEntry"
                )
                .getBoundSql(Map.of(
                        "questDate", LocalDate.of(2026, 8, 20),
                        "userId", 10L
                ));
        assertTrue(normalize(myRankSql.getSql()).endsWith(
                "FROM ranked WHERE user_id = ?"
        ));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
