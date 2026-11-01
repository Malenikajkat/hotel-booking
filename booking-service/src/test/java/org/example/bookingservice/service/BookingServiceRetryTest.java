package org.example.bookingservice.service;

import org.example.bookingservice.dto.BookingCreateRequest;
import org.example.bookingservice.dto.HotelAvailabilityRequest;
import org.example.bookingservice.entity.Booking;
import org.example.bookingservice.exception.HotelServiceUnavailableException;
import org.example.bookingservice.exception.RoomNotAvailableException;
import org.example.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Resilience4j retry and timeout handling.
 * Verifies that the system correctly handles Hotel Service failures,
 * timeouts, and retries before compensation.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceRetryTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private org.springframework.web.client.RestClient hotelServiceClient;

    @Mock
    private org.springframework.web.client.RestClient.RequestHeadersUriResponseSpec requestHeadersUriResponseSpec;

    @Mock
    private org.springframework.web.client.RestClient.RequestHeadersSpec<HotelAvailabilityRequest> requestHeadersSpec;

    @Mock
    private org.springframework.web.client.RestClient.RequestBodySpec requestBodySpec;

    @InjectMocks
    private BookingService bookingService;

    private BookingCreateRequest request;
    private Long userId = 1L;
    private Long roomId = 10L;

    @BeforeEach
    void setUp() {
        request = BookingCreateRequest.builder()
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .autoSelect(false)
                .build();

        // Mock RestClient chain for confirm-availability
        when(hotelServiceClient.post()).thenReturn(requestHeadersUriResponseSpec);
        when(requestHeadersUriResponseSpec.uri(anyString(), anyLong())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
    }

    @Test
    void createBooking_HotelServiceTimeout_ThrowsException() {
        // No overlapping bookings locally
        when(bookingRepository.findOverlappingBookings(anyLong(), any(), any()))
                .thenReturn(List.of());

        // Booking saved in PENDING
        Booking pendingBooking = Booking.builder()
                .id(1L)
                .userId(userId)
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(Booking.BookingStatus.PENDING)
                .correlationId("test-correlation")
                .build();

        when(bookingRepository.save(any(Booking.class)))
                .thenReturn(pendingBooking)
                .thenReturn(Booking.builder()
                        .id(1L)
                        .userId(userId)
                        .roomId(roomId)
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .status(Booking.BookingStatus.CANCELLED)
                        .correlationId("test-correlation")
                        .build());

        // Simulate timeout on Hotel Service call
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(
                new ResourceAccessException("Connection timed out"));

        // Should throw RoomNotAvailableException after retry exhaustion
        RoomNotAvailableException exception = assertThrows(RoomNotAvailableException.class,
                () -> bookingService.createBooking(userId, request));

        assertTrue(exception.getMessage().contains("not available"));
        // Verify compensation was called (booking saved as CANCELLED)
        verify(bookingRepository, times(2)).save(any(Booking.class));
    }

    @Test
    void createBooking_HotelServiceUnavailable_ThrowsException() {
        when(bookingRepository.findOverlappingBookings(anyLong(), any(), any()))
                .thenReturn(List.of());

        Booking pendingBooking = Booking.builder()
                .id(1L)
                .userId(userId)
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(Booking.BookingStatus.PENDING)
                .correlationId("test-correlation")
                .build();

        when(bookingRepository.save(any(Booking.class)))
                .thenReturn(pendingBooking)
                .thenReturn(Booking.builder()
                        .id(1L)
                        .userId(userId)
                        .roomId(roomId)
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .status(Booking.BookingStatus.CANCELLED)
                        .correlationId("test-correlation")
                        .build());

        // Simulate Hotel Service returning unavailable
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(
                new HotelServiceUnavailableException("Hotel Service unavailable"));

        RoomNotAvailableException exception = assertThrows(RoomNotAvailableException.class,
                () -> bookingService.createBooking(userId, request));

        assertTrue(exception.getMessage().contains("not available"));
        verify(bookingRepository, times(2)).save(any(Booking.class));
    }

    @Test
    void createBooking_IdempotentRequestId_ReturnsExistingBooking() {
        String requestId = "unique-request-id-123";

        Booking existingBooking = Booking.builder()
                .id(1L)
                .userId(userId)
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId(requestId)
                .build();

        when(bookingRepository.findByCorrelationId(requestId))
                .thenReturn(Optional.of(existingBooking));

        BookingCreateRequest idempotentRequest = BookingCreateRequest.builder()
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .autoSelect(false)
                .requestId(requestId)
                .build();

        BookingResponse response = bookingService.createBooking(userId, idempotentRequest);

        assertEquals(Booking.BookingStatus.CONFIRMED, response.getStatus());
        // Verify no Hotel Service call was made
        verify(hotelServiceClient, never()).post();
    }

    @Test
    void confirmAvailabilityWithRetry_ThrowsOnUnavailable() {
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(
                new HotelServiceUnavailableException("Room not available"));

        assertThrows(HotelServiceUnavailableException.class,
                () -> bookingService.confirmAvailabilityWithRetry(
                        roomId, request, "test-correlation"));
    }
}
