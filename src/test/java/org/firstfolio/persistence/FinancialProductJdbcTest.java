package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 상품 매퍼를 실제 MySQL에 붙여서 확인한다 (FUNC-029~032, 038~039).
 *
 * <h3>왜 필요한가</h3>
 *
 * <p>기존 검증 둘은 <b>SQL을 한 줄도 실행하지 않는다.</b></p>
 *
 * <table>
 *   <tr><th>검증</th><th>보는 것</th><th>한계</th></tr>
 *   <tr><td>{@code MapperStatementTest}</td><td>XML 파싱·문장 ID·동적 SQL 전개</td><td>실행하지 않는다</td></tr>
 *   <tr><td>서비스 단위 테스트</td><td>서비스 로직</td><td>매퍼를 모킹한다</td></tr>
 * </table>
 *
 * <p>가설이 아니다. #13에서 {@code findPriceTargets}의 OGNL 결함({@code List.of()}가 만드는
 * {@code ImmutableCollections$List12}가 JDK 17 모듈 캡슐화에 막혀 {@code InaccessibleObjectException})이
 * <b>실DB 테스트에서만 잡혔다.</b> 그때도 나머지 테스트는 전부 통과하고 있었다.</p>
 *
 * <h3>특히 위험한 두 문장</h3>
 *
 * <p>{@code findPage}는 {@code <if>}가 셋이라 <b>조합이 8가지</b>고,
 * {@code updateEditableFields}는 {@code <set>}이 필드마다 갈린다. 전개 결과가 문법에 맞는지는
 * 실행해야 드러난다.</p>
 *
 * <p>읽기는 롤백이 필요 없다. <b>쓰기 두 개만 트랜잭션으로 감싸 되돌린다.</b></p>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class FinancialProductJdbcTest {

    private static final String TEST_PROVIDER = "JDBC_TEST";

    // ------------------------------------------------------------- findPage 필터 조합

    @Test
    @DisplayName("findPage — 자산군·공개여부·커서 8가지 조합이 모두 실행된다")
    void findPageRunsEveryFilterCombination() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            // <if> 세 개의 참·거짓을 모두 지난다. 전개된 SQL이 문법에 맞는지가 확인 대상이다.
            for (AssetType assetType : new AssetType[]{null, AssetType.STOCK}) {
                for (Boolean active : new Boolean[]{null, Boolean.TRUE, Boolean.FALSE}) {
                    for (Long cursor : new Long[]{null, 0L}) {
                        List<FinancialProduct> page = mapper.findPage(assetType, active, cursor, 5);

                        assertNotNull(
                                page,
                                "조합 assetType=" + assetType + " active=" + active + " cursor=" + cursor
                        );
                        assertTrue(page.size() <= 5, "LIMIT이 걸려야 합니다.");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("findPage — 조건이 하나도 없으면 WHERE 절 없이 전체를 읽는다")
    void findPageWithoutAnyFilter() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            // <where>가 비었을 때 "WHERE" 키워드만 남으면 문법 오류가 난다.
            assertFalse(mapper.findPage(null, null, null, 3).isEmpty(), "상품 시드가 필요합니다.");
        }
    }

    @Test
    @DisplayName("findPage — 커서가 실제로 다음 페이지로 이어진다")
    void findPageCursorAdvances() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            List<FinancialProduct> first = mapper.findPage(null, null, null, 2);

            assertEquals(2, first.size(), "상품이 최소 3건 있어야 하는 테스트입니다.");

            Long cursor = first.get(1).getProductId();
            List<FinancialProduct> second = mapper.findPage(null, null, cursor, 2);

            for (FinancialProduct product : second) {
                assertTrue(
                        product.getProductId() > cursor,
                        "커서 이후 상품만 나와야 합니다: " + product.getProductId()
                );
            }
        }
    }

    @Test
    @DisplayName("findPage — 비공개 상품은 active=true에서 빠진다 (FUNC-032)")
    void findPageExcludesInactiveWhenAskedForActive() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            for (FinancialProduct product : mapper.findPage(null, Boolean.TRUE, null, 50)) {
                assertTrue(product.isActive(), "공개만 요청했는데 비공개가 나왔습니다.");
            }

            for (FinancialProduct product : mapper.findPage(null, Boolean.FALSE, null, 50)) {
                assertFalse(product.isActive(), "비공개만 요청했는데 공개가 나왔습니다.");
            }
        }
    }

    // ------------------------------------------------------------- 단건 조회

    @Test
    @DisplayName("findById·findActiveById — 비공개는 공개 조회에서만 빠진다")
    void findByIdSeparatesActiveFromInactive() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                FinancialProduct hidden = product(false);
                mapper.insert(hidden);

                assertNotNull(mapper.findById(hidden.getProductId()), "비공개도 id로는 찾힌다.");
                assertNull(
                        mapper.findActiveById(hidden.getProductId()),
                        "비공개는 공개 조회에서 없는 것으로 처리돼야 합니다 (FUNC-032)."
                );

                FinancialProduct open = product(true);
                mapper.insert(open);

                assertNotNull(mapper.findActiveById(open.getProductId()));
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    @Test
    @DisplayName("없는 상품은 null이다 — 빈 결과를 예외로 만들지 않는다")
    void returnsNullWhenAbsent() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            assertNull(mapper.findById(-1L));
            assertNull(mapper.findActiveById(-1L));
            assertNull(mapper.findBySource(TEST_PROVIDER, "없는-코드-" + UUID.randomUUID()));
        }
    }

    // ------------------------------------------------------------- 쓰기 (롤백)

    @Test
    @DisplayName("insert — 컬럼·타입이 맞고 생성 키를 돌려준다")
    void insertRoundTrips() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                FinancialProduct saved = product(true);
                mapper.insert(saved);

                assertNotNull(saved.getProductId(), "useGeneratedKeys가 id를 채워야 합니다.");

                FinancialProduct found = mapper.findBySource(
                        saved.getSourceProvider(),
                        saved.getSourceProductCode()
                );

                assertNotNull(found, "findBySource가 방금 넣은 행을 찾아야 합니다.");
                assertEquals(saved.getProductId(), found.getProductId());
                assertEquals(AssetType.STOCK, found.getAssetType(), "enum 매핑");
                assertTrue(found.isActive(), "is_active ↔ active 이름이 달라 수동 매핑에 의존한다.");
                assertEquals(saved.getRealTermsJson(), found.getRealTermsJson(), "JSON 컬럼");
                assertEquals(saved.getSourceReferenceAt(), found.getSourceReferenceAt(), "DATETIME");
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    @Test
    @DisplayName("updateEditableFields — 넘긴 필드만 바뀌고 나머지는 그대로다")
    void updateChangesOnlyGivenFields() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                FinancialProduct saved = product(true);
                mapper.insert(saved);

                FinancialProduct changes = new FinancialProduct();

                changes.setProductId(saved.getProductId());
                changes.setDisplayName("바뀐 가명");
                changes.setActive(true);
                changes.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));

                assertEquals(1, mapper.updateEditableFields(changes), "한 행이 갱신돼야 합니다.");

                FinancialProduct after = mapper.findById(saved.getProductId());

                assertEquals("바뀐 가명", after.getDisplayName());
                assertEquals(saved.getDescription(), after.getDescription(), "안 넘긴 필드는 그대로");
                assertEquals(saved.getRiskLevel(), after.getRiskLevel(), "안 넘긴 필드는 그대로");
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    @Test
    @DisplayName("updateEditableFields — 선택 필드를 하나도 안 넘겨도 SQL이 성립한다")
    void updateWithoutOptionalFields() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                FinancialProduct saved = product(true);
                mapper.insert(saved);

                // <if> 네 개가 모두 거짓인 경우. is_active·updated_at이 무조건 들어가 SET이 비지 않는다.
                FinancialProduct changes = new FinancialProduct();

                changes.setProductId(saved.getProductId());
                changes.setActive(true);
                changes.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));

                assertEquals(1, mapper.updateEditableFields(changes));
                assertEquals("가명 " + saved.getSourceProductCode(),
                        mapper.findById(saved.getProductId()).getDisplayName());
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    /**
     * {@code is_active}는 {@code <if>} 밖에 있어 <b>항상 덮어쓴다.</b>
     *
     * <p>{@code active}가 {@code boolean} 원시 타입이라 새 {@link FinancialProduct}의 기본값은
     * {@code false}다. 즉 <b>호출부가 현재 값을 넣어 주지 않으면 상품이 조용히 비공개가 된다.</b>
     * 지금은 {@code FinancialProductAdminService}가 {@code findById}로 읽어 넘기고 있어 안전하지만,
     * 이 성질을 모르고 새 호출부를 만들면 바로 걸린다. 그래서 여기 고정해 둔다.</p>
     */
    @Test
    @DisplayName("updateEditableFields — is_active는 조건 없이 덮어쓴다 (호출부가 현재 값을 넘겨야 한다)")
    void updateAlwaysOverwritesActiveFlag() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                FinancialProduct saved = product(true);
                mapper.insert(saved);

                FinancialProduct changes = new FinancialProduct();

                changes.setProductId(saved.getProductId());
                changes.setDisplayName("설명만 바꾸려 했다");
                // active를 설정하지 않는다 — boolean 기본값 false가 그대로 들어간다.
                changes.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));

                mapper.updateEditableFields(changes);

                assertFalse(
                        mapper.findById(saved.getProductId()).isActive(),
                        "active를 안 넘기면 비공개가 된다. 호출부가 현재 값을 넘겨야 한다."
                );
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    // ------------------------------------------------------------- 가격 대상 조회

    @Test
    @DisplayName("findPriceTargets — 공개된 주식·펀드만, 상품 필터도 함께 동작한다")
    void findPriceTargetsFiltersByTypeAndId() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            FinancialProductMapper mapper = context.getBean(FinancialProductMapper.class);

            // ArrayList로 넘긴다. List.of()는 OGNL 리플렉션에 막힌다 (#13에서 겪은 결함).
            List<AssetType> types = new ArrayList<>(List.of(AssetType.STOCK, AssetType.FUND));
            List<FinancialProduct> all = mapper.findPriceTargets(types, null);

            assertFalse(all.isEmpty(), "공개된 주식·펀드 시드가 필요합니다.");

            for (FinancialProduct product : all) {
                assertTrue(product.isActive(), "비공개가 가격 갱신 대상에 들어가면 안 됩니다.");
                assertTrue(
                        product.getAssetType() == AssetType.STOCK
                                || product.getAssetType() == AssetType.FUND,
                        "예·적금·채권은 시세로 평가하지 않습니다."
                );
            }

            List<Long> onlyFirst = new ArrayList<>(List.of(all.get(0).getProductId()));

            assertEquals(1, mapper.findPriceTargets(types, onlyFirst).size(), "상품 필터가 걸려야 합니다.");
        }
    }

    // ------------------------------------------------------------- 도구

    /**
     * 저장해도 시드와 섞이지 않도록 제공처를 따로 쓴다. 어차피 롤백된다.
     *
     * <p>{@code real_terms_json}·{@code simulation_terms_json}은 <b>둘 다 NOT NULL</b>이다.
     * 주식은 시간 압축 예외라 압축 배율 대신 <b>예외라는 사실 자체</b>를 담는 것이 이 프로젝트의
     * 관례다 (실데이터 확인: {@code {"time_compressed": false, "reason": "STOCK_REALTIME_PRICE"}}).</p>
     */
    private static FinancialProduct product(boolean active) {
        String code = "T" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

        FinancialProduct product = new FinancialProduct();

        product.setAssetType(AssetType.STOCK);
        product.setDisplayName("가명 " + code);
        product.setDescription("실DB 검증용");
        product.setSourceProvider(TEST_PROVIDER);
        product.setSourceProductCode(code);
        product.setSourceProductName("원본 " + code);
        product.setSourceReferenceAt(now);
        product.setRealTermsJson("{\"rate_percent\": \"3.20\"}");
        product.setSimulationTermsJson(
                "{\"time_compressed\": false, \"reason\": \"STOCK_REALTIME_PRICE\"}"
        );
        product.setRiskLevel("MEDIUM");
        product.setActive(active);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        return product;
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("financialProductJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }
}
