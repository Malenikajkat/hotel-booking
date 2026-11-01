package org.example.bookingservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ValidDateRange
public class BookingCreateRequest {

    @NotNull(message = "Room ID is required")
    private Long roomId;

    @NotNull(message = "Start date is required")
    @Future(message = "Start date must be in the future")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Future(message = "End date must be in the future")
    private LocalDate endDate;

    @Builder.Default
    private Boolean autoSelect = false;

    /**
     * Client-side idempotency key. If a request with the same requestId
     * is received again, the previously processed result is returned
     * instead of creating a duplicate booking.
     */
    @Size(max = 64, message = "Request ID must not exceed 64 characters")
    private String requestId;
}
