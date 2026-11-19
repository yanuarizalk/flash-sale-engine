package com.flashsale.engine.order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID reservationId,
        String userId,
        long totalAmountCents,
        OrderStatus status,
        String paymentReference,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getReservationId(),
                order.getUserId(),
                order.getTotalAmountCents(),
                order.getStatus(),
                order.getPaymentReference(),
                order.getCreatedAt()
        );
    }
}
