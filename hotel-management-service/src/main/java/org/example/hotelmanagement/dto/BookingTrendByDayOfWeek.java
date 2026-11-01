package org.example.hotelmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for booking trend statistics grouped by day of week.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingTrendByDayOfWeek {

    private Integer dayOfWeek;
    private String dayName;
    private Long bookingCount;
    private Double percentage;
}
