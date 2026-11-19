package com.flashsale.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.ratelimit.enabled=true",
        "app.ratelimit.capacity=5",
        "app.ratelimit.duration-seconds=10"
})
class RateLimitIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Rate limiter enforces token bucket policy and returns 429 when capacity is exceeded")
    void testRateLimitEnforced() {
        String testUserId = "ratelimit-user-" + UUID.randomUUID();
        UUID deterministicSaleId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", testUserId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // First 5 requests should succeed
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/flash-sales/" + deterministicSaleId,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 6th request must be throttled with 429
        ResponseEntity<String> throttledResponse = restTemplate.exchange(
                "/api/v1/flash-sales/" + deterministicSaleId,
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(throttledResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttledResponse.getHeaders().containsKey("X-Rate-Limit-Retry-After-Seconds")).isTrue();
        assertThat(throttledResponse.getBody()).contains("RATE_LIMIT_EXCEEDED");
    }
}
