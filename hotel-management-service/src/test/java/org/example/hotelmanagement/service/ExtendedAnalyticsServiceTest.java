package org.example.hotelmanagement.service;

import org.example.hotelmanagement.dto.BookingTrendByDayOfWeek;
import org.example.hotelmanagement.dto.MonthlyOccupancyStats;
import org.example.hotelmanagement.dto.TopBookedRoomStats;
import org.example.hotelmanagement.entity.Booking;
import org.example.hotelmanagement.entity.Hotel;
import org.example.hotelmanagement.entity.Room;
import org.example.hotelmanagement.exception.HotelNotFoundException;
import org.example.hotelmanagement.repository.BookingRepository;
import org.example.hotelmanagement.repository.HotelRepository;
import org.example.hotelmanagement.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for extended analytics methods in HotelService.
 * Tests booking trends, monthly occupancy, and top rooms ranking.
 */
@ExtendWith(MockitoExtension.class)
class ExtendedAnalyticsServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private HotelService hotelService;

    private Long hotelId;
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        hotelId = 1L;
        hotel = Hotel.builder()
                .id(hotelId)
                .name("Test Hotel")
                .address("Test Address")
                .build();
    }

    @Test
    void getBookingTrendByDayOfWeek_Success() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        // DAYOFWEEK returns 1=Sunday, 2=Monday, 3=Tuesday, etc.
        List<Object[]> results = List.of(
                new Object[]{(Object) 2L, (Object) 5L},  // Monday: 5 bookings
                new Object[]{(Object) 3L, (Object) 3L}   // Tuesday: 3 bookings
        );
        when(bookingRepository.countBookingsByDayOfWeek(hotelId)).thenReturn(results);

        // Act
        List<BookingTrendByDayOfWeek> trends = hotelService.getBookingTrendByDayOfWeek(hotelId);

        // Assert
        assertNotNull(trends);
        assertEquals(2, trends.size());
        assertEquals(Integer.valueOf(2), trends.get(0).getDayOfWeek());
        assertEquals("Monday", trends.get(0).getDayName());
        assertEquals(Long.valueOf(5), trends.get(0).getBookingCount());
        assertEquals(Integer.valueOf(3), trends.get(1).getDayOfWeek());
        assertEquals("Tuesday", trends.get(1).getDayName());
        assertEquals(Long.valueOf(3), trends.get(1).getBookingCount());

        // Verify total percentage calculation
        double totalPercentage = trends.stream()
                .mapToDouble(BookingTrendByDayOfWeek::getPercentage)
                .sum();
        assertEquals(100.0, totalPercentage, 0.1);

        verify(hotelRepository).findById(hotelId);
        verify(bookingRepository).countBookingsByDayOfWeek(hotelId);
    }

    @Test
    void getBookingTrendByDayOfWeek_HotelNotFound_ThrowsException() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(HotelNotFoundException.class,
                () -> hotelService.getBookingTrendByDayOfWeek(hotelId));

        verify(hotelRepository).findById(hotelId);
        verify(bookingRepository, never()).countBookingsByDayOfWeek(anyLong());
    }

    @Test
    void getBookingTrendByDayOfWeek_NoBookings_ReturnsEmpty() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(bookingRepository.countBookingsByDayOfWeek(hotelId)).thenReturn(List.of());

        // Act
        List<BookingTrendByDayOfWeek> trends = hotelService.getBookingTrendByDayOfWeek(hotelId);

        // Assert
        assertNotNull(trends);
        assertTrue(trends.isEmpty());
    }

    @Test
    void getMonthlyOccupancyStats_Success() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        List<Room> rooms = List.of(
                Room.builder().id(1L).hotelId(hotelId).roomNumber("101").available(true).timesBooked(5).build(),
                Room.builder().id(2L).hotelId(hotelId).roomNumber("102").available(true).timesBooked(3).build()
        );
        when(roomRepository.findByHotelId(hotelId)).thenReturn(rooms);

        // Year=2026, Month=1, Count=8
        List<Object[]> results = new ArrayList<>();
        results.add(new Object[]{2026L, 1L, 8L});
        when(bookingRepository.countBookingsByMonth(hotelId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(results);

        // Act
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        List<MonthlyOccupancyStats> stats = hotelService.getMonthlyOccupancyStats(hotelId, startDate, endDate);

        // Assert
        assertNotNull(stats);
        assertEquals(3, stats.size()); // January, February, March

        // January should have bookings
        MonthlyOccupancyStats january = stats.get(0);
        assertEquals(Integer.valueOf(2026), january.getYear());
        assertEquals(Integer.valueOf(1), january.getMonthNumber());
        assertEquals("January", january.getMonthName());
        assertEquals(Long.valueOf(8), january.getBookingCount());

        // February and March should have zero bookings
        MonthlyOccupancyStats february = stats.get(1);
        assertEquals(Long.valueOf(0), february.getBookingCount());
        assertEquals(Double.valueOf(0.0), february.getOccupancyRate());

        MonthlyOccupancyStats march = stats.get(2);
        assertEquals(Long.valueOf(0), march.getBookingCount());

        verify(hotelRepository).findById(hotelId);
        verify(roomRepository).findByHotelId(hotelId);
        verify(bookingRepository).countBookingsByMonth(eq(hotelId), eq(startDate), eq(endDate));
    }

    @Test
    void getMonthlyOccupancyStats_HotelNotFound_ThrowsException() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(HotelNotFoundException.class,
                () -> hotelService.getMonthlyOccupancyStats(hotelId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        verify(hotelRepository).findById(hotelId);
        verify(bookingRepository, never()).countBookingsByMonth(anyLong(), any(), any());
    }

    @Test
    void getMonthlyOccupancyStats_AllMonthsEmpty() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        List<Room> rooms = List.of(
                Room.builder().id(1L).hotelId(hotelId).roomNumber("101").available(true).timesBooked(0).build()
        );
        when(roomRepository.findByHotelId(hotelId)).thenReturn(rooms);

        when(bookingRepository.countBookingsByMonth(anyLong(), any(), any())).thenReturn(List.of());

        // Act
        List<MonthlyOccupancyStats> stats = hotelService.getMonthlyOccupancyStats(
                hotelId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        // Assert
        assertNotNull(stats);
        assertEquals(1, stats.size());
        assertEquals(Long.valueOf(0), stats.get(0).getBookingCount());
        assertEquals(Double.valueOf(0.0), stats.get(0).getOccupancyRate());
    }

    @Test
    void getTopBookedRooms_Success() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        List<Room> rooms = List.of(
                Room.builder().id(1L).hotelId(hotelId).roomNumber("101").available(true).timesBooked(10).build(),
                Room.builder().id(2L).hotelId(hotelId).roomNumber("102").available(true).timesBooked(5).build(),
                Room.builder().id(3L).hotelId(hotelId).roomNumber("201").available(true).timesBooked(8).build()
        );
        when(roomRepository.findByHotelId(hotelId)).thenReturn(rooms);

        // Act
        TopBookedRoomStats stats = hotelService.getTopBookedRooms(hotelId, 10);

        // Assert
        assertNotNull(stats);
        assertEquals(hotelId, stats.getHotelId());
        assertEquals("Test Hotel", stats.getHotelName());
        assertEquals(3, stats.getTopRooms().size());
        assertEquals(Long.valueOf(23), stats.getTotalBookings()); // 10 + 5 + 8

        // Verify ranking
        TopBookedRoomStats.RoomRanking topRoom = stats.getTopRooms().get(0);
        assertEquals(Integer.valueOf(1), topRoom.getRank());
        assertEquals(Long.valueOf(1L), topRoom.getRoomId());
        assertEquals("101", topRoom.getRoomNumber());
        assertEquals(Integer.valueOf(10), topRoom.getTimesBooked());

        TopBookedRoomStats.RoomRanking secondRoom = stats.getTopRooms().get(1);
        assertEquals(Integer.valueOf(2), secondRoom.getRank());
        assertEquals(Long.valueOf(3L), secondRoom.getRoomId());
        assertEquals(Integer.valueOf(8), secondRoom.getTimesBooked());

        TopBookedRoomStats.RoomRanking thirdRoom = stats.getTopRooms().get(2);
        assertEquals(Integer.valueOf(3), thirdRoom.getRank());
        assertEquals(Long.valueOf(2L), thirdRoom.getRoomId());
        assertEquals(Integer.valueOf(5), thirdRoom.getTimesBooked());

        verify(hotelRepository).findById(hotelId);
        verify(roomRepository).findByHotelId(hotelId);
    }

    @Test
    void getTopBookedRooms_LimitRestriction() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        List<Room> rooms = List.of(
                Room.builder().id(1L).hotelId(hotelId).roomNumber("101").available(true).timesBooked(10).build(),
                Room.builder().id(2L).hotelId(hotelId).roomNumber("102").available(true).timesBooked(5).build(),
                Room.builder().id(3L).hotelId(hotelId).roomNumber("201").available(true).timesBooked(8).build()
        );
        when(roomRepository.findByHotelId(hotelId)).thenReturn(rooms);

        // Act
        TopBookedRoomStats stats = hotelService.getTopBookedRooms(hotelId, 2);

        // Assert
        assertNotNull(stats);
        assertEquals(2, stats.getTopRooms().size());
        assertEquals(Integer.valueOf(1), stats.getTopRooms().get(0).getRank());
        assertEquals(Integer.valueOf(2), stats.getTopRooms().get(1).getRank());
    }

    @Test
    void getTopBookedRooms_HotelNotFound_ThrowsException() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(HotelNotFoundException.class,
                () -> hotelService.getTopBookedRooms(hotelId, 10));

        verify(hotelRepository).findById(hotelId);
        verify(roomRepository, never()).findByHotelId(anyLong());
    }

    @Test
    void getTopBookedRooms_EmptyHotel_ReturnsEmpty() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomRepository.findByHotelId(hotelId)).thenReturn(List.of());

        // Act
        TopBookedRoomStats stats = hotelService.getTopBookedRooms(hotelId, 10);

        // Assert
        assertNotNull(stats);
        assertEquals(hotelId, stats.getHotelId());
        assertEquals("Test Hotel", stats.getHotelName());
        assertTrue(stats.getTopRooms().isEmpty());
        assertEquals(Long.valueOf(0), stats.getTotalBookings());
        assertEquals(Double.valueOf(0.0), stats.getAverageBookingsPerRoom());
    }

    @Test
    void getTopBookedRooms_AllRoomsSameBookings_RankedById() {
        // Arrange
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        List<Room> rooms = List.of(
                Room.builder().id(3L).hotelId(hotelId).roomNumber("201").available(true).timesBooked(5).build(),
                Room.builder().id(1L).hotelId(hotelId).roomNumber("101").available(true).timesBooked(5).build(),
                Room.builder().id(2L).hotelId(hotelId).roomNumber("102").available(true).timesBooked(5).build()
        );
        when(roomRepository.findByHotelId(hotelId)).thenReturn(rooms);

        // Act
        TopBookedRoomStats stats = hotelService.getTopBookedRooms(hotelId, 10);

        // Assert - when timesBooked is equal, rooms should be ranked by id ASC
        assertEquals(Long.valueOf(1L), stats.getTopRooms().get(0).getRoomId());
        assertEquals(Long.valueOf(2L), stats.getTopRooms().get(1).getRoomId());
        assertEquals(Long.valueOf(3L), stats.getTopRooms().get(2).getRoomId());
    }
}
