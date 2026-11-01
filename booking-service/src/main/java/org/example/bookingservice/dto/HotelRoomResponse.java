package org.example.bookingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelRoomResponse {

    private Long id;
    private Long hotelId;
    private String roomNumber;
    private Boolean available;
    private Integer timesBooked;
}
