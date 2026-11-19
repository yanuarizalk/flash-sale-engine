package com.flashsale.engine.common.exception;

public class SaleNotActiveException extends RuntimeException {
    public SaleNotActiveException(String message) {
        super(message);
    }
}
