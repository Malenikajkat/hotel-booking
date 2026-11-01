package org.example.hotelmanagement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.hotelmanagement.dto.AvailabilityRequest;
import org.example.hotelmanagement.dto.AvailabilityResponse;
import org.example.hotelmanagement.dto.BookingTrendByDayOfWeek;
import org.example.hotelmanagement.dto.HotelCreateRequest;
import org.example.hotelmanagement.dto.HotelResponse;
import org.example.hotelmanagement.dto.MonthlyOccupancyStats;
import org.example.hotelmanagement.dto.RoomCreateRequest;
import org.example.hotelmanagement.dto.RoomOccupancyStats;
import org.example.hotelmanagement.dto.RoomResponse;
import org.example.hotelmanagement.dto.TopBookedRoomStats;
import org.example.hotelmanagement.service.HotelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Hotel API", description = "API для управления отелями")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @PostMapping("/hotels")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Создание отеля", description = "Добавляет новый отель. Только для администраторов.")
    @ApiResponse(responseCode = "201", description = "Отель создан")
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody HotelCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelService.createHotel(request));
    }

    @GetMapping("/hotels")
    @Operation(summary = "Получение списка отелей", description = "Возвращает список всех отелей с пагинацией")
    @ApiResponse(responseCode = "200", description = "Список отелей")
    public ResponseEntity<Page<HotelResponse>> getAllHotels(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(hotelService.getAllHotels(pageable));
    }

    @GetMapping("/hotels/{id}")
    @Operation(summary = "Получение отеля", description = "Возвращает отель по ID")
    @ApiResponse(responseCode = "200", description = "Отель найден")
    @ApiResponse(responseCode = "404", description = "Отель не найден")
    public ResponseEntity<HotelResponse> getHotel(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getHotel(id));
    }

    @PatchMapping("/hotels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Обновление отеля", description = "Обновляет данные отеля. Только для администраторов.")
    @ApiResponse(responseCode = "200", description = "Отель обновлён")
    public ResponseEntity<HotelResponse> updateHotel(@PathVariable Long id,
                                                      @Valid @RequestBody HotelCreateRequest request) {
        return ResponseEntity.ok(hotelService.updateHotel(id, request));
    }

    @DeleteMapping("/hotels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удаление отеля", description = "Удаляет отель. Только для администраторов.")
    @ApiResponse(responseCode = "204", description = "Отель удалён")
    public ResponseEntity<Void> deleteHotel(@PathVariable Long id) {
        hotelService.deleteHotel(id);
        return ResponseEntity.noContent().build();
    }

    // Analytics endpoints
    @GetMapping("/hotels/{id}/occupancy-stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Статистика загруженности отеля", description = "Возвращает детальную статистику по номерам отеля: " +
            "общий процент загрузки, среднее количество бронирований, статистика по каждому номеру.")
    @ApiResponse(responseCode = "200", description = "Статистика загруженности")
    @ApiResponse(responseCode = "404", description = "Отель не найден")
    public ResponseEntity<RoomOccupancyStats> getHotelOccupancyStats(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getRoomOccupancyStats(id));
    }

    @GetMapping("/hotels/occupancy-stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Статистика загруженности всех отелей", description = "Возвращает статистику по всем отелям.")
    @ApiResponse(responseCode = "200", description = "Список статистики")
    public ResponseEntity<List<RoomOccupancyStats>> getAllOccupancyStats() {
        return ResponseEntity.ok(hotelService.getAllHotelOccupancyStats());
    }

    @GetMapping("/hotels/{id}/occupancy-stats/period")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Статистика загруженности за период", description = "Возвращает статистику по бронированиям за указанный период")
    @ApiResponse(responseCode = "200", description = "Периодная статистика")
    @ApiResponse(responseCode = "404", description = "Отель не найден")
    public ResponseEntity<RoomOccupancyStats> getPeriodOccupancyStats(
            @PathVariable Long id,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(hotelService.getPeriodOccupancyStats(id, startDate, endDate));
    }

    // Extended analytics: Booking trend by day of week
    @GetMapping("/hotels/{id}/analytics/trend/day-of-week")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Тренд бронирований по дням недели", description = "Возвращает статистику бронирований, " +
            "сгруппированную по дням недели, с процентным соотношением. " +
            "Помогает выявить пиковые дни для оптимизации загрузки.")
    @ApiResponse(responseCode = "200", description = "Тренды по дням недели")
    @ApiResponse(responseCode = "404", description = "Отель не найден")
    public ResponseEntity<List<BookingTrendByDayOfWeek>> getBookingTrendByDayOfWeek(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getBookingTrendByDayOfWeek(id));
    }

    // Extended analytics: Monthly occupancy statistics
    @GetMapping("/hotels/{id}/analytics/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ежемесячная статистика загруженности", description = "Возвращает статистику бронирований " +
            "по месяцам за указанный период с процентом загруженности. " +
            "Помогает выявить сезонные тренды.")
    @ApiResponse(responseCode = "200", description = "Ежемесячная статистика")
    @ApiResponse(responseCode = "404", description = "Отель не найден")
    public ResponseEntity<List<MonthlyOccupancyStats>> getMonthlyOccupancyStats(
            @PathVariable Long id,
            @Parameter(description = "Дата начала периода") @RequestParam LocalDate startDate,
            @Parameter(description = "Дата окончания периода") @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(hotelService.getMonthlyOccupancyStats(id, startDate, endDate));
    }

    // Extended analytics: Top booked rooms
    @GetMapping("/hotels/{id}/analytics/top-rooms")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Топ самых бронированных номеров", description = "Возвращает рейтинг номеров " +
            "по количеству бронирований с процентным соотношением. " +
            "Помогает выявить самые популярные номера.")
    @ApiResponse(responseCode = "200", description = "Топ номеров")
    @ApiResponse(responseCode = "404", description = "Отель не найден")
    public ResponseEntity<TopBookedRoomStats> getTopBookedRooms(
            @PathVariable Long id,
            @Parameter(description = "Количество номеров в топе") @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(hotelService.getTopBookedRooms(id, limit));
    }
}
