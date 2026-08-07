package org.firstfolio.simulation.client.toss;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 토스증권 Open API 클라이언트.
 *
 * <p><b>토큰은 클라이언트당 하나만 유효하다.</b> 새로 발급받으면 이전 토큰이 즉시 무효화되므로,
 * 스레드마다 발급받으면 서로를 무효화시켜 간헐적 401이 난다. 하나를 캐시해 함께 쓰고,
 * 갱신은 한 번에 하나만 수행한다.</p>
 *
 * <p>Rate Limit은 그룹별로 걸린다 (AUTH 5/s, MARKET_DATA 10/s). {@code /api/v1/prices}는
 * 한 번에 200종목까지 받아서 10종목은 요청 1건이면 된다.</p>
 */
@Component
public class TossInvestClient {

    private static final Logger log = LogManager.getLogger(TossInvestClient.class);

    /** 실제 만료보다 이만큼 먼저 갱신한다. */
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    /** 한 요청에 넣을 수 있는 종목 수. */
    private static final int MAX_SYMBOLS_PER_REQUEST = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Object tokenLock = new Object();

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public TossInvestClient(
            @Value("${tossinvest.base-url}") String baseUrl,
            @Value("${tossinvest.client-id:}") String clientId,
            @Value("${tossinvest.client-secret:}") String clientSecret
    ) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * 여러 종목의 현재가를 한 번에 가져온다.
     *
     * <p>존재하지 않는 종목코드는 오류 없이 결과에서 빠지므로, 호출부가 요청한 종목과
     * 대조해 누락을 확인해야 한다.</p>
     */
    public List<TossPricesResponse.Item> fetchPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        if (symbols.size() > MAX_SYMBOLS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "한 번에 조회할 수 있는 종목은 " + MAX_SYMBOLS_PER_REQUEST + "개까지입니다."
            );
        }

        String query = URLEncoder.encode(String.join(",", symbols), StandardCharsets.UTF_8);
        String body = get("/api/v1/prices?symbols=" + query);

        TossPricesResponse parsed;

        try {
            parsed = parsePrices(body);
        } catch (Exception exception) {
            throw unavailable("시세 응답을 해석하지 못했습니다.", exception);
        }

        List<TossPricesResponse.Item> result =
                parsed.getResult() == null ? List.of() : parsed.getResult();

        if (result.size() != symbols.size()) {
            log.warn(
                    "시세 응답 종목 수가 요청과 다릅니다 요청={} 응답={} (없는 종목코드는 조용히 빠집니다)",
                    symbols.size(),
                    result.size()
            );
        }

        return result;
    }

    /** 응답 파싱만 떼어 둔다. 실제 호출 없이 표기 규칙을 검증할 수 있게 하기 위함이다. */
    static TossPricesResponse parsePrices(String json) throws IOException {
        return MAPPER.readValue(json, TossPricesResponse.class);
    }

    private String get(String path) {
        String token = accessToken();
        HttpResponse<byte[]> response = send(path, token);

        // 서버에서 토큰이 폐기됐을 수 있다. 한 번만 다시 받아 재시도한다.
        if (response.statusCode() == 401) {
            log.warn("토스 시세 요청이 401을 받아 토큰을 다시 발급합니다.");
            invalidateToken();
            response = send(path, accessToken());
        }

        if (response.statusCode() != 200) {
            log.error("토스 시세 응답 오류 status={}", response.statusCode());
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "주식 시세 제공처 응답이 올바르지 않습니다."
            );
        }

        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> send(String path, String token) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("주식 시세 제공처에 연결하지 못했습니다.", exception);
        } catch (Exception exception) {
            throw unavailable("주식 시세 제공처에 연결하지 못했습니다.", exception);
        }
    }

    private void invalidateToken() {
        synchronized (tokenLock) {
            cachedToken = null;
            tokenExpiresAt = Instant.EPOCH;
        }
    }

    /**
     * 유효한 토큰을 돌려준다. 만료가 가까우면 갱신하되, 동시에 여러 번 발급하지 않는다.
     */
    private String accessToken() {
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }

            if (clientId.isBlank() || clientSecret.isBlank()) {
                throw new ApiException(
                        ErrorCode.INVALID_SOURCE_PRODUCT,
                        "토스증권 인증 정보가 설정되지 않았습니다 (TOSSINVEST_CLIENT_ID/SECRET)."
                );
            }

            TossToken issued = issueToken();

            cachedToken = issued.accessToken;
            tokenExpiresAt = issued.expiresAt;

            log.info("토스 토큰 발급 완료 만료={}", tokenExpiresAt);

            return cachedToken;
        }
    }

    private TossToken issueToken() {
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/oauth2/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("토스증권 인증에 실패했습니다.", exception);
        } catch (Exception exception) {
            throw unavailable("토스증권 인증에 실패했습니다.", exception);
        }

        if (response.statusCode() != 200) {
            // 자격 증명이 본문에 들어가므로 요청 내용은 로그에 남기지 않는다.
            log.error("토스 토큰 발급 실패 status={}", response.statusCode());
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "토스증권 인증에 실패했습니다."
            );
        }

        try {
            TokenResponse parsed = MAPPER.readValue(
                    new String(response.body(), StandardCharsets.UTF_8),
                    TokenResponse.class
            );

            if (parsed.accessToken == null || parsed.accessToken.isBlank()) {
                throw new ApiException(
                        ErrorCode.INVALID_SOURCE_PRODUCT,
                        "토스증권 인증 응답에 토큰이 없습니다."
                );
            }

            long ttl = parsed.expiresIn == null ? 0L : parsed.expiresIn;
            // 만료값이 이상해도 최소 30초는 재사용해 발급 폭주를 막는다.
            Duration lifetime = Duration.ofSeconds(Math.max(ttl - REFRESH_MARGIN.toSeconds(), 30));

            return new TossToken(parsed.accessToken, Instant.now().plus(lifetime));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable("토스증권 인증 응답을 해석하지 못했습니다.", exception);
        }
    }

    private ApiException unavailable(String message, Exception cause) {
        log.error(message, cause);

        return new ApiException(ErrorCode.INVALID_SOURCE_PRODUCT, message, cause);
    }

    private static final class TossToken {

        private final String accessToken;
        private final Instant expiresAt;

        private TossToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static final class TokenResponse {

        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        String accessToken;

        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        Long expiresIn;
    }
}
