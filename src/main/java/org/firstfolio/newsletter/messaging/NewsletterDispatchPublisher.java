package org.firstfolio.newsletter.messaging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 뉴스레터 발송 작업을 RabbitMQ에 발행한다.
 *
 * <p>익스체인지·큐는 여기서 선언하지 않는다({@link org.firstfolio.config.RabbitMqConfig}
 * 참고). 발행 자체가 실패하면 예외를 그대로 던진다 — 발행이 안 됐는데 뉴스레터 상태만
 * PUBLISHED로 남으면 "게시했지만 아무도 못 받은" 상태가 되므로, 호출부의 트랜잭션이
 * 함께 롤백되게 둔다.</p>
 */
@Component
public class NewsletterDispatchPublisher {

    private static final Logger log = LogManager.getLogger(NewsletterDispatchPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public NewsletterDispatchPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.newsletter.exchange}") String exchange,
            @Value("${rabbitmq.newsletter.routing-key}") String routingKey
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(NewsletterDispatchMessage message) {
        if (message.recipients().isEmpty()) {
            log.warn("뉴스레터 발송 대상이 없어 큐에 발행하지 않습니다 newsletterId={}", message.newsletterId());
            return;
        }

        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info(
                "뉴스레터 발송 메시지 발행 newsletterId={} 수신자수={}",
                message.newsletterId(),
                message.recipients().size()
        );
    }
}
