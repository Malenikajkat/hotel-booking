package org.example.hotelmanagement.repository;

import org.example.hotelmanagement.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.startDate < :endDate AND b.endDate > :startDate")
    List<Booking> findOverlappingBookings(@Param("roomId") Long roomId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    Optional<Booking> findByCorrelationId(String correlationId);

    @Modifying
    @Query("DELETE FROM Booking b WHERE b.correlationId = :correlationId")
    int deleteByCorrelationId(@Param("correlationId") String correlationId);

    @Query("SELECT b FROM Booking b WHERE b.roomId IN " +
            "(SELECT r.id FROM Room r WHERE r.hotelId = :hotelId) " +
            "AND b.startDate < :endDate AND b.endDate > :startDate")
    List<Booking> findBookingsInPeriod(@Param("hotelId") Long hotelId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    @Query("SELECT FUNCTION('DAYOFWEEK', b.startDate) as dow, COUNT(b) as cnt " +
            "FROM Booking b WHERE b.roomId IN " +
            "(SELECT r.id FROM Room r WHERE r.hotelId = :hotelId) " +
            "GROUP BY FUNCTION('DAYOFWEEK', b.startDate) ORDER BY dow")
    List<Object[]> countBookingsByDayOfWeek(@Param("hotelId") Long hotelId);

    @Query("SELECT FUNCTION('YEAR', b.startDate) as yr, FUNCTION('MONTH', b.startDate) as mn, COUNT(b) as cnt " +
            "FROM Booking b WHERE b.roomId IN " +
            "(SELECT r.id FROM Room r WHERE r.hotelId = :hotelId) " +
            "AND b.startDate >= :startDate AND b.startDate <= :endDate " +
            "GROUP BY FUNCTION('YEAR', b.startDate), FUNCTION('MONTH', b.startDate) " +
            "ORDER BY yr, mn")
    List<Object[]> countBookingsByMonth(@Param("hotelId") Long hotelId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);
}
