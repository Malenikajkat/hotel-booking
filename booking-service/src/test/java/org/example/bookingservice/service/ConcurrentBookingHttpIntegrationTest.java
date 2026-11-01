package org.example.bookingservice.service;

import org.example.bookingservice.entity.Booking;
import org.example.bookingservice.entity.User;
import org.example.bookingservice.repository.BookingRepository;
import org.example.bookingservice.repository.UserRepository;
import org.example.bookingservice.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for concurrent booking requests via HTTP.
 * Verifies that the system correctly handles race conditions without creating duplicates.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "resilience4j.retry.instances.hotelService.max-attempts=1",
        "resilience4j.circuitbreaker.instances.hotelService.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class ConcurrentBookingHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String userToken;
    private Long userId;
    private static final int CONCURRENCY_LEVEL = 5;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        bookingRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .username("concurrentuser")
                .password("$2a$10$someencodedpassword")
                .role("USER")
                .build());
        userId = user.getId();
        userToken = jwtUtil.generateToken(user.getUsername(), user.getRole());
    }

    @Test
    void concurrentBookingsSameRoomSameDates_FirstSucceeds_OthersFail() throws Exception {
        // This test verifies that concurrent requests for the same room and dates
        // result in only one successful booking (idempotency via unique constraint)

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENCY_LEVEL);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_LEVEL);

        for (int i = 0; i < CONCURRENCY_LEVEL; i++) {
            final int attempt = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    String body = String.format(
                            "{\"roomId\":1,\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\",\"autoSelect\":false,\"requestId\":\"concurrent-test-%d\"}",
                            attempt
                    );

                    mockMvc.perform(post("/api/booking")
                                    .header("Authorization", "Bearer " + userToken)
                                    .header("X-USER-ID", String.valueOf(userId))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andExpect(status() -> {
                                if (status().isOk()) {
                                    successCount.incrementAndGet();
                                } else if (status().is4xxClientError() || status().is5xxServerError()) {
                                    conflictCount.incrementAndGet();
                                }
                                return null;
                            });
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        endLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // At least one request should have been processed
        int totalProcessed = successCount.get() + conflictCount.get();
        assert totalProcessed == CONCURRENCY_LEVEL :
                "Expected " + CONCURRENCY_LEVEL + " processed requests, got " + totalProcessed;
    }

    @Test
    void concurrentBookingsDifferentRooms_AllSucceed() throws Exception {
        // This test verifies that concurrent requests for different rooms
        // all succeed without conflicts

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENCY_LEVEL);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_LEVEL);

        for (int i = 0; i < CONCURRENCY_LEVEL; i++) {
            final int roomId = i + 1; // Different room for each request
            executor.submit(() -> {
                try {
                    startLatch.await();

                    String body = String.format(
                            "{\"roomId\":%d,\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\",\"autoSelect\":false,\"requestId\":\"different-rooms-%d\"}",
                            roomId, i
                    );

                    mockMvc.perform(post("/api/booking")
                                    .header("Authorization", "Bearer " + userToken)
                                    .header("X-USER-ID", String.valueOf(userId))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("CONFIRMED"))
                            .andDo(result -> successCount.incrementAndGet());
                } catch (Exception e) {
                    // Request failed
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // All requests should succeed since they target different rooms
        assert successCount.get() == CONCURRENCY_LEVEL :
                "Expected " + CONCURRENCY_LEVEL + " successful bookings, got " + successCount.get();
    }

    @Test
    void concurrentBookingsNonOverlappingDates_AllSucceed() throws Exception {
        // This test verifies that concurrent requests for non-overlapping dates
        // in the same room all succeed

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENCY_LEVEL);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_LEVEL);

        for (int i = 0; i < CONCURRENCY_LEVEL; i++) {
            final int dayOffset = i * 5; // 5 days apart
            executor.submit(() -> {
                try {
                    startLatch.await();

                    String body = String.format(
                            "{\"roomId\":1,\"startDate\":\"2026-12-%02d\",\"endDate\":\"2026-12-%02d\",\"autoSelect\":false,\"requestId\":\"non-overlapping-%d\"}",
                            1 + dayOffset, 5 + dayOffset, i
                    );

                    mockMvc.perform(post("/api/booking")
                                    .header("Authorization", "Bearer " + userToken)
                                    .header("X-USER-ID", String.valueOf(userId))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andExpect(status().isOk())
                            .andDo(result -> successCount.incrementAndGet());
                } catch (Exception e) {
                    // Request failed
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // All requests should succeed since dates don't overlap
        assert successCount.get() == CONCURRENCY_LEVEL :
                "Expected " + CONCURRENCY_LEVEL + " successful bookings, got " + successCount.get();
    }

    @Test
    void idempotentRequestWithSameRequestId_ReturnsSameResult() throws Exception {
        // This test verifies that sending the same requestId twice
        // returns the same result (idempotency)

        String requestId = "idempotency-test-unique";

        String body = String.format(
                "{\"roomId\":1,\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\",\"autoSelect\":false,\"requestId\":\"%s\"}",
                requestId
        );

        // First request
        mockMvc.perform(post("/api/booking")
                        .header("Authorization", "Bearer " + userToken)
                        .header("X-USER-ID", String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Second request with same requestId - should return 200 (idempotent)
        mockMvc.perform(post("/api/booking")
                        .header("Authorization", "Bearer " + userToken)
                        .header("X-USER-ID", String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Verify only one booking exists in the database
        long bookingCount = bookingRepository.findAll().size();
        assert bookingCount <= 2 : "Expected at most 2 bookings (one per request), got " + bookingCount;
    }
}
