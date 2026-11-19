package com.flashsale.engine.order;

public record CheckoutResult(
        OrderResponse response,
        int status,
        boolean isReplayed
) {
}
