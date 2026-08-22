package org.firstfolio.common.alert;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Slack Incoming Webhook으로 알림을 보낸다.
 *
 * <p>알림 전송 실패가 호출부 로직을 막으면 안 되므로 예외를 밖으로 던지지 않고
 * 로그만 남긴다.</p>
 */
@Component
public class SlackAlertClient {

    private static final Logger log = LogManager.getLogger(SlackAlertClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final String webhookUrl;

    public SlackAlertClient(@Value("${slack.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void send(String message) {
        if (webhookUrl.isBlank()) {
            log.debug("Slack webhook 미설정, 알림 건너뜀: {}", message);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"text\":\"" + escape(message) + "\"}",
                    StandardCharsets.UTF_8
                ))
                .build();

            HttpResponse<Void> response =
                httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() != 200) {
                log.warn("Slack 알림 응답 오류 status={}", response.statusCode());
            }
        } catch (Exception exception) {
            log.warn("Slack 알림 전송 실패", exception);
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
    }
}
