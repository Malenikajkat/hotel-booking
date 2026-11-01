package org.example.bookingservice.service;

import org.example.bookingservice.dto.BookingCreateRequest;
import org.example.bookingservice.dto.BookingResponse;
import org.example.bookingservice.entity.Booking;
import org.example.bookingservice.exception.RoomNotAvailableException;
import org.example.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestClient;

import jakarta.persistence.EntityNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RestClient hotelServiceClient;

    @Mock
    private Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_OverlappingBookings_ThrowsException() {
        Long userId = 1L;
        BookingCreateRequest request = BookingCreateRequest.builder()
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .autoSelect(false)
                .build();

        Booking existingBooking = Booking.builder()
                .id(2L)
                .userId(2L)
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 3))
                .endDate(LocalDate.of(2026, 12, 7))
                .status(Booking.BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findOverlappingBookings(anyLong(), any(), any()))
                .thenReturn(List.of(existingBooking));

        RoomNotAvailableException exception = assertThrows(RoomNotAvailableException.class,
                () -> bookingService.createBooking(userId, request));

        assertTrue(exception.getMessage().contains("already booked"));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void cancelBooking_OwnershipMismatch_ThrowsException() {
        Booking booking = Booking.builder()
                .id(1L)
                .userId(99L)
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> bookingService.cancelBooking(1L, 1L));

        assertTrue(exception.getMessage().contains("own bookings"));
    }

    @Test
    void cancelBooking_AlreadyCancelled_ReturnsBooking() {
        Booking booking = Booking.builder()
                .id(1L)
                .userId(1L)
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CANCELLED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.cancelBooking(1L, 1L);

        assertEquals(Booking.BookingStatus.CANCELLED, response.getStatus());
    }

    @Test
    void cancelBooking_NotFound_ThrowsException() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> bookingService.cancelBooking(1L, 999L));
    }

    @Test
    void getUserBookings_ReturnsPage() {
        Long userId = 1L;
        Booking booking = Booking.builder()
                .id(1L)
                .userId(userId)
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking)));

        var result = bookingService.getUserBookings(userId, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(Booking.BookingStatus.CONFIRMED, result.getContent().get(0).getStatus());
    }

    @Test
    void getBookingById_NotFound_ThrowsException() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> bookingService.getBookingById(999L, 1L));
    }
}
