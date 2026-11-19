package com.flashsale.engine.inventory;

import com.flashsale.engine.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final Clock clock;

    public FlashSaleService(FlashSaleRepository flashSaleRepository, Clock clock) {
        this.flashSaleRepository = flashSaleRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FlashSaleResponse getFlashSale(UUID saleId) {
        FlashSale sale = flashSaleRepository.findByIdWithProduct(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found: " + saleId));
        return FlashSaleResponse.from(sale, clock.instant());
    }
}
