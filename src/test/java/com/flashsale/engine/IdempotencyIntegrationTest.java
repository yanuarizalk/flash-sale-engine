package com.flashsale.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.engine.inventory.FlashSale;
import com.flashsale.engine.inventory.FlashSaleRepository;
import com.flashsale.engine.inventory.Product;
import com.flashsale.engine.inventory.ProductRepository;
import com.flashsale.engine.messaging.OutboxEventRepository;
import com.flashsale.engine.order.CheckoutRequest;
import com.flashsale.engine.order.Order;
import com.flashsale.engine.order.OrderRepository;
import com.flashsale.engine.reservation.Reservation;
import com.flashsale.engine.reservation.ReservationRepository;
import com.flashsale.engine.reservation.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("10 concurrent identical checkout requests with same Idempotency-Key result in 1 Order, 1 OutboxEvent, and identical 201 responses")
    void testConcurrentIdempotentCheckout() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "IDEMP-SKU-" + productId, "Idempotency Item", 25000L, Instant.now());
        productRepository.save(product);

        UUID saleId = UUID.randomUUID();
        Instant now = Instant.now();
        FlashSale sale = new FlashSale(
                saleId,
                product,
                10,
                9,
                1,
                2,
                now.minus(1, ChronoUnit.HOURS),
                now.plus(24, ChronoUnit.HOURS),
                now
        );
        flashSaleRepository.save(sale);

        String userId = "idemp-user-1";
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = new Reservation(
                reservationId,
                saleId,
                userId,
                1,
                ReservationStatus.RESERVED,
                now.plus(300, ChronoUnit.SECONDS),
                now,
                now
        );
        reservationRepository.save(reservation);

        String idempotencyKey = "idemp-key-" + UUID.randomUUID();
        String paymentToken = "tok_valid_" + UUID.randomUUID();

        int concurrentCallers = 10;
        CountDownLatch readyLatch = new CountDownLatch(concurrentCallers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentCallers);

        ConcurrentLinkedQueue<ResponseEntity<String>> responses = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrentCallers; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-User-Id", userId);
                        headers.set("Idempotency-Key", idempotencyKey);
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        CheckoutRequest request = new CheckoutRequest(reservationId, paymentToken);
                        HttpEntity<CheckoutRequest> entity = new HttpEntity<>(request, headers);

                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/v1/orders",
                                HttpMethod.POST,
                                entity,
                                String.class
                        );
                        responses.add(response);
                    } catch (Exception e) {
                        // ignore here, handled in assertions
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(responses).hasSize(concurrentCallers);

        String expectedOrderId = null;
        for (ResponseEntity<String> response : responses) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode bodyJson = objectMapper.readTree(response.getBody());
            String orderId = bodyJson.get("orderId").asText();
            assertThat(orderId).isNotBlank();

            if (expectedOrderId == null) {
                expectedOrderId = orderId;
            } else {
                assertThat(orderId).isEqualTo(expectedOrderId);
            }
        }

        // Assert exactly 1 Order row exists
        Optional<Order> orderOpt = orderRepository.findByReservationId(reservationId);
        assertThat(orderOpt).isPresent();
        assertThat(orderOpt.get().getId().toString()).isEqualTo(expectedOrderId);

        // Assert exactly 1 OutboxEvent exists for this order
        UUID orderUuid = UUID.fromString(expectedOrderId);
        long outboxCount = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(orderUuid))
                .count();
        assertThat(outboxCount).isEqualTo(1L);

        // Assert Reservation is CONFIRMED
        Reservation updatedRes = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(updatedRes.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }
}
