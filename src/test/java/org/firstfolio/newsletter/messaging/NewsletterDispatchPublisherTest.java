package org.firstfolio.newsletter.messaging;

import org.firstfolio.newsletter.dto.response.FinancialWordResponse;
import org.firstfolio.newsletter.dto.response.NewsletterIssueResponse;
import org.firstfolio.newsletter.dto.response.NewsletterStatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NewsletterDispatchPublisherTest {

    private static final String EXCHANGE = "newsletter.exchange";
    private static final String ROUTING_KEY = "newsletter.dispatch";

    private RabbitTemplate rabbitTemplate;
    private NewsletterDispatchPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new NewsletterDispatchPublisher(rabbitTemplate, EXCHANGE, ROUTING_KEY);
    }

    @Test
    void publishesMessageToConfiguredExchangeAndRoutingKey() {
        NewsletterDispatchMessage message = message(List.of("a@x.com"));

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), eq(message));
    }

    @Test
    void skipsPublishWhenNoRecipients() {
        NewsletterDispatchMessage message = message(List.of());

        publisher.publish(message);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(message));
    }

    private static NewsletterDispatchMessage message(List<String> recipients) {
        return new NewsletterDispatchMessage(
                1L,
                "대제목",
                List.of(new FinancialWordResponse("term", "definition")),
                List.of(new NewsletterIssueResponse("title", "summary", "term", List.of())),
                List.of(new NewsletterStatResponse("label", "value")),
                recipients
        );
    }
}
