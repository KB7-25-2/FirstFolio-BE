package org.firstfolio.user.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMapperXmlTest {

    private static final String RESOURCE = "mappers/user/UserMapper.xml";

    @Test
    void onboardingStepUsesOneGradedLevelTestAttempt() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }

        String statementId = UserMapper.class.getName() + ".findOnboardingStep";
        BoundSql sql = configuration.getMappedStatement(statementId)
                .getBoundSql(Map.of("userId", 11L));
        String normalized = normalize(sql.getSql());

        assertTrue(normalized.contains(
                "attempt.quiz_type = 'LEVEL_TEST' AND attempt.status = 'GRADED'"
        ));
        assertFalse(normalized.contains("attempt.main_chapter_id"));
        assertFalse(normalized.contains("chapter.chapter_type = 'ASSET'"));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
