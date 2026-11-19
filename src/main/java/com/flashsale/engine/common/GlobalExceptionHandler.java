package com.flashsale.engine.common;

import com.flashsale.engine.common.exception.IdempotencyKeyConflictException;
import com.flashsale.engine.common.exception.LockAcquisitionException;
import com.flashsale.engine.common.exception.MaxQuotaExceededException;
import com.flashsale.engine.common.exception.OutOfStockException;
import com.flashsale.engine.common.exception.PaymentFailedException;
import com.flashsale.engine.common.exception.ReservationExpiredException;
import com.flashsale.engine.common.exception.ResourceNotFoundException;
import com.flashsale.engine.common.exception.SaleNotActiveException;
import com.flashsale.engine.common.exception.UserForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("urn:problem-type:resource-not-found"));
        problem.setProperty("errorCode", "NOT_FOUND");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ProblemDetail> handleOutOfStock(OutOfStockException ex) {
        log.warn("Out of stock: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Out of Stock");
        problem.setType(URI.create("urn:problem-type:out-of-stock"));
        problem.setProperty("errorCode", "OUT_OF_STOCK");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MaxQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxQuotaExceeded(MaxQuotaExceededException ex) {
        log.warn("Max quota exceeded: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Max Quota Exceeded");
        problem.setType(URI.create("urn:problem-type:max-quota-exceeded"));
        problem.setProperty("errorCode", "MAX_QUOTA_EXCEEDED");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ResponseEntity<ProblemDetail> handleReservationExpired(ReservationExpiredException ex) {
        log.warn("Reservation expired: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        problem.setTitle("Reservation Expired");
        problem.setType(URI.create("urn:problem-type:reservation-expired"));
        problem.setProperty("errorCode", "RESERVATION_EXPIRED");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.GONE).body(problem);
    }

    @ExceptionHandler(SaleNotActiveException.class)
    public ResponseEntity<ProblemDetail> handleSaleNotActive(SaleNotActiveException ex) {
        log.warn("Sale not active: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Sale Not Active");
        problem.setType(URI.create("urn:problem-type:sale-not-active"));
        problem.setProperty("errorCode", "SALE_NOT_ACTIVE");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<ProblemDetail> handleLockAcquisition(LockAcquisitionException ex) {
        log.warn("Lock acquisition failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Service Unavailable");
        problem.setType(URI.create("urn:problem-type:lock-acquisition-failed"));
        problem.setProperty("errorCode", "LOCK_ACQUISITION_FAILED");
        problem.setProperty("timestamp", Instant.now());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "1");
        return new ResponseEntity<>(problem, headers, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        log.warn("Idempotency key conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Idempotency Key Conflict");
        problem.setType(URI.create("urn:problem-type:idempotency-key-conflict"));
        problem.setProperty("errorCode", "IDEMPOTENCY_CONFLICT");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ProblemDetail> handlePaymentFailed(PaymentFailedException ex) {
        log.warn("Payment failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setTitle("Payment Failed");
        problem.setType(URI.create("urn:problem-type:payment-failed"));
        problem.setProperty("errorCode", "PAYMENT_FAILED");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(problem);
    }

    @ExceptionHandler(UserForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleUserForbidden(UserForbiddenException ex) {
        log.warn("User forbidden: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Forbidden");
        problem.setType(URI.create("urn:problem-type:forbidden"));
        problem.setProperty("errorCode", "FORBIDDEN");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Required request header '" + ex.getHeaderName() + "' is missing");
        problem.setTitle("Bad Request");
        problem.setType(URI.create("urn:problem-type:missing-header"));
        problem.setProperty("errorCode", "MISSING_HEADER");
        problem.setProperty("headerName", ex.getHeaderName());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {}", errors);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors.isEmpty() ? "Validation failed" : errors);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("urn:problem-type:validation-failed"));
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex) {
        log.error("Unhandled internal server error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("urn:problem-type:internal-server-error"));
        problem.setProperty("errorCode", "INTERNAL_SERVER_ERROR");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
