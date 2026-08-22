package org.firstfolio.common.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackAlertClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendPostsMessageAsJsonToWebhook() throws IOException {
        BlockingQueue<String> receivedBodies = new ArrayBlockingQueue<>(1);
        BlockingQueue<String> receivedContentTypes = new ArrayBlockingQueue<>(1);

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/webhook", exchange -> {
            try (InputStream body = exchange.getRequestBody()) {
                receivedBodies.offer(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            receivedContentTypes.offer(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        String webhookUrl = "http://localhost:" + server.getAddress().getPort() + "/webhook";
        SlackAlertClient client = new SlackAlertClient(webhookUrl);

        client.send("오류 발생: \"결제 실패\"");

        String body = poll(receivedBodies);
        String contentType = poll(receivedContentTypes);

        assertEquals("application/json", contentType);
        assertEquals("{\"text\":\"오류 발생: \\\"결제 실패\\\"\"}", body);
    }

    @Test
    void sendDoesNothingWhenWebhookUrlIsBlank() {
        SlackAlertClient client = new SlackAlertClient("");

        assertDoesNotThrow(() -> client.send("알림 대상 없음"));
    }

    @Test
    void sendDoesNotThrowWhenWebhookIsUnreachable() {
        // 포트 0은 실제로 열리지 않으므로 연결 실패를 강제한다.
        SlackAlertClient client = new SlackAlertClient("http://localhost:0/webhook");

        assertDoesNotThrow(() -> client.send("연결 실패 상황"));
    }

    private static String poll(BlockingQueue<String> queue) {
        try {
            String value = queue.poll(2, TimeUnit.SECONDS);
            assertTrue(value != null, "webhook 서버가 요청을 받지 못했습니다.");
            return value;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
