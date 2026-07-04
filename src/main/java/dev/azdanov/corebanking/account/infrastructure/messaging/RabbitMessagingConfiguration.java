package dev.azdanov.corebanking.account.infrastructure.messaging;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessagingConfiguration {

    @Bean
    TopicExchange accountEventsExchange(
        @Value("${corebanking.messaging.exchange}") String exchangeName
    ) {
        return ExchangeBuilder
            .topicExchange(exchangeName)
            .build();
    }
}
