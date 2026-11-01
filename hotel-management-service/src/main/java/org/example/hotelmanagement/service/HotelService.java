package org.example.hotelmanagement.service;

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
import org.example.hotelmanagement.entity.Booking;
import org.example.hotelmanagement.entity.Hotel;
import org.example.hotelmanagement.entity.Room;
import org.example.hotelmanagement.exception.HotelNotFoundException;
import org.example.hotelmanagement.exception.RoomNotFoundException;
import org.example.hotelmanagement.repository.BookingRepository;
import org.example.hotelmanagement.repository.HotelRepository;
import org.example.hotelmanagement.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HotelService {

    private static final Logger log = LoggerFactory.getLogger(HotelService.class);

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public HotelService(HotelRepository hotelRepository,
                        RoomRepository roomRepository,
                        BookingRepository bookingRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public HotelResponse createHotel(HotelCreateRequest request) {
        Hotel hotel = Hotel.builder()
                .name(request.getName())
                .address(request.getAddress())
                .build();
        return mapToResponse(hotelRepository.save(hotel));
    }

    public Page<HotelResponse> getAllHotels(Pageable pageable) {
        Page<Hotel> hotelPage = hotelRepository.findAll(pageable);
        return hotelPage.map(this::mapToResponse);
    }

    public HotelResponse getHotel(Long id) {
        return hotelRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + id));
    }

    @Transactional
    public HotelResponse updateHotel(Long id, HotelCreateRequest request) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + id));
        hotel.setName(request.getName());
        hotel.setAddress(request.getAddress());
        return mapToResponse(hotelRepository.save(hotel));
    }

    @Transactional
    public void deleteHotel(Long id) {
        if (!hotelRepository.existsById(id)) {
            throw new HotelNotFoundException("Hotel not found: " + id);
        }
        hotelRepository.deleteById(id);
    }

    @Transactional
    public RoomResponse createRoom(RoomCreateRequest request) {
        Room room = Room.builder()
                .hotelId(request.getHotelId())
                .roomNumber(request.getRoomNumber())
                .available(true)
                .timesBooked(0)
                .build();
        return mapRoomToResponse(roomRepository.save(room));
    }

    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(this::mapRoomToResponse)
                .toList();
    }

    public List<RoomResponse> getRoomsByHotel(Long hotelId) {
        return roomRepository.findByHotelId(hotelId).stream()
                .map(this::mapRoomToResponse)
                .toList();
    }

    public List<RoomResponse> getAvailableRoomsByHotel(Long hotelId) {
        return roomRepository.findByHotelId(hotelId).stream()
                .filter(Room::getAvailable)
                .sorted(Comparator.comparing(Room::getTimesBooked)
                        .thenComparing(Room::getId))
                .map(this::mapRoomToResponse)
                .toList();
    }

    public Page<RoomResponse> getAvailableRooms(Pageable pageable) {
        Page<Room> roomPage = roomRepository.findByAvailableTrue(pageable);
        return roomPage.map(this::mapRoomToResponse);
    }

    public RoomResponse getRoom(Long id) {
        return roomRepository.findById(id)
                .map(this::mapRoomToResponse)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + id));
    }

    public List<RoomResponse> getRecommendedRooms() {
        return roomRepository.findAllOrderByTimesBookedAsc().stream()
                .map(this::mapRoomToResponse)
                .toList();
    }

    public List<RoomResponse> getAvailableRoomsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Room> rooms = roomRepository.findAvailableRooms(startDate, endDate);
        return rooms.stream()
                .sorted(Comparator.comparing(Room::getTimesBooked)
                        .thenComparing(Room::getId))
                .map(this::mapRoomToResponse)
                .toList();
    }

    @Transactional
    public AvailabilityResponse confirmAvailability(Long roomId, AvailabilityRequest request) {
        String correlationId = request.getCorrelationId() != null ? request.getCorrelationId() : "N/A";
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        if (!room.getAvailable()) {
            return AvailabilityResponse.builder()
                    .available(false)
                    .message("Room is not available (maintenance)")
                    .build();
        }

        if (request.getCorrelationId() != null) {
            Booking existing = bookingRepository.findByCorrelationId(request.getCorrelationId()).orElse(null);
            if (existing != null) {
                return AvailabilityResponse.builder()
                        .available(true)
                        .message("Booking already confirmed (idempotent)")
                        .build();
            }
        }

        List<Booking> overlapping = bookingRepository.findOverlappingBookings(
                roomId, request.getStartDate(), request.getEndDate());

        if (!overlapping.isEmpty()) {
            return AvailabilityResponse.builder()
                    .available(false)
                    .message("Room is not available for the selected dates")
                    .build();
        }

        Booking booking = Booking.builder()
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .correlationId(request.getCorrelationId())
                .build();
        bookingRepository.save(booking);

        return AvailabilityResponse.builder()
                .available(true)
                .message("Room is available and temporarily blocked")
                .build();
    }

    @Transactional
    public void releaseRoom(Long roomId, String correlationId) {
        if (correlationId != null) {
            bookingRepository.deleteByCorrelationId(correlationId);
        } else {
            List<Booking> bookings = bookingRepository.findOverlappingBookings(
                    roomId, LocalDate.now(), LocalDate.now().plusDays(365));
            bookings.stream()
                    .filter(b -> b.getCorrelationId() != null)
                    .forEach(bookingRepository::delete);
        }
    }

    @Transactional
    public void incrementTimesBooked(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
        room.setTimesBooked(room.getTimesBooked() + 1);
        roomRepository.save(room);
    }

    @Transactional
    public RoomResponse updateRoom(Long roomId, RoomCreateRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        if (request.getRoomNumber() != null) {
            room.setRoomNumber(request.getRoomNumber());
        }

        return mapRoomToResponse(roomRepository.save(room));
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        }
        roomRepository.deleteById(roomId);
    }

    @Transactional(readOnly = true)
    public RoomOccupancyStats getRoomOccupancyStats(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        List<Room> rooms = roomRepository.findByHotelIdOrdered(hotelId);
        return computeHotelStats(hotelId, hotel.getName(), rooms);
    }

    @Transactional(readOnly = true)
    public List<RoomOccupancyStats> getAllHotelOccupancyStats() {
        return hotelRepository.findAll().stream()
                .map(hotel -> computeHotelStats(
                        hotel.getId(), hotel.getName(),
                        roomRepository.findByHotelIdOrdered(hotel.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomOccupancyStats getPeriodOccupancyStats(Long hotelId, LocalDate startDate, LocalDate endDate) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        List<Room> rooms = roomRepository.findByHotelIdOrdered(hotelId);
        if (rooms.isEmpty()) {
            return buildEmptyPeriodStats(hotelId, hotel.getName(), startDate, endDate);
        }

        List<Booking> periodBookings = bookingRepository.findBookingsInPeriod(hotelId, startDate, endDate);
        long periodBookingsCount = periodBookings.size();
        int totalRoomDays = rooms.size() * (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        double periodOccupancyRate = totalRoomDays > 0
                ? (double) periodBookingsCount / totalRoomDays * 100
                : 0.0;

        return RoomOccupancyStats.builder()
                .hotelId(hotelId)
                .hotelName(hotel.getName())
                .totalRooms(rooms.size())
                .availableRooms((int) rooms.stream().filter(Room::getAvailable).count())
                .occupiedRooms(0)
                .occupancyRate(0.0)
                .averageTimesBooked(0.0)
                .roomDetails(List.of())
                .periodStart(startDate)
                .periodEnd(endDate)
                .periodBookingsCount((int) periodBookingsCount)
                .periodOccupancyRate(Math.round(periodOccupancyRate * 100.0) / 100.0)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BookingTrendByDayOfWeek> getBookingTrendByDayOfWeek(Long hotelId) {
        hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        List<Object[]> results = bookingRepository.countBookingsByDayOfWeek(hotelId);
        long totalBookings = results.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

        return results.stream()
                .map(row -> {
                    int dayOfWeek = ((Number) row[0]).intValue();
                    long count = ((Number) row[1]).longValue();
                    double percentage = totalBookings > 0 ? (double) count / totalBookings * 100 : 0.0;
                    return BookingTrendByDayOfWeek.builder()
                            .dayOfWeek(dayOfWeek)
                            .dayName(dayNames[Math.min(dayOfWeek - 1, 6)])
                            .bookingCount(count)
                            .percentage(Math.round(percentage * 100.0) / 100.0)
                            .build();
                })
                .sorted(Comparator.comparingInt(BookingTrendByDayOfWeek::getDayOfWeek))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyOccupancyStats> getMonthlyOccupancyStats(Long hotelId, LocalDate startDate, LocalDate endDate) {
        hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        List<Room> rooms = roomRepository.findByHotelId(hotelId);
        long totalRoomDays = rooms.size() * (long) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);

        List<Object[]> results = bookingRepository.countBookingsByMonth(hotelId, startDate, endDate);

        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        Map<String, MonthlyOccupancyStats> monthMap = new LinkedHashMap<>();

        for (Object[] row : results) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            String key = year + "-" + month;

            double occupancyRate = totalRoomDays > 0
                    ? (double) count / totalRoomDays * 100
                    : 0.0;

            monthMap.put(key, MonthlyOccupancyStats.builder()
                    .year(year)
                    .monthNumber(month)
                    .monthName(monthNames[Math.min(month - 1, 11)])
                    .bookingCount(count)
                    .occupancyRate(Math.round(occupancyRate * 100.0) / 100.0)
                    .uniqueRoomsBooked(0L)
                    .roomDetails(List.of())
                    .build());
        }

        LocalDate current = startDate.withDayOfMonth(1);
        while (!current.isAfter(endDate.withDayOfMonth(1))) {
            String key = current.getYear() + "-" + current.getMonthValue();
            if (!monthMap.containsKey(key)) {
                monthMap.put(key, MonthlyOccupancyStats.builder()
                        .year(current.getYear())
                        .monthNumber(current.getMonthValue())
                        .monthName(monthNames[Math.min(current.getMonthValue() - 1, 11)])
                        .bookingCount(0L)
                        .occupancyRate(0.0)
                        .uniqueRoomsBooked(0L)
                        .roomDetails(List.of())
                        .build());
            }
            current = current.plusMonths(1);
        }

        return new ArrayList<>(monthMap.values());
    }

    @Transactional(readOnly = true)
    public TopBookedRoomStats getTopBookedRooms(Long hotelId, int limit) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        List<Room> rooms = roomRepository.findByHotelId(hotelId);
        if (rooms.isEmpty()) {
            return TopBookedRoomStats.builder()
                    .hotelId(hotelId)
                    .hotelName(hotel.getName())
                    .topRooms(List.of())
                    .totalBookings(0L)
                    .averageBookingsPerRoom(0.0)
                    .build();
        }

        long totalBookings = rooms.stream().mapToInt(Room::getTimesBooked).sum();
        double avgBookings = (double) totalBookings / rooms.size();
        int maxBooked = rooms.stream().mapToInt(Room::getTimesBooked).max().orElse(1);

        List<TopBookedRoomStats.RoomRanking> ranked = rooms.stream()
                .sorted(Comparator.comparingInt(Room::getTimesBooked).reversed()
                        .thenComparing(Room::getId))
                .limit(limit)
                .map(room -> {
                    double percentage = maxBooked > 0
                            ? (double) room.getTimesBooked() / maxBooked * 100
                            : 0.0;
                    return TopBookedRoomStats.RoomRanking.builder()
                            .roomId(room.getId())
                            .roomNumber(room.getRoomNumber())
                            .timesBooked(room.getTimesBooked())
                            .percentageOfTotal(Math.round(percentage * 100.0) / 100.0)
                            .build();
                })
                .toList();

        for (int i = 0; i < ranked.size(); i++) {
            TopBookedRoomStats.RoomRanking r = ranked.get(i);
            ranked.set(i, TopBookedRoomStats.RoomRanking.builder()
                    .rank(i + 1)
                    .roomId(r.getRoomId())
                    .roomNumber(r.getRoomNumber())
                    .timesBooked(r.getTimesBooked())
                    .percentageOfTotal(r.getPercentageOfTotal())
                    .build());
        }

        return TopBookedRoomStats.builder()
                .hotelId(hotelId)
                .hotelName(hotel.getName())
                .topRooms(ranked)
                .totalBookings(totalBookings)
                .averageBookingsPerRoom(Math.round(avgBookings * 100.0) / 100.0)
                .build();
    }

    private RoomOccupancyStats computeHotelStats(Long hotelId, String hotelName, List<Room> rooms) {
        int totalRooms = rooms.size();
        if (totalRooms == 0) {
            return RoomOccupancyStats.builder()
                    .hotelId(hotelId).hotelName(hotelName)
                    .totalRooms(0).availableRooms(0).occupiedRooms(0)
                    .occupancyRate(0.0).averageTimesBooked(0.0)
                    .roomDetails(List.of()).build();
        }

        int availableRooms = (int) rooms.stream().filter(Room::getAvailable).count();
        int occupiedRooms = totalRooms - availableRooms;
        double occupancyRate = totalRooms > 0 ? (double) occupiedRooms / totalRooms * 100 : 0.0;
        double averageTimesBooked = rooms.stream()
                .mapToInt(Room::getTimesBooked)
                .average().orElse(0.0);

        final int maxTimesBooked = Math.max(
                rooms.stream().mapToInt(Room::getTimesBooked).max().orElse(1), 1);

        List<RoomOccupancyStats.RoomStats> roomDetails = rooms.stream()
                .map(room -> {
                    double occupancyPercentage = (double) room.getTimesBooked() / maxTimesBooked * 100;
                    return RoomOccupancyStats.RoomStats.builder()
                            .roomId(room.getId())
                            .roomNumber(room.getRoomNumber())
                            .timesBooked(room.getTimesBooked())
                            .available(room.getAvailable())
                            .occupancyPercentage(Math.round(occupancyPercentage * 100.0) / 100.0)
                            .build();
                })
                .toList();

        return RoomOccupancyStats.builder()
                .hotelId(hotelId)
                .hotelName(hotelName)
                .totalRooms(totalRooms)
                .availableRooms(availableRooms)
                .occupiedRooms(occupiedRooms)
                .occupancyRate(Math.round(occupancyRate * 100.0) / 100.0)
                .averageTimesBooked(Math.round(averageTimesBooked * 100.0) / 100.0)
                .roomDetails(roomDetails)
                .build();
    }

    // Helper: build empty period stats
    private RoomOccupancyStats buildEmptyPeriodStats(Long hotelId, String hotelName,
                                                      LocalDate startDate, LocalDate endDate) {
        return RoomOccupancyStats.builder()
                .hotelId(hotelId)
                .hotelName(hotelName)
                .totalRooms(0)
                .availableRooms(0)
                .occupiedRooms(0)
                .occupancyRate(0.0)
                .averageTimesBooked(0.0)
                .roomDetails(List.of())
                .periodStart(startDate)
                .periodEnd(endDate)
                .periodBookingsCount(0)
                .periodOccupancyRate(0.0)
                .build();
    }

    private HotelResponse mapToResponse(Hotel hotel) {
        return HotelResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .address(hotel.getAddress())
                .build();
    }

    private RoomResponse mapRoomToResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .hotelId(room.getHotelId())
                .roomNumber(room.getRoomNumber())
                .available(room.getAvailable())
                .timesBooked(room.getTimesBooked())
                .build();
    }
}
