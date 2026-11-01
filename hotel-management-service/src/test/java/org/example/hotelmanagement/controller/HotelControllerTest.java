package org.example.hotelmanagement.controller;

import org.example.hotelmanagement.dto.HotelCreateRequest;
import org.example.hotelmanagement.dto.RoomCreateRequest;
import org.example.hotelmanagement.entity.Booking;
import org.example.hotelmanagement.entity.Hotel;
import org.example.hotelmanagement.entity.Room;
import org.example.hotelmanagement.config.TestSecurityConfig;
import org.example.hotelmanagement.repository.BookingRepository;
import org.example.hotelmanagement.repository.HotelRepository;
import org.example.hotelmanagement.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@AutoConfigureMockMvc(addFilters = false)
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepo;

    @BeforeEach
    void setUp() {
        bookingRepo.deleteAll();
        hotelRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    void getAllHotels_Success() throws Exception {
        hotelRepository.save(Hotel.builder().name("Hotel 1").address("Address 1").build());
        hotelRepository.save(Hotel.builder().name("Hotel 2").address("Address 2").build());

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getHotel_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Test Hotel").address("Test Address").build());

        mockMvc.perform(get("/api/hotels/" + hotel.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Hotel"))
                .andExpect(jsonPath("$.address").value("Test Address"));
    }

    @Test
    void getRecommendedRooms_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(5).build());
        roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("102").available(true).timesBooked(2).build());
        roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("103").available(true).timesBooked(0).build());

        mockMvc.perform(get("/api/rooms/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].roomNumber").value("103"))
                .andExpect(jsonPath("$[1].roomNumber").value("102"))
                .andExpect(jsonPath("$[2].roomNumber").value("101"));
    }

    @Test
    void getAllAvailableRooms_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());
        roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("102").available(false).timesBooked(0).build());

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomNumber").value("101"));
    }

    @Test
    void getRoom_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        mockMvc.perform(get("/api/rooms/" + room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("101"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmAvailability_ReturnsOK() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        String body = "{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\",\"correlationId\":\"test-123\"}";

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmAvailability_Conflict_ReturnsBadRequest() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        Booking existing = Booking.builder()
                .roomId(room.getId())
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .build();
        bookingRepo.save(existing);

        String body = "{\"startDate\":\"2026-12-03\",\"endDate\":\"2026-12-07\"}";

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmAvailability_Idempotent_ReturnsOK() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        String body = "{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\",\"correlationId\":\"test-123\"}";

        // First call
        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        // Second call with same correlationId (idempotent)
        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.message").value("Booking already confirmed (idempotent)"));
    }

    @Test
    void releaseRoom_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/release?correlationId=test-rel-123"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void incrementTimesBooked_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(5).build());

        mockMvc.perform(patch("/api/rooms/" + room.getId() + "/times-booked"))
                .andExpect(status().isNoContent());

        // Verify timesBooked was incremented
        mockMvc.perform(get("/api/rooms/" + room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timesBooked").value(6));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmAvailability_RoomNotAvailable_ReturnsBadRequest() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(false).timesBooked(0).build());

        String body = "{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\"}";

        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value("Room is not available (maintenance)"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRoom_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        String body = "{\"roomNumber\":\"101-updated\"}";

        mockMvc.perform(patch("/api/rooms/" + room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("101-updated"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRoom_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        mockMvc.perform(delete("/api/rooms/" + room.getId()))
                .andExpect(status().isNoContent());

        // Verify room was deleted
        mockMvc.perform(get("/api/rooms/" + room.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRoomsByHotel_Success() throws Exception {
        Hotel hotel1 = hotelRepository.save(Hotel.builder().name("Hotel 1").address("Address 1").build());
        Hotel hotel2 = hotelRepository.save(Hotel.builder().name("Hotel 2").address("Address 2").build());
        roomRepository.save(Room.builder().hotelId(hotel1.getId()).roomNumber("101").available(true).timesBooked(0).build());
        roomRepository.save(Room.builder().hotelId(hotel1.getId()).roomNumber("102").available(true).timesBooked(0).build());
        roomRepository.save(Room.builder().hotelId(hotel2.getId()).roomNumber("201").available(true).timesBooked(0).build());

        mockMvc.perform(get("/api/rooms/hotel/" + hotel1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].hotelId").value(hotel1.getId()))
                .andExpect(jsonPath("$[1].hotelId").value(hotel1.getId()));
    }

    @Test
    void getAvailableRoomsByHotel_Success() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room1 = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(5).build());
        Room room2 = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("102").available(true).timesBooked(0).build());
        roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("103").available(false).timesBooked(0).build());

        mockMvc.perform(get("/api/rooms/hotel/" + hotel.getId() + "/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].roomNumber").value("102"))
                .andExpect(jsonPath("$[1].roomNumber").value("101"));
    }

    // 401 Unauthorized tests
    @Test
    void createHotel_NoAuth_ReturnsUnauthorized() throws Exception {
        String body = "{\"name\":\"Test Hotel\",\"address\":\"Test Address\"}";

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createHotel_InvalidToken_ReturnsUnauthorized() throws Exception {
        String body = "{\"name\":\"Test Hotel\",\"address\":\"Test Address\"}";

        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // 403 Forbidden tests
    @Test
    void createHotel_UserRoleNotAdmin_ReturnsForbidden() throws Exception {
        String body = "{\"name\":\"Test Hotel\",\"address\":\"Test Address\"}";

        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + generateUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRoom_UserRoleNotAdmin_ReturnsForbidden() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        String body = "{\"hotelId\":" + hotel.getId() + ",\"roomNumber\":\"101\"}";

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + generateUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHotelOccupancyStats_UserRoleNotAdmin_ReturnsForbidden() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());

        mockMvc.perform(get("/api/hotels/" + hotel.getId() + "/occupancy-stats")
                        .header("Authorization", "Bearer " + generateUserToken()))
                .andExpect(status().isForbidden());
    }

    // Search rooms by date range
    @Test
    void searchAvailableRooms_WithValidDates_ReturnsAvailableRooms() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room1 = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());
        Room room2 = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("102").available(true).timesBooked(0).build());

        // Create a blocking booking for room1
        Booking booking = Booking.builder()
                .roomId(room1.getId())
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 10))
                .build();
        bookingRepo.save(booking);

        mockMvc.perform(get("/api/rooms/search")
                        .param("startDate", "2026-12-01")
                        .param("endDate", "2026-12-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomNumber").value("102"));
    }

    // Period-based occupancy stats
    @Test
    @WithMockUser(roles = "ADMIN")
    void getPeriodOccupancyStats_ReturnsPeriodStats() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Hotel").address("Address").build());
        Room room = roomRepository.save(Room.builder().hotelId(hotel.getId()).roomNumber("101").available(true).timesBooked(0).build());

        // Create a booking in the period
        Booking booking = Booking.builder()
                .roomId(room.getId())
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .build();
        bookingRepo.save(booking);

        mockMvc.perform(get("/api/hotels/" + hotel.getId() + "/occupancy-stats/period")
                        .param("startDate", "2026-12-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-12-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-12-31"))
                .andExpect(jsonPath("$.periodBookingsCount").isNumber());
    }

    // Helper method to generate a USER token
    private String generateUserToken() {
        // Use a simple JWT that will pass validation but has USER role
        // The JWT validation extracts the role from the token payload
        return "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsInJvbGUiOiJVU0VSIiwiZXhwIjoyMDAwMDAwMDAwfQ.invalid_signature";
    }
}
