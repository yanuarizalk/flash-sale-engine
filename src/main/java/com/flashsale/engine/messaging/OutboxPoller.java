package com.flashsale.engine.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final String topic;

    public OutboxPoller(OutboxEventRepository outboxEventRepository,
                        KafkaTemplate<String, String> kafkaTemplate,
                        TransactionTemplate transactionTemplate,
                        Clock clock,
                        @Value("${app.kafka.topics.order-confirmed:flash-sale.orders.confirmed}") String topic) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.topic = topic;
    }

    @Scheduled(fixedDelay = 1000)
    public void scheduleOutboxPublish() {
        pollAndPublish();
    }

    public int pollAndPublish() {
        Integer publishedCount = transactionTemplate.execute(status -> {
            List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForUpdate(BATCH_SIZE);
            if (pendingEvents.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (OutboxEvent event : pendingEvents) {
                try {
                    kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
                            .get(5, TimeUnit.SECONDS);

                    event.setStatus(OutboxStatus.PUBLISHED);
                    event.setPublishedAt(clock.instant());
                    outboxEventRepository.save(event);
                    count++;
                    log.debug("Successfully published outbox event {} to topic {}", event.getId(), topic);
                } catch (Exception e) {
                    log.warn("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                    event.setRetryCount(event.getRetryCount() + 1);
                    if (event.getRetryCount() >= 5) {
                        event.setStatus(OutboxStatus.FAILED);
                        log.error("Outbox event {} exceeded max retries (5), marked as FAILED", event.getId());
                    }
                    outboxEventRepository.save(event);
                }
            }
            return count;
        });

        return publishedCount != null ? publishedCount : 0;
    }
}
