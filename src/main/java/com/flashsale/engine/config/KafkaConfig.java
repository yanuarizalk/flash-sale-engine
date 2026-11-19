package com.flashsale.engine.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.order-confirmed:flash-sale.orders.confirmed}")
    private String orderConfirmedTopic;

    @Value("${app.kafka.topics.order-confirmed-dlt:flash-sale.orders.confirmed-dlt}")
    private String orderConfirmedDltTopic;

    @Bean
    public NewTopic orderConfirmedTopic() {
        return TopicBuilder.name(orderConfirmedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderConfirmedDltTopic() {
        return TopicBuilder.name(orderConfirmedDltTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
