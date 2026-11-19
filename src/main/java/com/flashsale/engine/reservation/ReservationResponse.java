package com.flashsale.engine.reservation;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID flashSaleId,
        String userId,
        int quantity,
        ReservationStatus status,
        Instant expiresAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getFlashSaleId(),
                reservation.getUserId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt()
        );
    }
}
