package com.flashsale.engine.messaging;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID orderId,
        UUID reservationId,
        UUID flashSaleId,
        String userId,
        int quantity,
        long totalAmountCents,
        String paymentReference,
        Instant confirmedAt
) {
}
