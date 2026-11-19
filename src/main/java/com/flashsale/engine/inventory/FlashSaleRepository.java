package com.flashsale.engine.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlashSaleRepository extends JpaRepository<FlashSale, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fs FROM FlashSale fs WHERE fs.id = :id")
    Optional<FlashSale> findByIdWithPessimisticLock(@Param("id") UUID id);

    @Query("SELECT fs FROM FlashSale fs JOIN FETCH fs.product WHERE fs.id = :id")
    Optional<FlashSale> findByIdWithProduct(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FlashSale fs SET fs.availableStock = fs.availableStock + :qty, fs.reservedStock = fs.reservedStock - :qty WHERE fs.id = :id")
    int restoreStock(@Param("id") UUID id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FlashSale fs SET fs.reservedStock = fs.reservedStock - :qty WHERE fs.id = :id")
    int releaseReservedStock(@Param("id") UUID id, @Param("qty") int qty);
}
