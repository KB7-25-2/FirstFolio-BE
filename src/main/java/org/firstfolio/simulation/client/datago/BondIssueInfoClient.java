package org.firstfolio.simulation.client.datago;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공공데이터포털 {@code 금융위원회_채권기본정보}에서 만기·표면이율·신용등급을 가져온다.
 *
 * <p>채권시세 API에는 만기·금리가 없어서 상품 등록에는 이 API가 반드시 필요하다.</p>
 */
@Component
public class BondIssueInfoClient {

    private static final Logger log = LogManager.getLogger(BondIssueInfoClient.class);

    private static final DateTimeFormatter BAS_DT = DateTimeFormatter.ofPattern("uuuuMMdd");

    /** "6개월", "3개월" 같은 문구에서 개월 수를 뽑는다. */
    private static final Pattern MONTHS = Pattern.compile("(\\d+)\\s*개월");

    /** 휴일이면 그날 자 데이터가 없다. 며칠 전까지 거슬러 올라가며 찾는다. */
    private static final int MAX_BASE_DATE_LOOKBACK_DAYS = 7;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String baseUrl;
    private final String serviceKey;

    public BondIssueInfoClient(
            @Value("${datago.bond.base-url}") String baseUrl,
            @Value("${datago.service-key:}") String serviceKey
    ) {
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    /**
     * 종목코드로 최신 기본정보를 가져온다.
     *
     * <p>{@code isinCd}만 주면 영업일마다 쌓인 과거 이력이 전부 나오고, 첫 페이지는 가장 오래된
     * 행이다(2020년 데이터에는 신용등급이 지금과 다르다). 그래서 {@code basDt}를 함께 지정해
     * 최신 스냅샷만 읽는다.</p>
     */
    public List<BondBasicInfo> fetchLatest(List<String> isinCodes, LocalDate baseDate) {
        List<BondBasicInfo> found = new ArrayList<>();

        for (String isinCd : isinCodes) {
            findOne(isinCd, baseDate).ifPresentOrElse(
                    found::add,
                    () -> log.warn("채권 기본정보를 찾지 못했습니다 isinCd={}", isinCd)
            );
        }

        log.info("채권 기본정보 수집 요청={} 확보={}", isinCodes.size(), found.size());

        return found;
    }

    private Optional<BondBasicInfo> findOne(String isinCd, LocalDate baseDate) {
        for (int back = 0; back <= MAX_BASE_DATE_LOOKBACK_DAYS; back++) {
            LocalDate target = baseDate.minusDays(back);
            Optional<BondBasicInfoResponse.Item> item = request(isinCd, target);

            if (item.isPresent()) {
                return item.map(BondIssueInfoClient::toBondBasicInfo);
            }
        }

        return Optional.empty();
    }

    private Optional<BondBasicInfoResponse.Item> request(String isinCd, LocalDate baseDate) {
        URI uri = URI.create(String.format(
                "%s?serviceKey=%s&resultType=json&numOfRows=1&pageNo=1&isinCd=%s&basDt=%s",
                baseUrl,
                serviceKey,
                isinCd,
                BAS_DT.format(baseDate)
        ));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw sourceUnavailable(isinCd, exception);
        } catch (Exception exception) {
            throw sourceUnavailable(isinCd, exception);
        }

        if (response.statusCode() != 200) {
            // 인증키가 쿼리에 들어가므로 URI는 로그에 남기지 않는다.
            log.error("채권 기본정보 응답 오류 isinCd={} status={}", isinCd, response.statusCode());
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "채권 기본정보 제공처 응답이 올바르지 않습니다."
            );
        }

        BondBasicInfoResponse parsed;

        try {
            parsed = parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw sourceUnavailable(isinCd, exception);
        }

        if (parsed.getResponse() == null
                || parsed.getResponse().getHeader() == null
                || !parsed.getResponse().getHeader().isSuccess()) {
            String msg = parsed.getResponse() == null || parsed.getResponse().getHeader() == null
                    ? "응답 없음"
                    : parsed.getResponse().getHeader().getResultMsg();

            log.error("채권 기본정보 오류 응답 isinCd={} msg={}", isinCd, msg);
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "채권 기본정보를 가져오지 못했습니다."
            );
        }

        BondBasicInfoResponse.Body body = parsed.getResponse().getBody();

        if (body == null || body.getItems() == null || body.getItems().getItem() == null
                || body.getItems().getItem().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(body.getItems().getItem().get(0));
    }

    /** 응답 파싱만 떼어 둔다. 실제 호출 없이 표기 규칙을 검증할 수 있게 하기 위함이다. */
    static BondBasicInfoResponse parse(String json) throws IOException {
        return DataGoKrClientSupport.parse(json, BondBasicInfoResponse.class);
    }

    static BondBasicInfo toBondBasicInfo(BondBasicInfoResponse.Item item) {
        return new BondBasicInfo(
                item.getIsinCd(),
                trim(item.getIsinCdNm()),
                trim(item.getBondIsurNm()),
                parseDate(item.getBondIssuDt()),
                parseDate(item.getBondExprDt()),
                parseRate(item.getBondSrfcInrt()),
                parseMonths(item.getIntPayCyclCtt()),
                trim(item.getBondIntTcdNm()),
                trim(item.getScrsItmsKcdNm()),
                trim(item.getNiceScrsItmsKcdNm())
        );
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LocalDate parseDate(String yyyyMMdd) {
        String value = trim(yyyyMMdd);

        if (value == null || value.length() != 8) {
            return null;
        }

        try {
            return LocalDate.parse(value, BAS_DT);
        } catch (Exception exception) {
            return null;
        }
    }

    private static BigDecimal parseRate(String rate) {
        String value = trim(rate);

        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer parseMonths(String cycle) {
        String value = trim(cycle);

        if (value == null) {
            return null;
        }

        Matcher matcher = MONTHS.matcher(value);

        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private ApiException sourceUnavailable(String isinCd, Exception cause) {
        log.error("채권 기본정보 호출 실패 isinCd={}", isinCd, cause);

        return new ApiException(
                ErrorCode.INVALID_SOURCE_PRODUCT,
                "채권 기본정보 제공처에 연결하지 못했습니다.",
                cause
        );
    }
}
