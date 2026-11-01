package org.example.hotelmanagement.controller;

import org.example.hotelmanagement.config.TestSecurityConfig;
import org.example.hotelmanagement.dto.BookingTrendByDayOfWeek;
import org.example.hotelmanagement.dto.MonthlyOccupancyStats;
import org.example.hotelmanagement.dto.TopBookedRoomStats;
import org.example.hotelmanagement.entity.Booking;
import org.example.hotelmanagement.entity.Hotel;
import org.example.hotelmanagement.entity.Room;
import org.example.hotelmanagement.repository.BookingRepository;
import org.example.hotelmanagement.repository.HotelRepository;
import org.example.hotelmanagement.repository.RoomRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for extended hotel analytics endpoints.
 * Tests trend analysis, monthly occupancy, and top rooms ranking.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@AutoConfigureMockMvc(addFilters = false)
class ExtendedAnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Long hotelId;
    private Long roomId1;
    private Long roomId2;
    private Long roomId3;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();

        Hotel hotel = hotelRepository.save(Hotel.builder()
                .name("Analytics Test Hotel")
                .address("Test Address")
                .build());
        hotelId = hotel.getId();

        roomId1 = roomRepository.save(Room.builder()
                .hotelId(hotelId)
                .roomNumber("101")
                .available(true)
                .timesBooked(10)
                .build()).getId();

        roomId2 = roomRepository.save(Room.builder()
                .hotelId(hotelId)
                .roomNumber("102")
                .available(true)
                .timesBooked(5)
                .build()).getId();

        roomId3 = roomRepository.save(Room.builder()
                .hotelId(hotelId)
                .roomNumber("201")
                .available(true)
                .timesBooked(8)
                .build()).getId();

        // Create bookings for different days of week
        bookingRepository.save(Booking.builder()
                .roomId(roomId1)
                .startDate(LocalDate.of(2026, 1, 5)) // Monday
                .endDate(LocalDate.of(2026, 1, 8))
                .correlationId("trend-test-1")
                .build());

        bookingRepository.save(Booking.builder()
                .roomId(roomId1)
                .startDate(LocalDate.of(2026, 1, 12)) // Monday
                .endDate(LocalDate.of(2026, 1, 15))
                .correlationId("trend-test-2")
                .build());

        bookingRepository.save(Booking.builder()
                .roomId(roomId2)
                .startDate(LocalDate.of(2026, 1, 6)) // Tuesday
                .endDate(LocalDate.of(2026, 1, 9))
                .correlationId("trend-test-3")
                .build());

        bookingRepository.save(Booking.builder()
                .roomId(roomId3)
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 5))
                .correlationId("monthly-test-1")
                .build());
    }

    @Test
    void getBookingTrendByDayOfWeek_ReturnsTrends() throws Exception {
        mockMvc.perform(get("/api/hotels/{id}/analytics/trend/day-of-week", hotelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2)) // Monday and Tuesday
                .andExpect(jsonPath("$[0].dayOfWeek").value(2)) // Monday = 2 (DAYOFWEEK returns 1=Sunday, 2=Monday)
                .andExpect(jsonPath("$[0].bookingCount").value(2))
                .andExpect(jsonPath("$[1].dayOfWeek").value(3)) // Tuesday
                .andExpect(jsonPath("$[1].bookingCount").value(1));
    }

    @Test
    void getMonthlyOccupancyStats_ReturnsMonthlyBreakdown() throws Exception {
        mockMvc.perform(get("/api/hotels/{id}/analytics/monthly", hotelId)
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2)) // January and February
                .andExpect(jsonPath("$[0].year").value(2026))
                .andExpect(jsonPath("$[0].monthNumber").value(1))
                .andExpect(jsonPath("$[0].bookingCount").value(3))
                .andExpect(jsonPath("$[1].year").value(2026))
                .andExpect(jsonPath("$[1].monthNumber").value(2))
                .andExpect(jsonPath("$[1].bookingCount").value(1));
    }

    @Test
    void getTopBookedRooms_ReturnsRankedRooms() throws Exception {
        mockMvc.perform(get("/api/hotels/{id}/analytics/top-rooms", hotelId)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(hotelId.intValue()))
                .andExpect(jsonPath("$.totalBookings").value(23)) // 10 + 5 + 8
                .andExpect(jsonPath("$.averageBookingsPerRoom").value(7.67))
                .andExpect(jsonPath("$.topRooms").isArray())
                .andExpect(jsonPath("$.topRooms.length()").value(3))
                .andExpect(jsonPath("$.topRooms[0].rank").value(1))
                .andExpect(jsonPath("$.topRooms[0].roomId").value(roomId1.intValue()))
                .andExpect(jsonPath("$.topRooms[0].timesBooked").value(10))
                .andExpect(jsonPath("$.topRooms[1].rank").value(2))
                .andExpect(jsonPath("$.topRooms[1].roomId").value(roomId3.intValue()))
                .andExpect(jsonPath("$.topRooms[1].timesBooked").value(8))
                .andExpect(jsonPath("$.topRooms[2].rank").value(3))
                .andExpect(jsonPath("$.topRooms[2].roomId").value(roomId2.intValue()))
                .andExpect(jsonPath("$.topRooms[2].timesBooked").value(5));
    }

    @Test
    void getTopBookedRooms_LimitRestriction_ReturnsTopN() throws Exception {
        mockMvc.perform(get("/api/hotels/{id}/analytics/top-rooms", hotelId)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topRooms.length()").value(1))
                .andExpect(jsonPath("$.topRooms[0].rank").value(1))
                .andExpect(jsonPath("$.topRooms[0].roomId").value(roomId1.intValue()));
    }

    @Test
    void getBookingTrendByDayOfWeek_HotelNotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/hotels/999/analytics/trend/day-of-week"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMonthlyOccupancyStats_HotelNotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/hotels/999/analytics/monthly")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-02-28"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTopBookedRooms_HotelNotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/hotels/999/analytics/top-rooms"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBookingTrendByDayOfWeek_NoBookings_ReturnsEmpty() throws Exception {
        bookingRepository.deleteAll();

        Hotel emptyHotel = hotelRepository.save(Hotel.builder()
                .name("Empty Hotel")
                .address("Empty Address")
                .build());

        mockMvc.perform(get("/api/hotels/{id}/analytics/trend/day-of-week", emptyHotel.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMonthlyOccupancyStats_ZeroBookingsInPeriod_ReturnsEmptyMonths() throws Exception {
        mockMvc.perform(get("/api/hotels/{id}/analytics/monthly", hotelId)
                        .param("startDate", "2027-01-01")
                        .param("endDate", "2027-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].bookingCount").value(0))
                .andExpect(jsonPath("$[0].occupancyRate").value(0.0));
    }
}
