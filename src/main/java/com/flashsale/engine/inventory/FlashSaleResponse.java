package com.flashsale.engine.inventory;

import java.time.Instant;
import java.util.UUID;

public record FlashSaleResponse(
        UUID id,
        String productName,
        String sku,
        long priceCents,
        int totalStock,
        int availableStock,
        int reservedStock,
        int maxPerUser,
        Instant startsAt,
        Instant endsAt,
        String status
) {
    public static FlashSaleResponse from(FlashSale sale, Instant now) {
        String status;
        if (now.isBefore(sale.getStartsAt())) {
            status = "UPCOMING";
        } else if (now.isAfter(sale.getEndsAt())) {
            status = "ENDED";
        } else if (sale.getAvailableStock() <= 0) {
            status = "SOLD_OUT";
        } else {
            status = "ACTIVE";
        }

        return new FlashSaleResponse(
                sale.getId(),
                sale.getProduct() != null ? sale.getProduct().getName() : null,
                sale.getProduct() != null ? sale.getProduct().getSku() : null,
                sale.getProduct() != null ? sale.getProduct().getPriceCents() : 0L,
                sale.getTotalStock(),
                sale.getAvailableStock(),
                sale.getReservedStock(),
                sale.getMaxPerUser(),
                sale.getStartsAt(),
                sale.getEndsAt(),
                status
        );
    }
}
