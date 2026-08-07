package org.firstfolio.simulation.client.datago;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 공공데이터포털 {@code 금융위원회_증권상품시세정보}에서 ETF 시세를 가져온다.
 *
 * <p><b>{@code srtnCd}(정확 일치) 파라미터는 무시된다.</b> 넣어도 전체 결과의 첫 항목이
 * 그대로 돌아와서, 어떤 종목을 요청하든 같은 ETF가 나온다. 대신 {@code likeSrtnCd}를 쓰고,
 * 응답의 {@code srtnCd}가 요청한 코드와 같은지 확인한다.</p>
 *
 * <p>ETF는 채권보다 공시가 늦어 당일 데이터가 없을 수 있다. 며칠 전까지 거슬러 올라간다.</p>
 */
@Component
public class EtfPriceClient {

    private static final Logger log = LogManager.getLogger(EtfPriceClient.class);

    private static final DateTimeFormatter BAS_DT = DateTimeFormatter.ofPattern("uuuuMMdd");

    private static final int MAX_BASE_DATE_LOOKBACK_DAYS = 10;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String baseUrl;
    private final String serviceKey;

    public EtfPriceClient(
            @Value("${datago.etf.base-url}") String baseUrl,
            @Value("${datago.service-key:}") String serviceKey
    ) {
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public List<EtfPriceResponse.Item> fetchLatest(List<String> shortCodes, LocalDate baseDate) {
        List<EtfPriceResponse.Item> found = new ArrayList<>();

        for (String shortCode : shortCodes) {
            findOne(shortCode, baseDate).ifPresentOrElse(
                    found::add,
                    () -> log.warn("ETF 시세를 찾지 못했습니다 srtnCd={}", shortCode)
            );
        }

        log.info("ETF 시세 수집 요청={} 확보={}", shortCodes.size(), found.size());

        return found;
    }

    private Optional<EtfPriceResponse.Item> findOne(String shortCode, LocalDate baseDate) {
        for (int back = 0; back <= MAX_BASE_DATE_LOOKBACK_DAYS; back++) {
            Optional<EtfPriceResponse.Item> item = request(shortCode, baseDate.minusDays(back));

            if (item.isPresent()) {
                return item;
            }
        }

        return Optional.empty();
    }

    private Optional<EtfPriceResponse.Item> request(String shortCode, LocalDate baseDate) {
        URI uri = URI.create(String.format(
                "%s?serviceKey=%s&resultType=json&numOfRows=5&pageNo=1&likeSrtnCd=%s&basDt=%s",
                baseUrl,
                serviceKey,
                shortCode,
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
            throw sourceUnavailable(shortCode, exception);
        } catch (Exception exception) {
            throw sourceUnavailable(shortCode, exception);
        }

        if (response.statusCode() != 200) {
            // 인증키가 쿼리에 들어가므로 URI는 로그에 남기지 않는다.
            log.error("ETF 시세 응답 오류 srtnCd={} status={}", shortCode, response.statusCode());
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "ETF 시세 제공처 응답이 올바르지 않습니다."
            );
        }

        EtfPriceResponse parsed;

        try {
            parsed = parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw sourceUnavailable(shortCode, exception);
        }

        if (parsed.getResponse() == null
                || parsed.getResponse().getHeader() == null
                || !parsed.getResponse().getHeader().isSuccess()) {
            log.error("ETF 시세 오류 응답 srtnCd={}", shortCode);
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "ETF 시세를 가져오지 못했습니다."
            );
        }

        EtfPriceResponse.Body body = parsed.getResponse().getBody();

        if (body == null || body.getItems() == null || body.getItems().getItem() == null) {
            return Optional.empty();
        }

        // likeSrtnCd는 부분 일치라 요청한 코드와 같은지 반드시 확인한다.
        for (EtfPriceResponse.Item item : body.getItems().getItem()) {
            if (shortCode.equals(item.getSrtnCd())) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    /** 응답 파싱만 떼어 둔다. 실제 호출 없이 표기 규칙을 검증할 수 있게 하기 위함이다. */
    static EtfPriceResponse parse(String json) throws IOException {
        return DataGoKrClientSupport.parse(json, EtfPriceResponse.class);
    }

    private ApiException sourceUnavailable(String shortCode, Exception cause) {
        log.error("ETF 시세 호출 실패 srtnCd={}", shortCode, cause);

        return new ApiException(
                ErrorCode.INVALID_SOURCE_PRODUCT,
                "ETF 시세 제공처에 연결하지 못했습니다.",
                cause
        );
    }
}
