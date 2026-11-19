package com.flashsale.engine.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class OrderConfirmedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmedKafkaConsumer.class);

    private final Counter dltMessagesCounter;

    public OrderConfirmedKafkaConsumer(MeterRegistry meterRegistry) {
        this.dltMessagesCounter = meterRegistry.counter("flashsale.kafka.dlt.messages");
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "${app.kafka.topics.order-confirmed:flash-sale.orders.confirmed}",
            groupId = "${spring.kafka.consumer.group-id:flash-sale-order-service}"
    )
    public void consumeOrderConfirmed(ConsumerRecord<String, String> record) {
        log.info("Received OrderConfirmed message: key={}, partition={}, offset={}, payload={}",
                record.key(), record.partition(), record.offset(), record.value());

        // Simulate poison pill check for testing
        if (record.value() != null && record.value().contains("\"poison_pill\":true")) {
            log.error("Poison pill detected for message key {}. Throwing exception to trigger retry and DLT routing.", record.key());
            throw new RuntimeException("Simulated processing failure for poison pill order: " + record.key());
        }

        // Process notification / receipt generation simulation
        log.info("Successfully processed order confirmation for order {}", record.key());
    }

    @DltHandler
    public void handleDltMessage(ConsumerRecord<String, String> record,
                                 @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                 @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String errorMessage) {
        dltMessagesCounter.increment();
        log.error("DEAD LETTER TOPIC: Message routed to DLT topic '{}'. Key: '{}', Payload: '{}', Reason: '{}'",
                topic, record.key(), record.value(), errorMessage);
    }
}
