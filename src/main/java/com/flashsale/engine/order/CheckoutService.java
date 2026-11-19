package com.flashsale.engine.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.engine.common.exception.IdempotencyKeyConflictException;
import com.flashsale.engine.common.exception.OutOfStockException;
import com.flashsale.engine.common.exception.PaymentFailedException;
import com.flashsale.engine.common.exception.ReservationExpiredException;
import com.flashsale.engine.common.exception.ResourceNotFoundException;
import com.flashsale.engine.common.exception.UserForbiddenException;
import com.flashsale.engine.inventory.FlashSale;
import com.flashsale.engine.inventory.FlashSaleRepository;
import com.flashsale.engine.messaging.OrderConfirmedEvent;
import com.flashsale.engine.messaging.OutboxEvent;
import com.flashsale.engine.messaging.OutboxEventRepository;
import com.flashsale.engine.messaging.OutboxStatus;
import com.flashsale.engine.reservation.Reservation;
import com.flashsale.engine.reservation.ReservationRepository;
import com.flashsale.engine.reservation.ReservationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ReservationRepository reservationRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter ordersConfirmedCounter;

    public CheckoutService(ReservationRepository reservationRepository,
                           FlashSaleRepository flashSaleRepository,
                           OrderRepository orderRepository,
                           IdempotencyKeyRepository idempotencyKeyRepository,
                           OutboxEventRepository outboxEventRepository,
                           TransactionTemplate transactionTemplate,
                           ObjectMapper objectMapper,
                           Clock clock,
                           MeterRegistry meterRegistry) {
        this.reservationRepository = reservationRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.orderRepository = orderRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ordersConfirmedCounter = meterRegistry.counter("flashsale.orders.confirmed");
    }

    public CheckoutResult checkout(String idempotencyKey, String userId, UUID reservationId, String paymentToken) {
        String requestHash = computeSha256(userId + ":" + reservationId + ":" + paymentToken);

        // Step 2: Check idempotency_keys table
        Optional<IdempotencyKey> existingKeyOpt = idempotencyKeyRepository.findById(idempotencyKey);
        if (existingKeyOpt.isPresent()) {
            IdempotencyKey existingKey = existingKeyOpt.get();
            if (!existingKey.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException("Idempotency key '" + idempotencyKey + "' was already used with a different request payload");
            }
            try {
                OrderResponse cachedResponse = objectMapper.readValue(existingKey.getResponseBody(), OrderResponse.class);
                return new CheckoutResult(cachedResponse, existingKey.getResponseStatus(), true);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached idempotency response", e);
                throw new IllegalStateException("Corrupt cached idempotency response", e);
            }
        }

        // Step 3: Transactional boundary
        try {
            CheckoutResult result = transactionTemplate.execute(status -> {
                // Lock and load Reservation
                Reservation reservation = reservationRepository.findByIdWithPessimisticLock(reservationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

                // Verify user ownership
                if (!reservation.getUserId().equals(userId)) {
                    throw new UserForbiddenException("User " + userId + " does not own reservation " + reservationId);
                }

                // Verify reservation status
                if (reservation.getStatus() != ReservationStatus.RESERVED) {
                    throw new OutOfStockException("Reservation " + reservationId + " is in status " + reservation.getStatus() + ", not RESERVED");
                }

                // Verify expiration
                Instant now = clock.instant();
                if (!reservation.getExpiresAt().isAfter(now)) {
                    throw new ReservationExpiredException("Reservation " + reservationId + " has expired at " + reservation.getExpiresAt());
                }

                // Simulate payment processing
                if (paymentToken.startsWith("tok_fail")) {
                    throw new PaymentFailedException("Payment processing failed for token: " + paymentToken);
                }
                String paymentReference = "pay_" + UUID.randomUUID();

                // Update reservation to CONFIRMED
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservation.setUpdatedAt(now);
                reservationRepository.save(reservation);

                // Fetch flash sale with product to calculate price
                FlashSale flashSale = flashSaleRepository.findByIdWithProduct(reservation.getFlashSaleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found: " + reservation.getFlashSaleId()));

                // Decrement reserved stock on flash sale
                flashSaleRepository.releaseReservedStock(flashSale.getId(), reservation.getQuantity());

                // Insert Order record
                long totalAmountCents = flashSale.getProduct().getPriceCents() * reservation.getQuantity();
                UUID orderId = UUID.randomUUID();
                Order order = new Order(
                        orderId,
                        reservationId,
                        userId,
                        totalAmountCents,
                        OrderStatus.PAID,
                        paymentReference,
                        now
                );
                orderRepository.save(order);

                OrderResponse response = OrderResponse.from(order);
                String responseJson;
                try {
                    responseJson = objectMapper.writeValueAsString(response);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Failed to serialize order response", e);
                }

                // Insert OutboxEvent
                OrderConfirmedEvent confirmedEvent = new OrderConfirmedEvent(
                        orderId,
                        reservationId,
                        flashSale.getId(),
                        userId,
                        reservation.getQuantity(),
                        totalAmountCents,
                        paymentReference,
                        now
                );
                String outboxPayload;
                try {
                    outboxPayload = objectMapper.writeValueAsString(confirmedEvent);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Failed to serialize outbox event payload", e);
                }

                OutboxEvent outboxEvent = new OutboxEvent(
                        UUID.randomUUID(),
                        "ORDER",
                        orderId,
                        "ORDER_CONFIRMED",
                        outboxPayload,
                        OutboxStatus.PENDING,
                        0,
                        now,
                        null
                );
                outboxEventRepository.save(outboxEvent);

                // Insert IdempotencyKey record
                Instant idempotencyExpiresAt = now.plus(24, ChronoUnit.HOURS);
                IdempotencyKey idempKey = new IdempotencyKey(
                        idempotencyKey,
                        userId,
                        requestHash,
                        201,
                        responseJson,
                        now,
                        idempotencyExpiresAt
                );
                idempotencyKeyRepository.save(idempKey);

                return new CheckoutResult(response, 201, false);
            });

            // Increment Micrometer counter
            ordersConfirmedCounter.increment();
            return result;

        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent request detected with idempotency key: {}", idempotencyKey);
            // Re-read committed idempotency key
            IdempotencyKey committedKey = idempotencyKeyRepository.findById(idempotencyKey)
                    .orElseThrow(() -> ex);

            if (!committedKey.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException("Idempotency key '" + idempotencyKey + "' was already used with a different request payload");
            }

            try {
                OrderResponse cachedResponse = objectMapper.readValue(committedKey.getResponseBody(), OrderResponse.class);
                return new CheckoutResult(cachedResponse, committedKey.getResponseStatus(), true);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize committed idempotency response", e);
                throw new IllegalStateException("Corrupt committed idempotency response", e);
            }
        }
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
