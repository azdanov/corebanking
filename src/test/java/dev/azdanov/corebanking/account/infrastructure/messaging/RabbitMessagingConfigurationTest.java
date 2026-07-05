package dev.azdanov.corebanking.account.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMessagingConfigurationTest {

    @Test
    void shouldCreateTopicExchange() {
        var configuration = new RabbitMessagingConfiguration();

        var exchange = configuration.accountEventsExchange("corebanking.account.events");

        assertThat(exchange).isInstanceOf(TopicExchange.class);
        assertThat(exchange.getName()).isEqualTo("corebanking.account.events");
        assertThat(exchange.isDurable()).isTrue();
    }
}
