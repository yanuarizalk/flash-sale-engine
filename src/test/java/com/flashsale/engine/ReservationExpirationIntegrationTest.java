package com.flashsale.engine;

import com.flashsale.engine.inventory.FlashSale;
import com.flashsale.engine.inventory.FlashSaleRepository;
import com.flashsale.engine.inventory.Product;
import com.flashsale.engine.inventory.ProductRepository;
import com.flashsale.engine.reservation.Reservation;
import com.flashsale.engine.reservation.ReservationExpirationWorker;
import com.flashsale.engine.reservation.ReservationRepository;
import com.flashsale.engine.reservation.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationExpirationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationExpirationWorker expirationWorker;

    @Test
    @DisplayName("Expired reservations are transitioned to EXPIRED and inventory is atomically restored")
    void testReservationExpirationRestoresStock() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "EXPIRY-SKU-" + productId, "Expiry Test Item", 5000L, Instant.now());
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

        UUID reservationId = UUID.randomUUID();
        // Reservation that expired 2 seconds ago
        Reservation reservation = new Reservation(
                reservationId,
                saleId,
                "expiry-user-1",
                1,
                ReservationStatus.RESERVED,
                now.minus(2, ChronoUnit.SECONDS),
                now.minus(5, ChronoUnit.SECONDS),
                now.minus(5, ChronoUnit.SECONDS)
        );
        reservationRepository.save(reservation);

        // Run expiration worker
        int processed = expirationWorker.processExpiredReservations();
        assertThat(processed).isGreaterThanOrEqualTo(1);

        // Verify reservation status is EXPIRED
        Reservation expiredRes = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(expiredRes.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        // Verify stock is restored
        FlashSale updatedSale = flashSaleRepository.findById(saleId).orElseThrow();
        assertThat(updatedSale.getAvailableStock()).isEqualTo(10);
        assertThat(updatedSale.getReservedStock()).isEqualTo(0);
    }
}
