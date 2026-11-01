package org.example.bookingservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

/**
 * Resilience4j configuration for hotel service calls.
 * Configures circuit breaker policy for inter-service communication.
 * Retry policy with exponential backoff is configured via application.yml.
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public RetryConfig hotelServiceRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(ResourceAccessException.class, Exception.class)
                .build();
    }

    @Bean
    public CircuitBreakerConfig hotelServiceCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();
    }
}
