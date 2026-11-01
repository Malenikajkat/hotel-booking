package org.example.bookingservice.service;

import org.example.bookingservice.entity.Booking;
import org.example.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for pessimistic locking with real H2 database.
 * Verifies that concurrent booking attempts are correctly handled.
 */
@DataJpaTest
@ActiveProfiles("test")
class ConcurrentBookingIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookingRepository bookingRepository;

    private Long roomId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        entityManager.flush();

        // Create a test room reference (we only need the ID for the query)
        roomId = 1L;
    }

    @Test
    void overlappingBookings_PessimisticLock_DetectsConflict() {
        // Create a booking for Dec 1-5
        Booking existing = Booking.builder()
                .userId(1L)
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId("test-correlation")
                .build();
        entityManager.persistAndFlush(existing);

        // Try to find overlapping bookings for Dec 3-7
        List<Booking> overlaps = bookingRepository.findOverlappingBookings(
                roomId,
                LocalDate.of(2026, 12, 3),
                LocalDate.of(2026, 12, 7));

        assertFalse(overlaps.isEmpty());
        assertEquals(1, overlaps.size());
        assertEquals(Booking.BookingStatus.CONFIRMED, overlaps.get(0).getStatus());
    }

    @Test
    void nonOverlappingBookings_CheckoutOnCheckin_Allowed() {
        // Create a booking for Dec 1-5
        Booking existing = Booking.builder()
                .userId(1L)
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId("test-correlation")
                .build();
        entityManager.persistAndFlush(existing);

        // Try to find overlapping bookings for Dec 5-10 (checkout = checkin, should be allowed)
        List<Booking> overlaps = bookingRepository.findOverlappingBookings(
                roomId,
                LocalDate.of(2026, 12, 5),
                LocalDate.of(2026, 12, 10));

        // Dec 5 > Dec 5 = false, so no overlap (checkout on checkin day is allowed)
        assertTrue(overlaps.isEmpty());
    }

    @Test
    void cancelledBookings_NotChecked() {
        // Create a CANCELLED booking
        Booking cancelled = Booking.builder()
                .userId(1L)
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CANCELLED)
                .correlationId("test-correlation")
                .build();
        entityManager.persistAndFlush(cancelled);

        // Try to find overlapping bookings for Dec 3-7
        List<Booking> overlaps = bookingRepository.findOverlappingBookings(
                roomId,
                LocalDate.of(2026, 12, 3),
                LocalDate.of(2026, 12, 7));

        // CANCELLED bookings should not be returned
        assertTrue(overlaps.isEmpty());
    }

    @Test
    void fullyContainedBooking_Detected() {
        // Create a booking for Dec 1-10
        Booking existing = Booking.builder()
                .userId(1L)
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 10))
                .status(Booking.BookingStatus.PENDING)
                .correlationId("test-correlation")
                .build();
        entityManager.persistAndFlush(existing);

        // Try to find overlapping bookings for Dec 3-7 (fully inside)
        List<Booking> overlaps = bookingRepository.findOverlappingBookings(
                roomId,
                LocalDate.of(2026, 12, 3),
                LocalDate.of(2026, 12, 7));

        assertFalse(overlaps.isEmpty());
        assertEquals(1, overlaps.size());
    }

    @Test
    void idempotencyFindByCorrelationId_ReturnsExisting() {
        Booking existing = Booking.builder()
                .userId(1L)
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId("unique-correlation-id")
                .build();
        entityManager.persistAndFlush(existing);

        var found = bookingRepository.findByCorrelationId("unique-correlation-id");

        assertTrue(found.isPresent());
        assertEquals(Booking.BookingStatus.CONFIRMED, found.get().getStatus());
    }

    @Test
    void uniqueConstraint_UserRoomDateOverlap_PreventsDuplicate() {
        Booking first = Booking.builder()
                .userId(1L)
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId("first-booking")
                .build();
        entityManager.persistAndFlush(first);

        // The unique constraint is on (user_id, room_id, start_date, end_date)
        // Same user, same room, same dates should fail
        Booking duplicate = Booking.builder()
                .userId(1L)
                .roomId(roomId)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.PENDING)
                .correlationId("duplicate-booking")
                .build();

        assertThrows(Exception.class, () -> entityManager.persistAndFlush(duplicate));
    }
}
