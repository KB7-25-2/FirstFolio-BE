package org.firstfolio.simulation.client.finlife;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 금융감독원 finlife 오픈API에서 예·적금 상품을 가져온다.
 *
 * <p>제공처가 페이지 단위로 응답하므로 {@code max_page_no}까지 반드시 순회한다.
 * 저축은행 목록은 4페이지(약 395건)라 첫 페이지만 읽으면 KB저축은행이 통째로 누락된다.</p>
 */
@Component
public class FinlifeClient {

    private static final Logger log = LogManager.getLogger(FinlifeClient.class);

    /** 실수로 무한 순회하지 않도록 상한을 둔다. */
    private static final int MAX_PAGES = 50;

    /** 은행, 저축은행. SIMULATION_POLICY_v3 6절 기준. */
    private static final List<String> FINANCE_GROUPS = List.of("020000", "030300");

    private static final ObjectMapper EXTERNAL_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String baseUrl;
    private final String authKey;
    private final Set<String> allowedCompanyCodes;

    public FinlifeClient(
            @Value("${finlife.base-url}") String baseUrl,
            @Value("${finlife.auth-key}") String authKey,
            @Value("${finlife.company-codes:}") String companyCodes
    ) {
        this.baseUrl = baseUrl;
        this.authKey = authKey;
        this.allowedCompanyCodes = parseCompanyCodes(companyCodes);
    }

    /**
     * 가져올 금융회사를 제한한다.
     *
     * <p>finlife는 은행 38개사·저축은행 79개사의 상품을 전부 돌려준다. 그대로 등록하면
     * 수천 건이 검수 대기로 쌓여 관리자가 KB 상품을 찾을 수 없다. 초기 상품 구성은
     * KB국민은행 + KB저축은행이므로(SIMULATION_POLICY_v3 6절) 그 두 곳만 남긴다.</p>
     *
     * <p>비워 두면 제한하지 않는다.</p>
     */
    private static Set<String> parseCompanyCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        Set<String> codes = new HashSet<>();

        for (String code : raw.split(",")) {
            String trimmed = code.trim();

            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }

        return codes;
    }

    private boolean isAllowed(String finCoNo) {
        return allowedCompanyCodes.isEmpty() || allowedCompanyCodes.contains(finCoNo);
    }

    /**
     * 은행 + 저축은행 전체를 "상품 × 만기" 조합 단위로 가져온다.
     */
    public List<FinlifeProduct> fetchAll(FinlifeProductType productType) {
        List<FinlifeProduct> products = new ArrayList<>();

        for (String financeGroup : FINANCE_GROUPS) {
            products.addAll(fetchGroup(productType, financeGroup));
        }

        log.info(
                "finlife 수집 완료 type={} 조합수={}",
                productType,
                products.size()
        );

        return products;
    }

    private List<FinlifeProduct> fetchGroup(
            FinlifeProductType productType,
            String financeGroup
    ) {
        List<FinlifeProduct> products = new ArrayList<>();
        int page = 1;
        int maxPage = 1;

        while (page <= maxPage && page <= MAX_PAGES) {
            FinlifeResponse.Result result = request(productType, financeGroup, page);

            maxPage = result.getMaxPageNo() == null ? 1 : result.getMaxPageNo();
            products.addAll(merge(productType, result));
            page++;
        }

        if (maxPage > MAX_PAGES) {
            log.warn(
                    "finlife 페이지 상한 초과 type={} group={} maxPage={} 상한={}",
                    productType,
                    financeGroup,
                    maxPage,
                    MAX_PAGES
            );
        }

        return products;
    }

    private FinlifeResponse.Result request(
            FinlifeProductType productType,
            String financeGroup,
            int page
    ) {
        URI uri = URI.create(String.format(
                "%s/%s.json?auth=%s&topFinGrpNo=%s&pageNo=%d",
                baseUrl,
                productType.getEndpoint(),
                authKey,
                financeGroup,
                page
        ));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<byte[]> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw sourceUnavailable(productType, financeGroup, page, exception);
        } catch (Exception exception) {
            throw sourceUnavailable(productType, financeGroup, page, exception);
        }

        if (response.statusCode() != 200) {
            // 인증키가 URL 쿼리에 들어가므로 URI를 로그에 남기지 않는다.
            log.error(
                    "finlife 응답 오류 type={} group={} page={} status={}",
                    productType,
                    financeGroup,
                    page,
                    response.statusCode()
            );
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "원천 데이터 제공처 응답이 올바르지 않습니다."
            );
        }

        FinlifeResponse parsed;

        try {
            parsed = parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw sourceUnavailable(productType, financeGroup, page, exception);
        }

        if (parsed.getResult() == null || !parsed.getResult().isSuccess()) {
            String errCd = parsed.getResult() == null ? "null" : parsed.getResult().getErrCd();
            String errMsg = parsed.getResult() == null ? "응답 없음" : parsed.getResult().getErrMsg();

            log.error(
                    "finlife 오류 응답 type={} group={} page={} errCd={} errMsg={}",
                    productType,
                    financeGroup,
                    page,
                    errCd,
                    errMsg
            );
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "원천 데이터를 가져오지 못했습니다."
            );
        }

        return parsed.getResult();
    }

    /**
     * {@code baseList}와 {@code optionList}를 {@code fin_co_no + fin_prdt_cd}로 묶어
     * "상품 × 만기" 조합으로 펼친다.
     */
    private List<FinlifeProduct> merge(
            FinlifeProductType productType,
            FinlifeResponse.Result result
    ) {
        if (result.getBaseList() == null || result.getOptionList() == null) {
            // 정상 응답(err_cd=000)인데 목록이 없다면 응답 형식이 바뀐 것이다.
            // 조용히 0건으로 넘어가면 원인을 찾기 어려우므로 남긴다.
            log.warn(
                    "finlife 응답에 상품 목록이 없습니다 type={} totalCount={} 응답 형식 변경 여부 확인 필요",
                    productType,
                    result.getTotalCount()
            );

            return List.of();
        }

        Map<String, FinlifeResponse.Base> baseByKey = new HashMap<>();

        for (FinlifeResponse.Base base : result.getBaseList()) {
            baseByKey.put(key(base.getFinCoNo(), base.getFinPrdtCd()), base);
        }

        List<FinlifeProduct> products = new ArrayList<>();

        for (FinlifeResponse.Option option : result.getOptionList()) {
            if (!isAllowed(option.getFinCoNo())) {
                continue;
            }

            FinlifeResponse.Base base =
                    baseByKey.get(key(option.getFinCoNo(), option.getFinPrdtCd()));

            if (base == null || option.getIntrRate() == null) {
                // 기본금리가 없는 옵션은 임의 값을 만들지 않고 건너뛴다 (FUNC-038).
                continue;
            }

            Integer months = parseMonths(option.getSaveTrm());

            if (months == null) {
                continue;
            }

            products.add(new FinlifeProduct(
                    productType,
                    option.getFinCoNo(),
                    option.getFinPrdtCd(),
                    months,
                    base.getKorCoNm(),
                    base.getFinPrdtNm(),
                    option.getIntrRate(),
                    option.getIntrRateTypeNm(),
                    option.getRsrvTypeNm(),
                    base.getDclsMonth()
            ));
        }

        return products;
    }

    /** 응답 파싱만 떼어 둔다. 실제 호출 없이 표기 규칙을 검증할 수 있게 하기 위함이다. */
    static FinlifeResponse parse(String json) throws IOException {
        return EXTERNAL_MAPPER.readValue(json, FinlifeResponse.class);
    }

    private static String key(String finCoNo, String finPrdtCd) {
        return finCoNo + ":" + finPrdtCd;
    }

    private static Integer parseMonths(String saveTrm) {
        if (saveTrm == null || saveTrm.isBlank()) {
            return null;
        }

        try {
            int months = Integer.parseInt(saveTrm.trim());

            return months > 0 ? months : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ApiException sourceUnavailable(
            FinlifeProductType productType,
            String financeGroup,
            int page,
            Exception cause
    ) {
        log.error(
                "finlife 호출 실패 type={} group={} page={}",
                productType,
                financeGroup,
                page,
                cause
        );

        return new ApiException(
                ErrorCode.INVALID_SOURCE_PRODUCT,
                "원천 데이터 제공처에 연결하지 못했습니다.",
                cause
        );
    }
}
