package org.example.hotelmanagement.repository;

import org.example.hotelmanagement.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByAvailableTrue();

    Page<Room> findByAvailableTrue(Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.available = true AND r.id NOT IN " +
            "(SELECT bk.roomId FROM Booking bk WHERE bk.roomId = r.id AND bk.endDate > :startDate AND bk.startDate < :endDate)")
    List<Room> findAvailableRooms(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT r FROM Room r WHERE r.available = true ORDER BY r.timesBooked ASC, r.id ASC")
    List<Room> findAllOrderByTimesBookedAsc();

    List<Room> findByHotelId(Long hotelId);

    @Query("SELECT r FROM Room r WHERE r.hotelId = :hotelId ORDER BY r.timesBooked ASC, r.id ASC")
    List<Room> findByHotelIdOrdered(@Param("hotelId") Long hotelId);
}
