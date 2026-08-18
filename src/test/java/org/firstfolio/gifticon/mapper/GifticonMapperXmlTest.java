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

    @Test
    void exchangeMapperUsesDatabaseLocksAndConditionalMutations() throws IOException {
        Configuration configuration = parse(
                "mappers/gifticon/GifticonExchangeMapper.xml"
        );

        BoundSql userLock = configuration.getMappedStatement(
                GifticonExchangeMapper.class.getName() + ".findPointBalanceForUpdate"
        ).getBoundSql(Map.of("userId", 101L));
        assertTrue(normalize(userLock.getSql()).endsWith("FOR UPDATE"));

        BoundSql codeLock = configuration.getMappedStatement(
                GifticonExchangeMapper.class.getName() + ".lockNextAvailableCode"
        ).getBoundSql(Map.of(
                "gifticonProductId", 11L,
                "now", LocalDateTime.of(2026, 8, 18, 7, 30)
        ));
        String normalizedCodeLock = normalize(codeLock.getSql());
        assertTrue(normalizedCodeLock.contains("status = 'AVAILABLE'"));
        assertTrue(normalizedCodeLock.contains("expires_at > ?"));
        assertTrue(normalizedCodeLock.endsWith("FOR UPDATE SKIP LOCKED"));

        BoundSql waitingLock = configuration.getMappedStatement(
                GifticonExchangeMapper.class.getName() + ".lockNextAvailableCodeWaiting"
        ).getBoundSql(Map.of(
                "gifticonProductId", 11L,
                "now", LocalDateTime.of(2026, 8, 18, 7, 30)
        ));
        assertTrue(normalize(waitingLock.getSql()).endsWith("FOR UPDATE"));
        assertFalse(normalize(waitingLock.getSql()).contains("SKIP LOCKED"));

        BoundSql pointDecrease = configuration.getMappedStatement(
                GifticonExchangeMapper.class.getName() + ".decreasePointBalance"
        ).getBoundSql(Map.of(
                "userId", 101L,
                "amount", 5000,
                "updatedAt", LocalDateTime.of(2026, 8, 18, 7, 30)
        ));
        assertTrue(normalize(pointDecrease.getSql()).contains(
                "point_balance = point_balance - ?"
        ));
        assertTrue(normalize(pointDecrease.getSql()).contains(
                "AND point_balance >= ?"
        ));
    }

    @Test
    void exchangeDoesNotSerializeEveryRequestOnTheProductRow() throws IOException {
        Configuration configuration = parse(
                "mappers/gifticon/GifticonProductMapper.xml"
        );
        BoundSql productLookup = configuration.getMappedStatement(
                GifticonProductMapper.class.getName() + ".findById"
        ).getBoundSql(Map.of("gifticonProductId", 11L));

        assertFalse(normalize(productLookup.getSql()).contains("FOR UPDATE"));
    }

    @Test
    void ordinaryOrderQueriesNeverReadCiphertext() throws IOException {
        Configuration configuration = parse(
                "mappers/gifticon/GifticonExchangeMapper.xml"
        );
        Map<String, Object> pageParameters = new HashMap<>();
        pageParameters.put("userId", 101L);
        pageParameters.put("cursor", 501L);
        pageParameters.put("size", 21);
        BoundSql page = configuration.getMappedStatement(
                GifticonExchangeMapper.class.getName() + ".findOrdersByUser"
        ).getBoundSql(pageParameters);
        String pageSql = normalize(page.getSql());
        assertFalse(pageSql.contains("code_ciphertext"));
        assertFalse(pageSql.contains("encryption_key_version"));
        assertTrue(pageSql.contains("o.user_id = ?"));

        BoundSql disclosure = configuration.getMappedStatement(
                GifticonExchangeMapper.class.getName() + ".findDisclosureForUpdate"
        ).getBoundSql(Map.of("userId", 101L, "gifticonOrderId", 501L));
        String disclosureSql = normalize(disclosure.getSql());
        assertTrue(disclosureSql.contains("c.code_ciphertext"));
        assertTrue(disclosureSql.contains("c.encryption_key_version"));
        assertTrue(disclosureSql.endsWith("FOR UPDATE"));
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
