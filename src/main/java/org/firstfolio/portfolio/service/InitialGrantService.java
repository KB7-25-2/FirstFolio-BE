package org.firstfolio.portfolio.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioStatus;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 포트폴리오 기초 과정 최초 완료 시 모의투자금을 지급한다 (FUNC-029).
 *
 * <h3>학습 도메인과의 연결 지점</h3>
 *
 * <p>지급 트리거는 대단원 퀴즈의 마지막 문항 답안이 채점되어 포트폴리오 기초
 * 과정이 완료되는 시점에 발생한다.</p>
 *
 * <pre>
 * // 학습 도메인 (FOUNDATION 대단원 합격 처리 안에서)
 * InitialGrantResult grant = initialGrantService.grantOnFoundationCompleted(userId, curriculumItemId);
 *
 * // 응답 조립
 * response.setFoundationGrant(grant);            // granted / amount / portfolioId
 * response.setNextAction("PORTFOLIO_SETUP");
 * </pre>
 *
 * <p>같은 트랜잭션 안에서 호출하면 퀴즈 채점과 지급이 함께 커밋된다. 별도 트랜잭션으로
 * 불러도 멱등하므로 실패 시 그대로 재시도하면 된다.</p>
 *
 * <h3>멱등성</h3>
 *
 * <p>{@code user_id + curriculum_item_id}로 만든 키를 {@code idempotency_key}에 넣는다.
 * 몇 번을 호출해도 지급은 한 번뿐이다 (API_DOCS 퀴즈 제출 처리 규칙).</p>
 *
 * <p>방어선이 둘이다. 먼저 키로 조회해 걸러내고, 동시 호출로 둘 다 통과한 경우
 * {@code uq_portfolio_transactions_idempotency} 유니크 제약이 막는다.</p>
 */
@Service
public class InitialGrantService {

    /** 최초 지급액. PROJECT_SPEC 6.1, v3 4절 확정값. */
    public static final BigDecimal INITIAL_AMOUNT = new BigDecimal("30000000.00");

    private static final Logger log = LogManager.getLogger(InitialGrantService.class);

    private static final String KEY_PREFIX = "initial-grant";
    private static final int FIRST_GENERATION = 1;

    private final PortfolioMapper portfolioMapper;
    private final PortfolioTransactionMapper transactionMapper;

    public InitialGrantService(
            PortfolioMapper portfolioMapper,
            PortfolioTransactionMapper transactionMapper
    ) {
        this.portfolioMapper = portfolioMapper;
        this.transactionMapper = transactionMapper;
    }

    /**
     * 초기 모의투자금 3천만원을 정확히 한 번 지급하고 최초 포트폴리오를 만든다.
     *
     * <p>이 시점의 포트폴리오는 <b>전액 현금이고 보유 상품이 없다.</b> 상품 매수는
     * {@code POST /portfolios/current/trades}에서 한다 (FUNC-035).</p>
     *
     * <p>"최초 포트폴리오 구성"이라는 별도 단계는 없다. 지급이 끝나면 사용자는 곧바로
     * 포트폴리오 화면으로 가고, 거기서 원할 때 상품을 산다.</p>
     *
     * @param userId           지급 대상 사용자
     * @param curriculumItemId 포트폴리오 기초 과정 커리큘럼 항목. 멱등 키의 일부다
     */
    @Transactional
    public InitialGrantResult grantOnFoundationCompleted(Long userId, Long curriculumItemId) {
        if (userId == null || curriculumItemId == null) {
            throw new IllegalArgumentException("userId와 curriculumItemId는 필수입니다.");
        }

        String idempotencyKey = idempotencyKey(userId, curriculumItemId);

        PortfolioTransaction existing = transactionMapper.findByIdempotencyKey(idempotencyKey);

        if (existing != null) {
            log.info("이미 지급된 초기 모의투자금입니다 userId={} key={}", userId, idempotencyKey);

            return new InitialGrantResult(false, existing.getAmount(), existing.getPortfolioId());
        }

        try {
            return grantNow(userId, idempotencyKey);
        } catch (DuplicateKeyException exception) {
            // 동시 호출로 둘 다 조회를 통과한 경우. 유니크 제약이 막아 준다.
            PortfolioTransaction concurrent = transactionMapper.findByIdempotencyKey(idempotencyKey);

            if (concurrent == null) {
                throw exception;
            }

            log.warn("초기 지급이 동시에 요청되어 한 건만 반영했습니다 userId={}", userId);

            return new InitialGrantResult(false, concurrent.getAmount(), concurrent.getPortfolioId());
        }
    }

    private InitialGrantResult grantNow(Long userId, String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Portfolio portfolio = new Portfolio();

        portfolio.setUserId(userId);
        portfolio.setGenerationNo(FIRST_GENERATION);
        portfolio.setStatus(PortfolioStatus.ACTIVE);
        portfolio.setInitialAmount(INITIAL_AMOUNT);
        // 상품을 아직 고르지 않았으므로 전액 현금이다.
        portfolio.setCashBalance(INITIAL_AMOUNT);
        portfolio.setOpenedAt(now);
        portfolio.setCreatedAt(now);
        portfolio.setUpdatedAt(now);

        portfolioMapper.insert(portfolio);

        PortfolioTransaction grant = new PortfolioTransaction();

        grant.setPortfolioId(portfolio.getPortfolioId());
        grant.setTransactionType(TransactionType.INITIAL_GRANT);
        grant.setAmount(INITIAL_AMOUNT);
        grant.setStatus(TransactionStatus.COMPLETED);
        grant.setProcessedAt(now);
        grant.setIdempotencyKey(idempotencyKey);
        grant.setCreatedAt(now);

        transactionMapper.insert(grant);

        log.info(
                "초기 모의투자금 지급 userId={} portfolioId={} amount={}",
                userId,
                portfolio.getPortfolioId(),
                INITIAL_AMOUNT
        );

        return new InitialGrantResult(true, INITIAL_AMOUNT, portfolio.getPortfolioId());
    }

    /** {@code initial-grant:{userId}:{curriculumItemId}} */
    static String idempotencyKey(Long userId, Long curriculumItemId) {
        return KEY_PREFIX + ":" + userId + ":" + curriculumItemId;
    }
}
