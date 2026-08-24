package org.firstfolio.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 뉴스레터 발송 큐(RabbitMQ) 연결을 선언한다.
 *
 * <p>익스체인지·큐·바인딩은 여기서 선언하지 않는다. {@code CachingConnectionFactory}는
 * 실제 사용 시점까지 연결하지 않지만, 큐 선언용 {@code RabbitAdmin}을 추가하면 컨텍스트
 * 로딩 시점에 브로커 연결을 시도해 테스트(jdbcTest 등 {@code RootConfig}를 통째로 올리는
 * 테스트)가 브로커 없이는 실패하게 된다. 큐 선언은 소비자(Celery 워커)가 기동 시 하는
 * 것이 Celery 관례이기도 해서, BE는 발행만 하고 선언은 워커에 맡긴다.</p>
 */
@Configuration
public class RabbitMqConfig {

    @Bean(destroyMethod = "destroy")
    public ConnectionFactory rabbitConnectionFactory(
            @Value("${rabbitmq.host}") String host,
            @Value("${rabbitmq.port}") int port,
            @Value("${rabbitmq.username}") String username,
            @Value("${rabbitmq.password}") String password
    ) {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
