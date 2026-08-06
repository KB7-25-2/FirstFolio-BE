package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioStatus;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 포트폴리오 초기화 (FUNC-037).
 *
 * <h3>"파산"이 아니라 초기화다</h3>
 *
 * <p>명세와 디자인에는 "파산 신청"으로 되어 있지만, <b>이 서비스에서는 파산이 성립하지 않는다.</b>
 * 예·적금·채권은 원금으로 평가되고, 대출·공매도·신용이 없어 빚을 질 수단이 없으며,
 * 현금은 {@code CHECK (cash_balance >= 0)}이 막는다. 자산이 0이 되는 경로가 없다.
 * 그래서 이 기능은 <b>학습자가 다시 시작하는 장치</b>일 뿐이고, 횟수도 제한하지 않는다
 * (2026-08-06 확정, {@code DECISION_TIMELINE.md} D12).</p>
 *
 * <h3>보유 상품은 건드리지 않는다</h3>
 *
 * <p>세대를 {@code CLOSED}로 닫으면 그 세대의 보유는 새 세대 조회에 나오지 않는다
 * ({@code portfolio_holdings}는 언제나 {@code portfolio_id}로만 조회된다). 상태를 {@code SOLD}로
 * 바꾸면 <b>팔지도 않은 것을 팔았다고 기록</b>하게 되고, 대응하는 {@code SELL} 거래가 없어
 * 두 테이블이 어긋난다.</p>
 *
 * <h3>하나의 트랜잭션이어야 한다</h3>
 *
 * <p>세대 종료 · 새 세대 생성 · 이력 기록이 <b>전부 반영되거나 전부 취소</b>돼야 한다.
 * 중간에 끊기면 사용자에게 활성 포트폴리오가 없거나 둘이 되는 상태가 남는다.</p>
 */
@Service
public class PortfolioResetService {

    /** API_DOCS가 정한 고정 확인 문구. 이 값이 정확히 와야 초기화한다. */
    public static final String RESET_CONFIRMATION = "RESET_PORTFOLIO";

    private static final Logger log = LogManager.getLogger(PortfolioResetService.class);

    private static final ObjectMapper OBJECT_MAPPER = ApiObjectMapperFactory.create();

    private final PortfolioMapper portfolioMapper;
    private final PortfolioHoldingMapper holdingMapper;
    private final PortfolioTransactionMapper transactionMapper;

    public PortfolioResetService(
            PortfolioMapper portfolioMapper,
            PortfolioHoldingMapper holdingMapper,
            PortfolioTransactionMapper transactionMapper
    ) {
        this.portfolioMapper = portfolioMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
    }

    /**
     * 현재 세대를 닫고 3천만원짜리 새 세대를 만든다.
     *
     * @param confirmation   {@link #RESET_CONFIRMATION}과 정확히 같아야 한다
     * @param idempotencyKey 같은 키로 다시 부르면 초기화하지 않고 기존 결과를 돌려준다
     */
    @Transactional
    public PortfolioResetResult reset(Long userId, String confirmation, String idempotencyKey) {
        requireConfirmation(confirmation);
        requireIdempotencyKey(idempotencyKey);

        PortfolioTransaction done = transactionMapper.findByIdempotencyKey(idempotencyKey);

        if (done != null) {
            log.info("이미 처리된 초기화 요청입니다 userId={} key={}", userId, idempotencyKey);

            return replay(done);
        }

        Portfolio current = portfolioMapper.findActiveByUserId(userId);

        if (current == null) {
            throw new ApiException(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String detailJson = describe(current, now);

        // 조건부 UPDATE라 다른 요청이 먼저 닫았으면 0이 온다.
        if (portfolioMapper.closeGeneration(current.getPortfolioId(), now) == 0) {
            log.warn("이미 종료된 세대입니다 userId={} portfolioId={}", userId, current.getPortfolioId());

            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "초기화가 이미 처리되었습니다. 잠시 후 다시 조회해 주세요."
            );
        }

        Portfolio created = openNextGeneration(current, now);
        PortfolioTransaction record = record(created, idempotencyKey, detailJson, now);

        log.info(
                "포트폴리오 초기화 userId={} 닫은세대={} 새세대={} generation={}",
                userId,
                current.getPortfolioId(),
                created.getPortfolioId(),
                created.getGenerationNo()
        );

        return new PortfolioResetResult(
                current.getPortfolioId(),
                created.getPortfolioId(),
                created.getGenerationNo(),
                created.getCashBalance(),
                record.getPortfolioTransactionId()
        );
    }

    /** 새 세대. 지급액은 최초 지급과 같은 값을 쓴다 (v3 4절). */
    private Portfolio openNextGeneration(Portfolio closed, LocalDateTime now) {
        Portfolio next = new Portfolio();

        next.setUserId(closed.getUserId());
        next.setGenerationNo(closed.getGenerationNo() + 1);
        next.setStatus(PortfolioStatus.ACTIVE);
        next.setInitialAmount(InitialGrantService.INITIAL_AMOUNT);
        next.setCashBalance(InitialGrantService.INITIAL_AMOUNT);
        next.setOpenedAt(now);
        next.setCreatedAt(now);
        next.setUpdatedAt(now);

        portfolioMapper.insert(next);

        return next;
    }

    /**
     * 초기화 이력. <b>새 세대에</b> 남긴다 — 사용자에게는 새 출발의 첫 기록으로 보여야 한다.
     *
     * <p>지급 성격이 같으므로 금액은 최초 지급과 같다. 초기화 직전 상태는 {@code detail_json}에
     * 남긴다 (스키마 주석이 이 컬럼에 "초기화 전후 정보"를 두라고 되어 있다).</p>
     */
    private PortfolioTransaction record(
            Portfolio created,
            String idempotencyKey,
            String detailJson,
            LocalDateTime now
    ) {
        PortfolioTransaction reset = new PortfolioTransaction();

        reset.setPortfolioId(created.getPortfolioId());
        reset.setTransactionType(TransactionType.RESET);
        reset.setAmount(InitialGrantService.INITIAL_AMOUNT);
        reset.setStatus(TransactionStatus.COMPLETED);
        reset.setProcessedAt(now);
        reset.setIdempotencyKey(idempotencyKey);
        reset.setDetailJson(detailJson);
        reset.setCreatedAt(now);

        transactionMapper.insert(reset);

        return reset;
    }

    /** 초기화 직전 상태를 담는다. 나중에 "무엇을 지웠는지" 확인할 수 있는 유일한 기록이다. */
    private String describe(Portfolio closing, LocalDateTime now) {
        ObjectNode detail = OBJECT_MAPPER.createObjectNode();

        detail.put("previous_portfolio_id", closing.getPortfolioId());
        detail.put("previous_generation_no", closing.getGenerationNo());
        detail.put("previous_cash_balance", closing.getCashBalance().toPlainString());
        detail.put("previous_holding_count",
                holdingMapper.findActiveByPortfolioId(closing.getPortfolioId()).size());
        detail.put("reset_at", now.toString());

        return detail.toString();
    }

    /**
     * 같은 키로 다시 온 요청. 초기화하지 않고 그때 만든 결과를 그대로 돌려준다.
     *
     * <p>닫힌 세대 식별자는 {@code detail_json}에만 있으므로 거기서 꺼낸다.</p>
     */
    private PortfolioResetResult replay(PortfolioTransaction done) {
        Portfolio created = portfolioMapper.findById(done.getPortfolioId());

        return new PortfolioResetResult(
                previousPortfolioId(done.getDetailJson()),
                done.getPortfolioId(),
                created == null ? null : created.getGenerationNo(),
                done.getAmount(),
                done.getPortfolioTransactionId()
        );
    }

    private static Long previousPortfolioId(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(detailJson).get("previous_portfolio_id");

            return node == null || node.isNull() ? null : node.asLong();
        } catch (Exception exception) {
            // 이력을 못 읽는다고 재요청을 실패시키지는 않는다. 새 세대 정보는 그대로 유효하다.
            log.warn("초기화 이력을 읽지 못했습니다", exception);

            return null;
        }
    }

    private static void requireConfirmation(String confirmation) {
        if (!RESET_CONFIRMATION.equals(confirmation)) {
            throw new ApiException(ErrorCode.RESET_CONFIRMATION_REQUIRED);
        }
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "중복 초기화 방지 키가 필요합니다.");
        }
    }
}
