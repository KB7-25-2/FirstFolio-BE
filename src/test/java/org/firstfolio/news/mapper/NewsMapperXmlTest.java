package org.firstfolio.news.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsMapperXmlTest {

    private static final String RESOURCE = "mappers/news/NewsMapper.xml";

    @Test
    void parsesUpdateAndDeleteStatements() throws IOException {
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

        assertTrue(configuration.hasMapper(NewsMapper.class));
        assertTrue(configuration.hasStatement(NewsMapper.class.getName() + ".findById"));
        assertTrue(configuration.hasStatement(NewsMapper.class.getName() + ".update"));
        assertTrue(configuration.hasStatement(NewsMapper.class.getName() + ".deleteById"));

        BoundSql updateSql = configuration.getMappedStatement(
                        NewsMapper.class.getName() + ".update"
                )
                .getBoundSql(new Object());
        String normalizedUpdate = normalize(updateSql.getSql());
        assertTrue(normalizedUpdate.contains("UPDATE news_articles"));
        assertTrue(normalizedUpdate.contains("WHERE financial_news_id = ?"));

        BoundSql deleteSql = configuration.getMappedStatement(
                        NewsMapper.class.getName() + ".deleteById"
                )
                .getBoundSql(1L);
        String normalizedDelete = normalize(deleteSql.getSql());
        assertTrue(normalizedDelete.contains("DELETE FROM news_articles"));
        assertTrue(normalizedDelete.contains("WHERE financial_news_id = ?"));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
