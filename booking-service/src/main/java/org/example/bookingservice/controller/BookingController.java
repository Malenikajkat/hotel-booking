package org.example.bookingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.bookingservice.dto.BookingCreateRequest;
import org.example.bookingservice.dto.BookingResponse;
import org.example.bookingservice.dto.AuthResponse;
import org.example.bookingservice.dto.UserRegisterRequest;
import org.example.bookingservice.dto.UserUpdateRequest;
import org.example.bookingservice.entity.User;
import org.example.bookingservice.service.BookingService;
import org.example.bookingservice.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Booking API", description = "API для управления бронированиями")
public class BookingController {

    private final BookingService bookingService;
    private final UserService userService;

    public BookingController(BookingService bookingService, UserService userService) {
        this.bookingService = bookingService;
        this.userService = userService;
    }

    // Auth endpoints
    @PostMapping("/user/register")
    @Operation(summary = "Регистрация пользователя", description = "Создаёт нового пользователя с ролью USER")
    @ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован")
    @ApiResponse(responseCode = "400", description = "Некорректный запрос (дубликат username)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/user/auth")
    @Operation(summary = "Авторизация пользователя", description = "Вход пользователя, возвращает JWT токен")
    @ApiResponse(responseCode = "200", description = "Успешная авторизация")
    @ApiResponse(responseCode = "400", description = "Неверные учётные данные")
    public ResponseEntity<AuthResponse> authenticate(@RequestParam String username,
                                                      @RequestParam String password) {
        return ResponseEntity.ok(userService.authenticate(username, password));
    }

    // Helper method to extract userId from JWT via SecurityContextHolder
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            String userIdStr = authentication.getName();
            try {
                return Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid user ID in JWT token");
            }
        }
        throw new IllegalArgumentException("User not authenticated");
    }

    // Booking endpoints
    @PostMapping("/booking")
    @Operation(summary = "Создание бронирования", description = "Создаёт бронирование с двухфазной согласованностью. " +
            "При autoSelect=true автоматически подбирает лучший номер.")
    @ApiResponse(responseCode = "201", description = "Бронирование создано")
    @ApiResponse(responseCode = "409", description = "Номер недоступен")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(userId, request));
    }

    @GetMapping("/bookings")
    @Operation(summary = "История бронирований", description = "Возвращает список бронирований текущего пользователя с пагинацией")
    @ApiResponse(responseCode = "200", description = "Список бронирований")
    public ResponseEntity<Page<BookingResponse>> getUserBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(bookingService.getUserBookings(userId, pageable));
    }

    @GetMapping("/booking/{id}")
    @Operation(summary = "Получение бронирования", description = "Возвращает бронирование по ID")
    @ApiResponse(responseCode = "200", description = "Бронирование найдено")
    @ApiResponse(responseCode = "404", description = "Бронирование не найдено")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(bookingService.getBookingById(id, userId));
    }

    @DeleteMapping("/booking/{id}")
    @Operation(summary = "Отмена бронирования", description = "Отменяет бронирование пользователя. " +
            "Выполняет компенсацию в Hotel Service.")
    @ApiResponse(responseCode = "200", description = "Бронирование отменено")
    @ApiResponse(responseCode = "404", description = "Бронирование не найдено")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(bookingService.cancelBooking(userId, id));
    }

    // Admin endpoints
    @GetMapping("/user")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение всех пользователей", description = "Только для администраторов")
    @ApiResponse(responseCode = "200", description = "Список пользователей")
    public ResponseEntity<Page<User>> getAllUsers(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @PatchMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Обновление пользователя", description = "Только для администраторов")
    @ApiResponse(responseCode = "200", description = "Пользователь обновлён")
    public ResponseEntity<User> updateUser(@PathVariable Long id,
                                            @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удаление пользователя", description = "Только для администраторов")
    @ApiResponse(responseCode = "204", description = "Пользователь удалён")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/user")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Создание пользователя", description = "Только для администраторов. " +
            "Позволяет указать роль (USER или ADMIN).")
    @ApiResponse(responseCode = "201", description = "Пользователь создан")
    public ResponseEntity<User> createUser(@RequestBody UserRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                userService.createUser(request));
    }
}
