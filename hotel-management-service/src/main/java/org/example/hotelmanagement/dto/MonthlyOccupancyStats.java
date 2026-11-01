package org.example.hotelmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for monthly occupancy statistics across a date range.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyOccupancyStats {

    private LocalDate month;
    private Integer year;
    private Integer monthNumber;
    private String monthName;
    private Long bookingCount;
    private Double occupancyRate;
    private Long uniqueRoomsBooked;
    private List<RoomMonthlyStats> roomDetails;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomMonthlyStats {
        private Long roomId;
        private String roomNumber;
        private Long bookingCount;
        private Double occupancyRate;
    }
}
