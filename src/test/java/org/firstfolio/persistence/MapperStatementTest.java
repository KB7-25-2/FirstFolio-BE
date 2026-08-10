package org.firstfolio.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.firstfolio.user.domain.User;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 매퍼 XML과 매퍼 인터페이스가 맞물리는지 확인한다.
 *
 * <p>둘의 어긋남은 컴파일에서 걸리지 않고 <b>그 API를 처음 호출하는 순간</b> 터진다.
 * 서비스 테스트는 매퍼를 모킹하므로 XML을 한 줄도 읽지 않아 여기를 지나간다.
 * MyBatis 설정을 실제로 만들어 보면 DB 없이도 다음을 잡을 수 있다.</p>
 *
 * <ul>
 *   <li>XML에 없는 메서드 (선언만 하고 구현 XML을 빠뜨린 경우)</li>
 *   <li>XML 문법 오류, resultMap·namespace 오타</li>
 *   <li>동적 SQL({@code <if>}, {@code <foreach>})이 만들어 내는 문장의 형태</li>
 * </ul>
 */
class MapperStatementTest {

    private static final String MAPPER_PACKAGE = "org.firstfolio";

    private final Configuration configuration = buildConfiguration();

    private static Configuration buildConfiguration() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();

            factoryBean.setConfigLocation(resolver.getResource("classpath:/mybatis-config.xml"));
            factoryBean.setMapperLocations(resolver.getResources("classpath*:mappers/**/*.xml"));
            // 문장을 실행하지 않으므로 연결하지 않는 DataSource로 충분하다.
            factoryBean.setDataSource(new SimpleDriverDataSource());

            SqlSessionFactory factory = factoryBean.getObject();

            return factory.getConfiguration();
        } catch (Exception exception) {
            throw new IllegalStateException("매퍼 설정을 만들 수 없습니다.", exception);
        }
    }

    private static List<Class<?>> mapperInterfaces() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition
                    ) {
                        return definition.getMetadata().isInterface();
                    }
                };

        scanner.addIncludeFilter(new AnnotationTypeFilter(Mapper.class));

        Set<BeanDefinition> found = scanner.findCandidateComponents(MAPPER_PACKAGE);
        List<Class<?>> mappers = new ArrayList<>();

        for (BeanDefinition definition : found) {
            try {
                mappers.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        }

        return mappers;
    }

    @Test
    @DisplayName("@Mapper 인터페이스의 모든 메서드에 대응하는 문장이 있다")
    void everyMapperMethodHasStatement() {
        List<Class<?>> mappers = mapperInterfaces();

        assertFalse(mappers.isEmpty(), "매퍼 인터페이스를 하나도 못 찾았습니다.");

        for (Class<?> mapper : mappers) {
            for (Method method : mapper.getDeclaredMethods()) {
                if (method.isDefault() || method.isSynthetic()) {
                    continue;
                }

                String statementId = mapper.getName() + "." + method.getName();

                assertTrue(
                        configuration.hasStatement(statementId),
                        statementId + " 에 대응하는 매퍼 XML 문장이 없습니다."
                );
            }
        }
    }

    @Test
    @DisplayName("이력 조회는 필터·커서 유무에 따라 조건을 붙인다")
    void buildsTransactionPageSqlWithOptionalConditions() {
        String statementId =
                "org.firstfolio.portfolio.mapper.PortfolioTransactionMapper.findPage";

        Map<String, Object> noFilter = new HashMap<>();

        noFilter.put("portfolioId", 8001L);
        noFilter.put("transactionType", null);
        noFilter.put("cursorId", null);
        noFilter.put("limit", 21);

        String sql = sqlOf(statementId, noFilter);

        assertTrue(sql.contains("portfolio_transactions"));
        assertFalse(sql.contains("transaction_type ="), "필터가 없으면 유형 조건이 없어야 합니다.");
        assertFalse(sql.contains("portfolio_transaction_id <"), "첫 페이지에는 커서 조건이 없어야 합니다.");

        Map<String, Object> filtered = new HashMap<>(noFilter);

        filtered.put("transactionType", org.firstfolio.portfolio.domain.TransactionType.INTEREST);
        filtered.put("cursorId", 8202L);

        String filteredSql = sqlOf(statementId, filtered);

        assertTrue(filteredSql.contains("transaction_type ="));
        assertTrue(filteredSql.contains("portfolio_transaction_id <"));
    }

    @Test
    @DisplayName("가격 일괄 조회는 상품 수만큼 IN 절을 만든다")
    void buildsInClauseForLatestPrices() {
        String statementId =
                "org.firstfolio.simulation.mapper.ProductPriceMapper.findLatestByProductIds";

        Map<String, Object> parameters = new HashMap<>();

        parameters.put("productIds", List.of(25L, 26L, 27L));

        String sql = sqlOf(statementId, parameters);

        assertTrue(sql.contains("IN"));
        assertTrue(sql.contains("MAX(reference_at)"));
        assertFalse(sql.contains("IN ( )"), "빈 IN 절이 만들어지면 안 됩니다.");
    }

    @Test
    @DisplayName("회원 INSERT는 모든 컬럼에 값을 바인딩한다")
    void bindsEveryUserInsertColumn() {
        String statementId = "org.firstfolio.user.mapper.UserMapper.insert";
        User user = User.signup(
                "firebase-uid",
                "user@example.com",
                "nickname",
                LocalDateTime.of(2026, 8, 10, 0, 0)
        );

        BoundSql boundSql = configuration.getMappedStatement(statementId).getBoundSql(user);
        List<String> parameterProperties = boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty())
                .toList();

        assertEquals(
                List.of(
                        "firebaseUid",
                        "email",
                        "nickname",
                        "roleCode",
                        "status",
                        "pointBalance",
                        "lastAttendanceDate",
                        "newsletterOptIn",
                        "createdAt",
                        "updatedAt"
                ),
                parameterProperties
        );
        assertEquals(10, boundSql.getSql().chars().filter(character -> character == '?').count());
    }

    private String sqlOf(String statementId, Map<String, Object> parameters) {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        BoundSql boundSql = statement.getBoundSql(parameters);

        return boundSql.getSql().replaceAll("\\s+", " ");
    }
}
