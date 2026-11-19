package com.flashsale.engine.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdWithPessimisticLock(@Param("id") UUID id);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM Reservation r " +
           "WHERE r.flashSaleId = :saleId AND r.userId = :userId AND r.status IN ('RESERVED', 'CONFIRMED')")
    int getActiveReservedQuantityForUser(@Param("saleId") UUID saleId, @Param("userId") String userId);

    @Query(value = "SELECT * FROM reservations WHERE status = 'RESERVED' AND expires_at < :now " +
                   "ORDER BY expires_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Reservation> findExpiredReservationsForUpdate(@Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByFlashSaleId(UUID flashSaleId);
}
