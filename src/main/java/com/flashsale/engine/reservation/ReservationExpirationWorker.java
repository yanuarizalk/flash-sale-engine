package com.flashsale.engine.reservation;

import com.flashsale.engine.inventory.FlashSaleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class ReservationExpirationWorker {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationWorker.class);
    private static final int BATCH_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Counter reservationsExpiredCounter;

    public ReservationExpirationWorker(ReservationRepository reservationRepository,
                                       FlashSaleRepository flashSaleRepository,
                                       TransactionTemplate transactionTemplate,
                                       Clock clock,
                                       MeterRegistry meterRegistry) {
        this.reservationRepository = reservationRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.reservationsExpiredCounter = meterRegistry.counter("flashsale.reservations.expired");
    }

    @Scheduled(fixedDelayString = "${app.reservation.expiration-poll-ms:5000}")
    public void scheduleExpirationCheck() {
        processExpiredReservations();
    }

    public int processExpiredReservations() {
        Instant now = clock.instant();

        Integer expiredCount = transactionTemplate.execute(status -> {
            List<Reservation> expiredReservations = reservationRepository.findExpiredReservationsForUpdate(now, BATCH_SIZE);
            if (expiredReservations.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (Reservation res : expiredReservations) {
                res.setStatus(ReservationStatus.EXPIRED);
                res.setUpdatedAt(now);
                reservationRepository.save(res);

                flashSaleRepository.restoreStock(res.getFlashSaleId(), res.getQuantity());
                reservationsExpiredCounter.increment();
                count++;
                log.info("Expired reservation {} for sale {}, restored quantity {}",
                        res.getId(), res.getFlashSaleId(), res.getQuantity());
            }
            return count;
        });

        return expiredCount != null ? expiredCount : 0;
    }
}
