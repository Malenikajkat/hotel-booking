package org.example.bookingservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.persistence.EntityNotFoundException;
import org.example.bookingservice.dto.BookingCreateRequest;
import org.example.bookingservice.dto.BookingResponse;
import org.example.bookingservice.dto.HotelAvailabilityRequest;
import org.example.bookingservice.dto.HotelAvailabilityResponse;
import org.example.bookingservice.dto.HotelRoomResponse;
import org.example.bookingservice.entity.Booking;
import org.example.bookingservice.exception.HotelServiceUnavailableException;
import org.example.bookingservice.exception.RoomNotAvailableException;
import org.example.bookingservice.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final RestClient hotelServiceClient;
    private final Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    public BookingService(BookingRepository bookingRepository,
                          RestClient hotelServiceClient,
                          Resilience4JCircuitBreakerFactory circuitBreakerFactory) {
        this.bookingRepository = bookingRepository;
        this.hotelServiceClient = hotelServiceClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Transactional
    public BookingResponse createBooking(Long userId, BookingCreateRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.info("[{}] Creating booking for user {}", correlationId, userId);

        if (request.getRequestId() != null && !request.getRequestId().isBlank()) {
            Optional<Booking> existing = bookingRepository.findByCorrelationId(request.getRequestId());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        if (request.getEndDate().isBefore(request.getStartDate()) ||
            request.getEndDate().isEqual(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        Long roomId = request.getRoomId();
        if (request.getAutoSelect() != null && request.getAutoSelect()) {
            roomId = selectBestRoom(userId, request, correlationId);
        }

        List<Booking> overlapping = bookingRepository.findOverlappingBookings(
                roomId, request.getStartDate(), request.getEndDate());
        if (!overlapping.isEmpty()) {
            throw new RoomNotAvailableException("Room is already booked for the selected dates");
        }

        Booking booking = Booking.builder()
                .userId(userId)
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(Booking.BookingStatus.PENDING)
                .correlationId(correlationId)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        booking = bookingRepository.save(booking);

        try {
            confirmAvailabilityWithRetry(roomId, request, correlationId);

            booking.setStatus(Booking.BookingStatus.CONFIRMED);
            booking = bookingRepository.save(booking);

            try {
                hotelServiceClient.patch()
                        .uri("/api/rooms/{roomId}/times-booked", roomId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("[{}] Failed to increment times_booked for room {}: {}",
                        correlationId, roomId, e.getMessage());
            }

            log.info("[{}] Booking CONFIRMED with id {}", correlationId, booking.getId());
            return mapToResponse(booking);
        } catch (HotelServiceUnavailableException | RestClientException e) {
            compensateBooking(booking, correlationId);
            throw new RoomNotAvailableException("Room is not available for the selected dates: " + e.getMessage());
        } catch (RoomNotAvailableException e) {
            compensateBooking(booking, correlationId);
            throw e;
        }
    }

    @Retry(name = "hotelService")
    @CircuitBreaker(name = "hotelService", fallbackMethod = "confirmAvailabilityCircuitBreakerFallback")
    public void confirmAvailabilityWithRetry(Long roomId, BookingCreateRequest request,
                                              String correlationId) {
        HotelAvailabilityRequest availRequest = new HotelAvailabilityRequest();
        availRequest.setStartDate(request.getStartDate());
        availRequest.setEndDate(request.getEndDate());
        availRequest.setCorrelationId(correlationId);

        HotelAvailabilityResponse response = hotelServiceClient.post()
                .uri("/api/rooms/{roomId}/confirm-availability", roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                .body(availRequest)
                .retrieve()
                .body(new ParameterizedTypeReference<HotelAvailabilityResponse>() {});

        if (response == null || !Boolean.TRUE.equals(response.getAvailable())) {
            String message = response != null ? response.getMessage() : "Null response from Hotel Service";
            log.warn("[{}] Availability not confirmed for room {}: {}", correlationId, roomId, message);
            throw new HotelServiceUnavailableException("Hotel Service returned unavailable: " + message);
        }
    }

    /**
     * Fallback method for circuit breaker pattern.
     * Called when the circuit breaker is open or too many failures occur.
     */
    public void confirmAvailabilityCircuitBreakerFallback(Long roomId, BookingCreateRequest request,
                                                            String correlationId, Throwable throwable) {
        log.error("[{}] Circuit breaker opened for room {}: {}",
                correlationId, roomId, throwable.getMessage());
        throw new HotelServiceUnavailableException(
                "Hotel Service is temporarily unavailable (circuit breaker open): " + throwable.getMessage());
    }

    private void compensateBooking(Booking booking, String correlationId) {
        try {
            hotelServiceClient.post()
                    .uri("/api/rooms/{roomId}/release?correlationId={correlationId}",
                            booking.getRoomId(), correlationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("[{}] Failed to release room {} in Hotel Service: {}",
                    correlationId, booking.getRoomId(), e.getMessage());
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    private Long selectBestRoom(Long userId, BookingCreateRequest request, String correlationId) {
        List<HotelRoomResponse> rooms;
        try {
            rooms = hotelServiceClient.get()
                    .uri("/api/rooms/recommend")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<HotelRoomResponse>>() {});
        } catch (Exception e) {
            log.error("[{}] Failed to fetch rooms from Hotel Service: {}", correlationId, e.getMessage());
            throw new HotelServiceUnavailableException("Failed to fetch rooms: " + e.getMessage());
        }

        if (rooms == null || rooms.isEmpty()) {
            throw new RoomNotAvailableException("No rooms available");
        }

        List<Long> availableRoomIds = new ArrayList<>();
        for (HotelRoomResponse room : rooms) {
            try {
                HotelAvailabilityRequest availRequest = new HotelAvailabilityRequest();
                availRequest.setStartDate(request.getStartDate());
                availRequest.setEndDate(request.getEndDate());
                availRequest.setCorrelationId(null);

                HotelAvailabilityResponse availability = hotelServiceClient.post()
                        .uri("/api/rooms/{roomId}/confirm-availability", room.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                        .body(availRequest)
                        .retrieve()
                        .body(new ParameterizedTypeReference<HotelAvailabilityResponse>() {});

                if (availability != null && Boolean.TRUE.equals(availability.getAvailable())) {
                    hotelServiceClient.post()
                            .uri("/api/rooms/{roomId}/release", room.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                            .retrieve()
                            .toBodilessEntity();
                    availableRoomIds.add(room.getId());
                }
            } catch (Exception e) {
                try {
                    hotelServiceClient.post()
                            .uri("/api/rooms/{roomId}/release", room.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                            .retrieve()
                            .toBodilessEntity();
                } catch (Exception releaseEx) {
                    log.warn("[{}] Failed to release room {} after failed check: {}",
                            correlationId, room.getId(), releaseEx.getMessage());
                }
            }
        }

        if (availableRoomIds.isEmpty()) {
            throw new RoomNotAvailableException("No rooms available for the selected dates");
        }

        return availableRoomIds.get(0);
    }

    public Page<BookingResponse> getUserBookings(Long userId, Pageable pageable) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        return mapToResponse(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long userId, Long bookingId) {
        String correlationId = UUID.randomUUID().toString();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new IllegalArgumentException("You can only cancel your own bookings");
        }

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            return mapToResponse(booking);
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);

        try {
            hotelServiceClient.post()
                    .uri("/api/rooms/{roomId}/release?correlationId={correlationId}",
                            booking.getRoomId(), booking.getCorrelationId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("[{}] Failed to release room {} in Hotel Service: {}",
                    correlationId, booking.getRoomId(), e.getMessage());
        }

        return mapToResponse(booking);
    }

    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUserId())
                .roomId(booking.getRoomId())
                .startDate(booking.getStartDate())
                .endDate(booking.getEndDate())
                .status(booking.getStatus().name())
                .correlationId(booking.getCorrelationId())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
