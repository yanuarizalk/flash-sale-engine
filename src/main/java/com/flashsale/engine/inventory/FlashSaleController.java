package com.flashsale.engine.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flash-sales")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @GetMapping("/{saleId}")
    public ResponseEntity<FlashSaleResponse> getFlashSale(@PathVariable UUID saleId) {
        FlashSaleResponse response = flashSaleService.getFlashSale(saleId);
        return ResponseEntity.ok(response);
    }
}
