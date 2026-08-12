package org.firstfolio.learning.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningContinueMapperXmlTest {

    private static final String RESOURCE =
            "mappers/learning/LearningContinueMapper.xml";

    @Test
    void parsesLatestInProgressQuery() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }

        String statement = LearningContinueMapper.class.getName()
                + ".findLatestInProgress";
        assertTrue(configuration.hasMapper(LearningContinueMapper.class));
        assertTrue(configuration.hasStatement(statement));

        BoundSql boundSql = configuration.getMappedStatement(statement)
                .getBoundSql(Map.of("userId", 11L));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("progress.status = 'IN_PROGRESS'"));
        assertTrue(sql.contains("curriculum.status = 'ACTIVE'"));
        assertTrue(sql.contains("curriculum.completed_at IS NULL"));
        assertTrue(sql.endsWith(
                "ORDER BY progress.updated_at DESC, progress.progress_id DESC LIMIT 1"
        ));
    }
}
