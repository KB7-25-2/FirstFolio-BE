package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.AssetAllocation;
import org.firstfolio.portfolio.domain.HoldingValuation;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.PortfolioValuation;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 포트폴리오 상세·이력 조회 (FUNC-034).
 *
 * <h3>다른 사용자의 포트폴리오를 조회할 수 없는 방법</h3>
 *
 * <p>포트폴리오 식별자를 <b>입력으로 받지 않는다.</b> 언제나 요청한 사용자의 활성 포트폴리오를
 * 찾아서 쓴다. 식별자를 받아 소유자를 검사하는 방식은 검사를 빠뜨리면 그대로 뚫리지만,
 * 받을 수 없으면 빠뜨릴 검사 자체가 없다 (FUNC-034 예외/제한사항).</p>
 */
@Service
public class PortfolioQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** 저장된 {@code detail_json}을 응답 표기 그대로 읽는다. */
    private static final ObjectMapper OBJECT_MAPPER = ApiObjectMapperFactory.create();

    private final PortfolioMapper portfolioMapper;
    private final PortfolioTransactionMapper transactionMapper;
    private final PortfolioValuationService valuationService;

    public PortfolioQueryService(
            PortfolioMapper portfolioMapper,
            PortfolioTransactionMapper transactionMapper,
            PortfolioValuationService valuationService
    ) {
        this.portfolioMapper = portfolioMapper;
        this.transactionMapper = transactionMapper;
        this.valuationService = valuationService;
    }

    /** 현재 활성 포트폴리오의 현금·보유·평가액·손익·자산군 비중. */
    @Transactional(readOnly = true)
    public PortfolioDetailResponse findCurrent(Long userId) {
        Portfolio portfolio = requireActivePortfolio(userId);
        PortfolioValuation valuation = valuationService.valuate(portfolio);

        List<PortfolioDetailResponse.Holding> holdings = new ArrayList<>();

        for (HoldingValuation holdingValuation : valuation.getHoldings()) {
            holdings.add(toResponse(holdingValuation));
        }

        List<PortfolioDetailResponse.Allocation> allocation = new ArrayList<>();

        for (AssetAllocation assetAllocation : valuation.getAllocations()) {
            allocation.add(new PortfolioDetailResponse.Allocation(
                    assetAllocation.getAssetType().name(),
                    assetAllocation.getValuationAmount(),
                    assetAllocation.getRatio()
            ));
        }

        PortfolioDetailResponse.Summary summary = new PortfolioDetailResponse.Summary(
                valuation.getHoldingsValue(),
                valuation.getTotalAssets(),
                valuation.getProfitLoss(),
                valuation.getProfitRate()
        );

        return new PortfolioDetailResponse(
                portfolio.getPortfolioId(),
                portfolio.getGenerationNo(),
                valuation.getCashBalance(),
                holdings,
                summary,
                allocation,
                valuation.getValuedAt()
        );
    }

    /** 현재 세대의 거래·자산 이벤트 이력. 최신순 커서 페이지다. */
    @Transactional(readOnly = true)
    public PortfolioTransactionPageResponse findCurrentTransactions(
            Long userId,
            String type,
            String cursor,
            Integer size
    ) {
        Portfolio portfolio = requireActivePortfolio(userId);
        int pageSize = resolvePageSize(size);

        // 다음 페이지가 있는지 알려고 한 건 더 읽는다.
        List<PortfolioTransaction> found = transactionMapper.findPage(
                portfolio.getPortfolioId(),
                parseType(type),
                parseCursor(cursor),
                pageSize + 1
        );

        boolean hasNext = found.size() > pageSize;
        List<PortfolioTransaction> page = hasNext ? found.subList(0, pageSize) : found;

        List<PortfolioTransactionPageResponse.Item> items = new ArrayList<>();

        for (PortfolioTransaction transaction : page) {
            items.add(toResponse(transaction));
        }

        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getPortfolioTransactionId())
                : null;

        return new PortfolioTransactionPageResponse(items, nextCursor);
    }

    /**
     * 활성 포트폴리오. 없으면 404다.
     *
     * <p>기초 과정을 아직 마치지 않아 지급이 일어나지 않은 상태가 여기에 해당한다 (FUNC-029).</p>
     */
    private Portfolio requireActivePortfolio(Long userId) {
        Portfolio portfolio = portfolioMapper.findActiveByUserId(userId);

        if (portfolio == null) {
            throw new ApiException(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND);
        }

        return portfolio;
    }

    private static PortfolioDetailResponse.Holding toResponse(HoldingValuation valuation) {
        PortfolioHolding holding = valuation.getHolding();

        return new PortfolioDetailResponse.Holding(
                holding.getHoldingId(),
                holding.getProductId(),
                holding.getProductDisplayName(),
                holding.getProductAssetType() == null ? null : holding.getProductAssetType().name(),
                holding.getQuantity(),
                holding.getPrincipalAmount(),
                valuation.getValuationAmount(),
                valuation.getBasis().name(),
                valuation.getValuedAt()
        );
    }

    private static PortfolioTransactionPageResponse.Item toResponse(PortfolioTransaction transaction) {
        return new PortfolioTransactionPageResponse.Item(
                transaction.getPortfolioTransactionId(),
                transaction.getTransactionType() == null ? null : transaction.getTransactionType().name(),
                transaction.getProductDisplayName(),
                transaction.getAmount(),
                transaction.getQuantity(),
                transaction.getUnitPrice(),
                transaction.getStatus() == null ? null : transaction.getStatus().name(),
                transaction.getScheduledAt(),
                transaction.getProcessedAt(),
                readDetail(transaction.getDetailJson())
        );
    }

    private static JsonNode readDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readTree(detailJson);
        } catch (Exception exception) {
            // 우리가 쓴 JSON이므로 못 읽으면 데이터가 깨진 것이다. 조용히 넘기지 않는다.
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    "거래 상세 정보를 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private static int resolvePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static TransactionType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        try {
            return TransactionType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "거래 유형 필터가 올바르지 않습니다.");
        }
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "페이지 커서가 올바르지 않습니다.");
        }
    }
}
