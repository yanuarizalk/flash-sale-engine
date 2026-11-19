package com.flashsale.engine.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutService checkoutService;

    public OrderController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id header cannot be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header cannot be blank");
        }

        CheckoutResult result = checkoutService.checkout(
                idempotencyKey.trim(),
                userId.trim(),
                request.reservationId(),
                request.paymentToken()
        );

        HttpHeaders headers = new HttpHeaders();
        if (result.isReplayed()) {
            headers.set("X-Idempotent-Replayed", "true");
        }

        return ResponseEntity.status(result.status())
                .headers(headers)
                .body(result.response());
    }
}
