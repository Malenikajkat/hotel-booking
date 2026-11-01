package org.example.hotelmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for room occupancy statistics per hotel.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomOccupancyStats {

    private Long hotelId;
    private String hotelName;
    private Integer totalRooms;
    private Integer availableRooms;
    private Integer occupiedRooms;
    private Double occupancyRate;
    private Double averageTimesBooked;
    private List<RoomStats> roomDetails;

    // Period-based fields
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Double periodOccupancyRate;
    private Integer periodBookingsCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomStats {
        private Long roomId;
        private String roomNumber;
        private Integer timesBooked;
        private Boolean available;
        private Double occupancyPercentage;
    }
}
