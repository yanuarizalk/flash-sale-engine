package com.flashsale.engine.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "total_amount_cents", nullable = false)
    private Long totalAmountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "payment_reference", nullable = false, length = 128)
    private String paymentReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Order() {
    }

    public Order(UUID id, UUID reservationId, String userId, Long totalAmountCents,
                 OrderStatus status, String paymentReference, Instant createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.userId = userId;
        this.totalAmountCents = totalAmountCents;
        this.status = status;
        this.paymentReference = paymentReference;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getTotalAmountCents() {
        return totalAmountCents;
    }

    public void setTotalAmountCents(Long totalAmountCents) {
        this.totalAmountCents = totalAmountCents;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
