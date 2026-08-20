package org.firstfolio.newsletter.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterGenerationType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsletterMapperXmlTest {

    private static final String RESOURCE = "mappers/newsletter/NewsletterMapper.xml";

    @Test
    void parsesAllStatements() throws IOException {
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

        assertTrue(configuration.hasMapper(NewsletterMapper.class));
        assertTrue(configuration.hasStatement(
                NewsletterMapper.class.getName() + ".findById"
        ));
        assertTrue(configuration.hasStatement(
                NewsletterMapper.class.getName() + ".findByIdForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                NewsletterMapper.class.getName() + ".findByStatus"
        ));
        assertTrue(configuration.hasStatement(
                NewsletterMapper.class.getName() + ".findByWeekStartDate"
        ));
        assertTrue(configuration.hasStatement(
                NewsletterMapper.class.getName() + ".publishReview"
        ));
        assertTrue(configuration.hasStatement(
                NewsletterMapper.class.getName() + ".retirePublished"
        ));
        assertTrue(configuration.hasStatement(
                NewsletterMapper.class.getName() + ".insert"
        ));

        Map<String, Object> publishParams = new HashMap<>();
        publishParams.put("newsletterId", 1L);
        publishParams.put("publishedAt", LocalDateTime.now());
        BoundSql publishSql = configuration.getMappedStatement(
                        NewsletterMapper.class.getName() + ".publishReview"
                )
                .getBoundSql(publishParams);
        String normalizedPublish = normalize(publishSql.getSql());
        assertTrue(normalizedPublish.contains("UPDATE newsletters"));
        assertTrue(normalizedPublish.contains("status = 'PUBLISHED'"));
        assertTrue(normalizedPublish.contains("status = 'REVIEW'"));

        BoundSql retireSql = configuration.getMappedStatement(
                        NewsletterMapper.class.getName() + ".retirePublished"
                )
                .getBoundSql(1L);
        String normalizedRetire = normalize(retireSql.getSql());
        assertTrue(normalizedRetire.contains("UPDATE newsletters"));
        assertTrue(normalizedRetire.contains("status = 'RETIRED'"));
        assertTrue(normalizedRetire.contains("status = 'PUBLISHED'"));

        BoundSql insertSql = configuration.getMappedStatement(
                        NewsletterMapper.class.getName() + ".insert"
                )
                .getBoundSql(sampleNewsletter());
        String normalizedInsert = normalize(insertSql.getSql());
        assertTrue(normalizedInsert.contains("INSERT INTO newsletters"));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private Newsletter sampleNewsletter() {
        return Newsletter.review(
                LocalDate.of(2026, 8, 17),
                "역대 최대 흑자 속에서도, 돈은 안전자산으로",
                "[]",
                "[]",
                "[]",
                NewsletterGenerationType.AI,
                1L,
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );
    }
}
