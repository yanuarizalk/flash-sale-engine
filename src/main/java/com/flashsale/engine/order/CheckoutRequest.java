package com.flashsale.engine.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutRequest(
        @NotNull(message = "Reservation ID is required")
        UUID reservationId,

        @NotBlank(message = "Payment token is required")
        String paymentToken
) {
}
