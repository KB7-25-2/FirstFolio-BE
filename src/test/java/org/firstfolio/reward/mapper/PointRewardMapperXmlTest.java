package org.firstfolio.reward.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.firstfolio.reward.domain.PointTransaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PointRewardMapperXmlTest {

    private static final String RESOURCE =
            "mappers/reward/PointRewardMapper.xml";

    @Test
    void parsesPolicyBalanceAndLedgerStatements() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }

        assertTrue(configuration.hasMapper(PointRewardMapper.class));
        assertTrue(configuration.hasStatement(id("findActivePolicyAt")));
        assertTrue(configuration.hasStatement(id("findTransactionById")));
        assertTrue(configuration.hasStatement(id("increasePointBalance")));
        assertTrue(configuration.hasStatement(id("findPointBalance")));
        assertTrue(configuration.hasStatement(id("insertTransaction")));

        BoundSql policySql = configuration.getMappedStatement(
                        id("findActivePolicyAt")
                )
                .getBoundSql(Map.of(
                        "policyKey", "DAILY_QUEST_REWARD",
                        "effectiveAt",
                        LocalDateTime.of(2026, 8, 13, 1, 30)
                ));
        assertTrue(normalize(policySql.getSql()).contains(
                "AND effective_from <= ? "
                        + "AND (effective_to IS NULL OR effective_to > ?)"
        ));
        assertTrue(normalize(policySql.getSql()).endsWith(
                "ORDER BY version_no DESC LIMIT 1 FOR SHARE"
        ));

        BoundSql balanceSql = configuration.getMappedStatement(
                        id("increasePointBalance")
                )
                .getBoundSql(Map.of(
                        "userId", 11L,
                        "amount", 400,
                        "updatedAt",
                        LocalDateTime.of(2026, 8, 13, 1, 30)
                ));
        assertTrue(normalize(balanceSql.getSql()).contains(
                "SET point_balance = point_balance + ?, "
                        + "updated_at = ? WHERE user_id = ?"
        ));

        BoundSql insertSql = configuration.getMappedStatement(
                        id("insertTransaction")
                )
                .getBoundSql(new PointTransaction());
        assertTrue(normalize(insertSql.getSql()).contains(
                "INSERT INTO point_transactions"
        ));
    }

    private String id(String statement) {
        return PointRewardMapper.class.getName() + "." + statement;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
