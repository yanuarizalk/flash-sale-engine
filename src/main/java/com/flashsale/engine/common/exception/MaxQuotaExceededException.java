package com.flashsale.engine.common.exception;

public class MaxQuotaExceededException extends RuntimeException {
    public MaxQuotaExceededException(String message) {
        super(message);
    }
}
