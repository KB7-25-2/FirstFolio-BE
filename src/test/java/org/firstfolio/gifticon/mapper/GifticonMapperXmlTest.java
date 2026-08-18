package org.firstfolio.gifticon.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GifticonMapperXmlTest {

    @Test
    void productMapperParsesInventoryQuery() throws IOException {
        Configuration configuration = parse("mappers/gifticon/GifticonProductMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                GifticonProductMapper.class.getName() + ".findPage"
        ).getBoundSql(Map.of("status", "ON_SALE", "cursor", 10L, "size", 21));
        String normalized = normalize(sql.getSql());

        assertTrue(normalized.contains("c.status = 'AVAILABLE' AND c.expires_at > UTC_TIMESTAMP()"));
        assertTrue(normalized.contains("p.gifticon_product_id > ?"));
        assertTrue(normalized.endsWith("ORDER BY p.gifticon_product_id LIMIT ?"));
    }

    @Test
    void codeListQueryNeverSelectsProtectedColumns() throws IOException {
        Configuration configuration = parse("mappers/gifticon/GifticonCodeMapper.xml");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("gifticonProductId", 11L);
        parameters.put("status", "AVAILABLE");
        parameters.put("expiresBefore", LocalDateTime.of(2026, 9, 1, 0, 0));
        parameters.put("cursor", 100L);
        parameters.put("size", 51);
        BoundSql sql = configuration.getMappedStatement(
                GifticonCodeMapper.class.getName() + ".findPage"
        ).getBoundSql(parameters);
        String normalized = normalize(sql.getSql());

        assertTrue(normalized.contains("gifticon_product_id = ?"));
        assertTrue(normalized.contains("status = ?"));
        assertTrue(normalized.contains("expires_at < ?"));
        assertFalse(normalized.contains("code_ciphertext"));
        assertFalse(normalized.contains("code_fingerprint"));
    }

    private Configuration parse(String resource) throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments()
            ).parse();
        }
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
