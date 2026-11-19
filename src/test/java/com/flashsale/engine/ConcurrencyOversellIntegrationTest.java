package com.flashsale.engine;

import com.flashsale.engine.inventory.FlashSale;
import com.flashsale.engine.inventory.FlashSaleRepository;
import com.flashsale.engine.inventory.Product;
import com.flashsale.engine.inventory.ProductRepository;
import com.flashsale.engine.reservation.ReservationRepository;
import com.flashsale.engine.reservation.ReservationRequest;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyOversellIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("200 concurrent virtual threads reserving 100 units total with 50 stock: exactly 50 succeed (201), 150 conflict (409), zero oversell")
    void testZeroOversellUnderConcurrency() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = new Product(
                productId,
                "CONCURRENCY-SKU-" + productId,
                "Concurrency Test Product",
                19999L,
                Instant.now()
        );
        productRepository.save(product);

        UUID saleId = UUID.randomUUID();
        Instant now = Instant.now();
        FlashSale flashSale = new FlashSale(
                saleId,
                product,
                50,
                50,
                0,
                1,
                now.minus(1, ChronoUnit.HOURS),
                now.plus(24, ChronoUnit.HOURS),
                now
        );
        flashSaleRepository.save(flashSale);

        int totalThreads = 200;
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Integer> statusCodes = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= totalThreads; i++) {
                final String userId = "user-" + i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-User-Id", userId);
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        ReservationRequest request = new ReservationRequest(1);
                        HttpEntity<ReservationRequest> entity = new HttpEntity<>(request, headers);

                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/v1/flash-sales/" + saleId + "/reservations",
                                HttpMethod.POST,
                                entity,
                                String.class
                        );

                        int statusCode = response.getStatusCode().value();
                        statusCodes.add(statusCode);
                        if (statusCode == HttpStatus.CREATED.value()) {
                            createdCount.incrementAndGet();
                        } else if (statusCode == HttpStatus.CONFLICT.value()) {
                            conflictCount.incrementAndGet();
                        } else {
                            otherCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        otherCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(15, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(45, TimeUnit.SECONDS)).isTrue();
        }

        // Verify HTTP responses
        assertThat(createdCount.get()).isEqualTo(50);
        assertThat(conflictCount.get()).isEqualTo(150);
        assertThat(otherCount.get()).isEqualTo(0);

        // Verify database state
        FlashSale updatedSale = flashSaleRepository.findById(saleId).orElseThrow();
        assertThat(updatedSale.getAvailableStock()).isEqualTo(0);
        assertThat(updatedSale.getReservedStock()).isEqualTo(50);

        long reservationsCount = reservationRepository.countByFlashSaleId(saleId);
        assertThat(reservationsCount).isEqualTo(50);
    }
}
