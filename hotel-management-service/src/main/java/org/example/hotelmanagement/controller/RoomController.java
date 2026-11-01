package org.example.hotelmanagement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.hotelmanagement.dto.AvailabilityRequest;
import org.example.hotelmanagement.dto.AvailabilityResponse;
import org.example.hotelmanagement.dto.RoomCreateRequest;
import org.example.hotelmanagement.dto.RoomResponse;
import org.example.hotelmanagement.service.HotelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@Tag(name = "Room API", description = "API для управления номерами")
public class RoomController {

    private final HotelService hotelService;

    public RoomController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Создание номера", description = "Добавляет номер в отель. Только для администраторов.")
    @ApiResponse(responseCode = "201", description = "Номер создан")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelService.createRoom(request));
    }

    @GetMapping
    @Operation(summary = "Получение доступных номеров", description = "Возвращает все номера с available=true с пагинацией")
    @ApiResponse(responseCode = "200", description = "Список номеров")
    public ResponseEntity<Page<RoomResponse>> getAllAvailableRooms(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(hotelService.getAvailableRooms(pageable));
    }

    @GetMapping("/recommend")
    @Operation(summary = "Рекомендованные номера", description = "Возвращает все номера, отсортированные по возрастанию times_booked")
    @ApiResponse(responseCode = "200", description = "Отсортированный список номеров")
    public ResponseEntity<List<RoomResponse>> getRecommendedRooms() {
        return ResponseEntity.ok(hotelService.getRecommendedRooms());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получение номера", description = "Возвращает номер по ID")
    @ApiResponse(responseCode = "200", description = "Номер найден")
    @ApiResponse(responseCode = "404", description = "Номер не найден")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getRoom(id));
    }

    @PostMapping("/{id}/confirm-availability")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Подтверждение доступности", description = "Временная блокировка номера на указанные даты. " +
            "Используется в процессе двухфазной согласованности бронирования. " +
            "INTERNAL endpoint - доступен только через внутренний токен.")
    @ApiResponse(responseCode = "200", description = "Доступность подтверждена")
    public ResponseEntity<AvailabilityResponse> confirmAvailability(@PathVariable Long id,
                                                                     @Valid @RequestBody AvailabilityRequest request) {
        return ResponseEntity.ok(hotelService.confirmAvailability(id, request));
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Снять блокировку", description = "Компенсирующее действие: снимает временную блокировку номера. " +
            "INTERNAL endpoint, не публикуется через Gateway.")
    @ApiResponse(responseCode = "204", description = "Блокировка снята")
    public ResponseEntity<Void> releaseRoom(@PathVariable Long id,
                                             @RequestParam(required = false) String correlationId) {
        hotelService.releaseRoom(id, correlationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/times-booked")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Увеличить счётчик бронирований", description = "Увеличивает times_booked на 1. Только для администраторов.")
    @ApiResponse(responseCode = "204", description = "Счётчик увеличен")
    public ResponseEntity<Void> incrementTimesBooked(@PathVariable Long id) {
        hotelService.incrementTimesBooked(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Обновление номера", description = "Обновляет данные номера (available, room_number). Только для администраторов.")
    @ApiResponse(responseCode = "200", description = "Номер обновлён")
    @ApiResponse(responseCode = "404", description = "Номер не найден")
    public ResponseEntity<RoomResponse> updateRoom(@PathVariable Long id,
                                                    @Valid @RequestBody RoomCreateRequest request) {
        return ResponseEntity.ok(hotelService.updateRoom(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удаление номера", description = "Удаляет номер из отеля. Только для администраторов.")
    @ApiResponse(responseCode = "204", description = "Номер удалён")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        hotelService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hotel/{hotelId}")
    @Operation(summary = "Получение номеров отеля", description = "Возвращает список всех номеров конкретного отеля")
    @ApiResponse(responseCode = "200", description = "Список номеров")
    public ResponseEntity<List<RoomResponse>> getRoomsByHotel(@PathVariable Long hotelId) {
        return ResponseEntity.ok(hotelService.getRoomsByHotel(hotelId));
    }

    @GetMapping("/hotel/{hotelId}/available")
    @Operation(summary = "Получение свободных номеров отеля", description = "Возвращает список свободных номеров конкретного отеля, отсортированных по times_booked")
    @ApiResponse(responseCode = "200", description = "Список свободных номеров")
    public ResponseEntity<List<RoomResponse>> getAvailableRoomsByHotel(@PathVariable Long hotelId) {
        return ResponseEntity.ok(hotelService.getAvailableRoomsByHotel(hotelId));
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск номеров с фильтрацией по датам", description = "Возвращает номера, доступные в указанный период")
    @ApiResponse(responseCode = "200", description = "Список доступных номеров")
    public ResponseEntity<List<RoomResponse>> searchAvailableRooms(
            @Parameter(description = "Дата начала") @RequestParam LocalDate startDate,
            @Parameter(description = "Дата окончания") @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(hotelService.getAvailableRoomsByDateRange(startDate, endDate));
    }
}
