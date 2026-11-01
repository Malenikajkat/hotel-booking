package org.example.bookingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bookingservice.dto.BookingCreateRequest;
import org.example.bookingservice.dto.BookingResponse;
import org.example.bookingservice.config.TestSecurityConfig;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String userToken;
    private String adminToken;
    private Long userId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        bookingRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .username("testuser")
                .password("$2a$10$someencodedpassword")
                .role("USER")
                .build());
        userId = user.getId();

        User admin = userRepository.save(User.builder()
                .username("admin")
                .password("$2a$10$someencodedpassword")
                .role("ADMIN")
                .build());
        adminId = admin.getId();

        userToken = jwtUtil.generateToken(String.valueOf(userId), "USER");
        adminToken = jwtUtil.generateToken(String.valueOf(adminId), "ADMIN");
    }

    @Test
    void register_Success() throws Exception {
        String body = "{\"username\":\"newuser\",\"password\":\"newpass\"}";

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_DuplicateUsername_ReturnsBadRequest() throws Exception {
        String body = "{\"username\":\"testuser\",\"password\":\"newpass\"}";

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticate_Success() throws Exception {
        mockMvc.perform(post("/api/user/auth")
                        .param("username", "testuser")
                        .param("password", "wrongpass"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticate_CorrectCredentials_ReturnsToken() throws Exception {
        mockMvc.perform(post("/api/user/auth")
                        .param("username", "testuser")
                        .param("password", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void authenticate_InvalidCredentials_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/user/auth")
                        .param("username", "nonexistent")
                        .param("password", "wrongpass"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserBookings_Empty_ReturnsOK() throws Exception {
        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void getBooking_NotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/booking/999")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelBooking_NotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/booking/999")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBooking_Success_ReturnsCreated() throws Exception {
        String body = "{\"roomId\":1," +
                "\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\"," +
                "\"autoSelect\":false}";

        mockMvc.perform(post("/api/booking")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value((int) userId))
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void getBookingById_Success_ReturnsBooking() throws Exception {
        Booking booking = bookingRepository.save(Booking.builder()
                .userId(userId)
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId("test-get-booking")
                .build());

        mockMvc.perform(get("/api/booking/" + booking.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.userId").value((int) userId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void createUser_Admin_Success() throws Exception {
        String body = "{\"username\":\"newadmin\",\"password\":\"pass123\",\"role\":\"ADMIN\"}";

        mockMvc.perform(post("/api/user")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newadmin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void createUser_UserRole_Success() throws Exception {
        String body = "{\"username\":\"newuser2\",\"password\":\"pass123\",\"role\":\"USER\"}";

        mockMvc.perform(post("/api/user")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser2"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void createUser_NoRole_DefaultsToUser() throws Exception {
        String body = "{\"username\":\"newuser3\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/api/user")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser3"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void createUser_NotAdmin_ReturnsForbidden() throws Exception {
        String body = "{\"username\":\"newuser\",\"password\":\"pass123\",\"role\":\"ADMIN\"}";

        mockMvc.perform(post("/api/user")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsers_Admin_ReturnsOK() throws Exception {
        mockMvc.perform(get("/api/user")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void getAllUsers_NotAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/user")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_Admin_Success() throws Exception {
        String body = "{\"username\":\"updateduser\",\"role\":\"USER\"}";

        mockMvc.perform(patch("/api/user/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("updateduser"));
    }

    @Test
    void deleteUser_Admin_Success() throws Exception {
        mockMvc.perform(delete("/api/user/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUser_NotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/user/999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void register_InvalidUsername_ReturnsBadRequest() throws Exception {
        String body = "{\"username\":\"\",\"password\":\"pass123\"}";

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // 401 Unauthorized tests
    @Test
    void getUserBookings_NoAuthorizationHeader_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserBookings_InvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserBookings_EmptyToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    // 403 Forbidden tests
    @Test
    void getUserBookings_UserRole_CanAccessOwnBookings() throws Exception {
        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void cancelBooking_UserRoleOwnBooking_CanCancel() throws Exception {
        // Create a booking for the test user
        Booking booking = bookingRepository.save(Booking.builder()
                .userId(userId)
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId("test-cancel")
                .build());

        mockMvc.perform(delete("/api/booking/" + booking.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void cancelBooking_UserRoleOtherUserBooking_ReturnsForbidden() throws Exception {
        // Create a booking for a different user
        User otherUser = userRepository.save(User.builder()
                .username("otheruser")
                .password("$2a$10$someencodedpassword")
                .role("USER")
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .userId(otherUser.getId())
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .status(Booking.BookingStatus.CONFIRMED)
                .correlationId("test-cancel-other")
                .build());

        mockMvc.perform(delete("/api/booking/" + booking.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // Additional 401 Unauthorized tests for all protected endpoints
    @Test
    void createBooking_NoAuthorizationHeader_ReturnsUnauthorized() throws Exception {
        String body = "{\"roomId\":1," +
                "\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\"," +
                "\"autoSelect\":false}";

        mockMvc.perform(post("/api/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBooking_InvalidToken_ReturnsUnauthorized() throws Exception {
        String body = "{\"roomId\":1," +
                "\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\"," +
                "\"autoSelect\":false}";

        mockMvc.perform(post("/api/booking")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelBooking_NoAuthorizationHeader_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/booking/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelBooking_InvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/booking/1")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUser_NoAuthorizationHeader_ReturnsUnauthorized() throws Exception {
        String body = "{\"username\":\"updateduser\"}";

        mockMvc.perform(patch("/api/user/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUser_InvalidToken_ReturnsUnauthorized() throws Exception {
        String body = "{\"username\":\"updateduser\"}";

        mockMvc.perform(patch("/api/user/" + userId)
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_NoAuthorizationHeader_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/user/" + userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_InvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/user/" + userId)
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // Additional 403 Forbidden tests for ownership and role checks
    @Test
    void updateUser_NotAdmin_ReturnsForbidden() throws Exception {
        String body = "{\"username\":\"updateduser\"}";

        mockMvc.perform(patch("/api/user/" + userId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_NotAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/user/" + userId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBookingById_NoAuthorizationHeader_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/booking/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBookingById_InvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/booking/1")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }
}
