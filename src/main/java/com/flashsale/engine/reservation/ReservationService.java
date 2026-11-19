package com.flashsale.engine.reservation;

import com.flashsale.engine.common.exception.LockAcquisitionException;
import com.flashsale.engine.common.exception.MaxQuotaExceededException;
import com.flashsale.engine.common.exception.OutOfStockException;
import com.flashsale.engine.common.exception.ResourceNotFoundException;
import com.flashsale.engine.common.exception.SaleNotActiveException;
import com.flashsale.engine.inventory.FlashSale;
import com.flashsale.engine.inventory.FlashSaleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final FlashSaleRepository flashSaleRepository;
    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Counter reservationsCreatedCounter;
    private final long reservationTtlSeconds;

    public ReservationService(FlashSaleRepository flashSaleRepository,
                              ReservationRepository reservationRepository,
                              RedissonClient redissonClient,
                              TransactionTemplate transactionTemplate,
                              Clock clock,
                              MeterRegistry meterRegistry,
                              @Value("${app.reservation.ttl-seconds:300}") long reservationTtlSeconds) {
        this.flashSaleRepository = flashSaleRepository;
        this.reservationRepository = reservationRepository;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.reservationTtlSeconds = reservationTtlSeconds;
        this.reservationsCreatedCounter = meterRegistry.counter("flashsale.reservations.created");
    }

    public ReservationResponse reserve(UUID saleId, String userId, int quantity) {
        // Step 1: Pre-validation without lock
        FlashSale existingSale = flashSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found: " + saleId));

        Instant now = clock.instant();
        if (now.isBefore(existingSale.getStartsAt()) || now.isAfter(existingSale.getEndsAt())) {
            throw new SaleNotActiveException("Flash sale " + saleId + " is not currently active");
        }

        // Step 2: Acquire Redisson distributed lock
        String lockKey = "lock:flash-sale:" + saleId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Thread interrupted while acquiring lock for flash sale: " + saleId);
        }

        if (!locked) {
            throw new LockAcquisitionException("Could not acquire lock for flash sale: " + saleId);
        }

        // Step 3: Transactional boundary executed while holding the lock
        try {
            Reservation reservation = transactionTemplate.execute(status -> {
                // Fetch FlashSale using pessimistic write lock
                FlashSale sale = flashSaleRepository.findByIdWithPessimisticLock(saleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found: " + saleId));

                // Re-verify sale window under lock
                Instant current = clock.instant();
                if (current.isBefore(sale.getStartsAt()) || current.isAfter(sale.getEndsAt())) {
                    throw new SaleNotActiveException("Flash sale " + saleId + " is not active");
                }

                // Verify user quota
                int existingQty = reservationRepository.getActiveReservedQuantityForUser(saleId, userId);
                if (existingQty + quantity > sale.getMaxPerUser()) {
                    throw new MaxQuotaExceededException(
                            String.format("User %s requested %d units, but already has %d active reservations. Max allowed: %d",
                                    userId, quantity, existingQty, sale.getMaxPerUser())
                    );
                }

                // Verify available stock
                if (sale.getAvailableStock() < quantity) {
                    throw new OutOfStockException(
                            String.format("Insufficient stock for sale %s. Available: %d, Requested: %d",
                                    saleId, sale.getAvailableStock(), quantity)
                    );
                }

                // Decrement available stock, increment reserved stock
                sale.setAvailableStock(sale.getAvailableStock() - quantity);
                sale.setReservedStock(sale.getReservedStock() + quantity);
                flashSaleRepository.save(sale);

                // Create Reservation
                Instant expiresAt = current.plusSeconds(reservationTtlSeconds);
                Reservation newReservation = new Reservation(
                        UUID.randomUUID(),
                        saleId,
                        userId,
                        quantity,
                        ReservationStatus.RESERVED,
                        expiresAt,
                        current,
                        current
                );
                return reservationRepository.save(newReservation);
            });

            // Step 4: Increment Micrometer counter after successful commit
            reservationsCreatedCounter.increment();

            // Step 5: Return 201 response DTO
            return ReservationResponse.from(reservation);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
