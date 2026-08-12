package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCurriculumMapperXmlTest {

    private static final String RESOURCE =
            "mappers/curriculum/UserCurriculumMapper.xml";

    @Test
    void parsesActiveCurriculumQuery() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }

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

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
