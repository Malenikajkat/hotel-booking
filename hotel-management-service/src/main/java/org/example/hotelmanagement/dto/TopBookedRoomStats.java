package org.example.hotelmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for top booked rooms analytics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopBookedRoomStats {

    private Long hotelId;
    private String hotelName;
    private List<RoomRanking> topRooms;
    private Long totalBookings;
    private Double averageBookingsPerRoom;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomRanking {
        private Integer rank;
        private Long roomId;
        private String roomNumber;
        private Integer timesBooked;
        private Double percentageOfTotal;
    }
}
