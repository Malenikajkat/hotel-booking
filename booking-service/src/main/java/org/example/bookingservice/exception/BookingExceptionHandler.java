package org.example.bookingservice.exception;

import org.example.common.exception.ErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(basePackages = "org.example.bookingservice")
public class BookingExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorDto> handleUserNotFoundException(UserNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex, request);
    }

    @ExceptionHandler(RoomNotAvailableException.class)
    public ResponseEntity<ErrorDto> handleRoomNotAvailableException(RoomNotAvailableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex, request);
    }

    @ExceptionHandler(HotelServiceUnavailableException.class)
    public ResponseEntity<ErrorDto> handleHotelServiceUnavailableException(HotelServiceUnavailableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex, request);
    }

    private ResponseEntity<ErrorDto> buildResponse(HttpStatus status, String error, Exception ex, HttpServletRequest request) {
        ErrorDto errorDto = ErrorDto.builder()
                .error(error)
                .message(ex.getMessage() != null ? ex.getMessage() : "An error occurred")
                .status(status.value())
                .timestamp(java.time.LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(getTraceId())
                .build();
        return ResponseEntity.status(status).body(errorDto);
    }

    private String getTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId != null ? traceId : "unknown";
    }
}
