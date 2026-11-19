package com.flashsale.engine.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ProxyManager<String> proxyManager;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long capacity;
    private final long durationSeconds;
    private final Supplier<BucketConfiguration> bucketConfigurationSupplier;

    public RateLimitFilter(ProxyManager<String> proxyManager,
                           ObjectMapper objectMapper,
                           @Value("${app.ratelimit.enabled:true}") boolean enabled,
                           @Value("${app.ratelimit.capacity:20}") long capacity,
                           @Value("${app.ratelimit.duration-seconds:10}") long durationSeconds) {
        this.proxyManager = proxyManager;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.capacity = capacity;
        this.durationSeconds = durationSeconds;
        this.bucketConfigurationSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofSeconds(durationSeconds))
                        .build())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientKey = resolveClientKey(request);
        String bucketKey = "rate-limit:" + clientKey;

        BucketProxy bucket = proxyManager.builder().build(bucketKey, bucketConfigurationSupplier);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitNanos = probe.getNanosToWaitForRefill();
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos));

            log.warn("Rate limit exceeded for clientKey: {}. Retry after {} seconds.", clientKey, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds."
            );
            problem.setTitle("Too Many Requests");
            problem.setType(URI.create("urn:problem-type:rate-limit-exceeded"));
            problem.setProperty("errorCode", "RATE_LIMIT_EXCEEDED");
            problem.setProperty("retryAfterSeconds", retryAfterSeconds);
            problem.setProperty("timestamp", Instant.now());

            objectMapper.writeValue(response.getOutputStream(), problem);
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
