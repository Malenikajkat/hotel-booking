package org.example.hotelmanagement.service;

import org.example.hotelmanagement.dto.AvailabilityRequest;
import org.example.hotelmanagement.dto.AvailabilityResponse;
import org.example.hotelmanagement.dto.HotelCreateRequest;
import org.example.hotelmanagement.dto.HotelResponse;
import org.example.hotelmanagement.dto.RoomCreateRequest;
import org.example.hotelmanagement.dto.RoomResponse;
import org.example.hotelmanagement.entity.Booking;
import org.example.hotelmanagement.entity.Hotel;
import org.example.hotelmanagement.entity.Room;
import org.example.hotelmanagement.exception.HotelNotFoundException;
import org.example.hotelmanagement.exception.RoomNotFoundException;
import org.example.hotelmanagement.repository.BookingRepository;
import org.example.hotelmanagement.repository.HotelRepository;
import org.example.hotelmanagement.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private HotelService hotelService;

    @Test
    void createHotel_ReturnsHotelResponse() {
        HotelCreateRequest request = new HotelCreateRequest("Test Hotel", "Test Address");
        Hotel hotel = Hotel.builder().id(1L).name("Test Hotel").address("Test Address").build();

        when(hotelRepository.save(any(Hotel.class))).thenReturn(hotel);

        HotelResponse response = hotelService.createHotel(request);

        assertNotNull(response);
        assertEquals("Test Hotel", response.getName());
        assertEquals("Test Address", response.getAddress());
        verify(hotelRepository).save(any(Hotel.class));
    }

    @Test
    void updateHotel_Existing_ReturnsUpdatedHotel() {
        Hotel hotel = Hotel.builder().id(1L).name("Old Name").address("Old Address").build();
        HotelCreateRequest request = new HotelCreateRequest("New Name", "New Address");

        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(hotelRepository.save(any(Hotel.class))).thenReturn(
                Hotel.builder().id(1L).name("New Name").address("New Address").build());

        HotelResponse response = hotelService.updateHotel(1L, request);

        assertEquals("New Name", response.getName());
        assertEquals("New Address", response.getAddress());
    }

    @Test
    void updateHotel_NotFound_ThrowsException() {
        when(hotelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> hotelService.updateHotel(999L, new HotelCreateRequest("N", "A")));
    }

    @Test
    void deleteHotel_Existing_DeletesHotel() {
        when(hotelRepository.existsById(1L)).thenReturn(true);

        hotelService.deleteHotel(1L);

        verify(hotelRepository).deleteById(1L);
    }

    @Test
    void deleteHotel_NotFound_ThrowsException() {
        when(hotelRepository.existsById(999L)).thenReturn(false);

        assertThrows(HotelNotFoundException.class, () -> hotelService.deleteHotel(999L));
    }

    @Test
    void createRoom_ReturnsRoomResponse() {
        RoomCreateRequest request = new RoomCreateRequest(1L, "101");
        Room room = Room.builder().id(1L).hotelId(1L).roomNumber("101").available(true).timesBooked(0).build();

        when(roomRepository.save(any(Room.class))).thenReturn(room);

        RoomResponse response = hotelService.createRoom(request);

        assertNotNull(response);
        assertEquals("101", response.getRoomNumber());
        assertEquals(1L, response.getHotelId());
        assertTrue(response.getAvailable());
    }

    @Test
    void getRecommendedRooms_ReturnsSortedByTimesBooked() {
        Room room3 = Room.builder().id(3L).hotelId(1L).roomNumber("103").available(true).timesBooked(0).build();
        Room room2 = Room.builder().id(2L).hotelId(1L).roomNumber("102").available(true).timesBooked(3).build();
        Room room1 = Room.builder().id(1L).hotelId(1L).roomNumber("101").available(true).timesBooked(10).build();

        when(roomRepository.findAllOrderByTimesBookedAsc()).thenReturn(List.of(room3, room2, room1));

        List<RoomResponse> rooms = hotelService.getRecommendedRooms();

        assertEquals(3, rooms.size());
        assertEquals(0, rooms.get(0).getTimesBooked());
        assertEquals(3, rooms.get(1).getTimesBooked());
        assertEquals(10, rooms.get(2).getTimesBooked());
    }

    @Test
    void confirmAvailability_RoomAvailable_ReturnsAvailable() {
        Room room = Room.builder().id(1L).hotelId(1L).roomNumber("101").available(true).timesBooked(0).build();
        AvailabilityRequest request = new AvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2026, 12, 5));
        request.setCorrelationId("test-123");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlappingBookings(anyLong(), any(), any())).thenReturn(List.of());
        when(bookingRepository.findByCorrelationId("test-123")).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenReturn(Booking.builder().build());

        AvailabilityResponse response = hotelService.confirmAvailability(1L, request);

        assertTrue(response.getAvailable());
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void confirmAvailability_RoomNotAvailable_ReturnsUnavailable() {
        Room room = Room.builder().id(1L).hotelId(1L).roomNumber("101").available(false).timesBooked(0).build();
        AvailabilityRequest request = new AvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2026, 12, 5));

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        AvailabilityResponse response = hotelService.confirmAvailability(1L, request);

        assertFalse(response.getAvailable());
        assertEquals("Room is not available (maintenance)", response.getMessage());
    }

    @Test
    void confirmAvailability_OverlappingBooking_ReturnsUnavailable() {
        Room room = Room.builder().id(1L).hotelId(1L).roomNumber("101").available(true).timesBooked(0).build();
        AvailabilityRequest request = new AvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2026, 12, 5));

        Booking overlapping = Booking.builder()
                .roomId(1L)
                .startDate(LocalDate.of(2026, 12, 3))
                .endDate(LocalDate.of(2026, 12, 7))
                .build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlappingBookings(anyLong(), any(), any())).thenReturn(List.of(overlapping));

        AvailabilityResponse response = hotelService.confirmAvailability(1L, request);

        assertFalse(response.getAvailable());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void confirmAvailability_Idempotent_Hit_ReturnsAvailable() {
        Room room = Room.builder().id(1L).hotelId(1L).roomNumber("101").available(true).timesBooked(0).build();
        AvailabilityRequest request = new AvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2026, 12, 5));
        request.setCorrelationId("test-123");

        Booking existing = Booking.builder()
                .correlationId("test-123")
                .build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByCorrelationId("test-123")).thenReturn(Optional.of(existing));

        AvailabilityResponse response = hotelService.confirmAvailability(1L, request);

        assertTrue(response.getAvailable());
        assertEquals("Booking already confirmed (idempotent)", response.getMessage());
    }

    @Test
    void incrementTimesBooked_IncrementsCounter() {
        Room room = Room.builder().id(1L).hotelId(1L).roomNumber("101").available(true).timesBooked(5).build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(
                Room.builder().id(1L).hotelId(1L).roomNumber("101").available(true).timesBooked(6).build());

        hotelService.incrementTimesBooked(1L);

        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void incrementTimesBooked_RoomNotFound_ThrowsException() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class, () -> hotelService.incrementTimesBooked(999L));
    }

    @Test
    void getHotel_NotFound_ThrowsException() {
        when(hotelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(HotelNotFoundException.class, () -> hotelService.getHotel(999L));
    }

    @Test
    void getRoom_NotFound_ThrowsException() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class, () -> hotelService.getRoom(999L));
    }
}
