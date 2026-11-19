package com.flashsale.engine.reservation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flash-sales")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{saleId}/reservations")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID saleId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ReservationRequest request) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id header cannot be blank");
        }

        ReservationResponse response = reservationService.reserve(saleId, userId.trim(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
